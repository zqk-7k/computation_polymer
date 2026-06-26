package com.vaspshow.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaspshow.backend.config.AssistantProperties;
import com.vaspshow.backend.dto.AssistantChatMessageRequest;
import com.vaspshow.backend.dto.AssistantChatRequest;
import com.vaspshow.backend.dto.AssistantChatResponse;
import com.vaspshow.backend.dto.AuthUserResponse;
import com.vaspshow.backend.dto.DatasetCatalogResponse;
import com.vaspshow.backend.dto.DatasetDetailResponse;
import com.vaspshow.backend.dto.DatasetLinkResponse;
import com.vaspshow.backend.dto.DatasetRecordDetailResponse;
import com.vaspshow.backend.exception.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {

  private static final int MAX_MESSAGES = 12;
  private static final int MAX_MESSAGE_LENGTH = 4000;
  private static final int MAX_TOTAL_LENGTH = 16000;

  private final AssistantProperties properties;
  private final DisplayDatasetService displayDatasetService;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public AssistantService(
      AssistantProperties properties,
      DisplayDatasetService displayDatasetService,
      ObjectMapper objectMapper
  ) {
    this.properties = properties;
    this.displayDatasetService = displayDatasetService;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
        .build();
  }

  public AssistantChatResponse chat(AssistantChatRequest request, AuthUserResponse user) {
    if (!properties.isEnabled()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "智能助手当前未启用");
    }
    List<AssistantChatMessageRequest> messages = validatedMessages(request);
    ContextBundle context = buildContext(request);

    ObjectNode body = objectMapper.createObjectNode();
    body.put("model", properties.getModel());
    body.put("stream", false);
    body.put("think", false);
    body.put("keep_alive", properties.getKeepAlive());
    body.putObject("options").put("temperature", 0.2);
    ArrayNode ollamaMessages = body.putArray("messages");
    appendMessage(ollamaMessages, "system", systemPrompt(user, context.text()));
    messages.forEach(message -> appendMessage(ollamaMessages, message.role(), message.content().trim()));

    try {
      HttpRequest ollamaRequest = HttpRequest.newBuilder()
          .uri(URI.create(normalizedBaseUrl() + "/api/chat"))
          .timeout(Duration.ofSeconds(Math.max(10, properties.getRequestTimeoutSeconds())))
          .header("Content-Type", "application/json; charset=UTF-8")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();
      HttpResponse<String> response = httpClient.send(ollamaRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "智能助手模型服务响应异常");
      }
      JsonNode responseBody = objectMapper.readTree(response.body());
      String answer = responseBody.path("message").path("content").asText("").trim();
      if (answer.isBlank()) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "智能助手未返回有效回答");
      }
      return new AssistantChatResponse(properties.getModel(), answer, context.label(), context.sources());
    } catch (HttpTimeoutException ex) {
      throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "智能助手响应超时，请稍后重试");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "智能助手请求已中断");
    } catch (IOException | IllegalArgumentException ex) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "无法连接智能助手模型，请检查 Ollama 服务与 SSH 隧道");
    }
  }

  private List<AssistantChatMessageRequest> validatedMessages(AssistantChatRequest request) {
    List<AssistantChatMessageRequest> provided = request == null ? null : request.messages();
    if (provided == null || provided.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "请输入需要咨询的问题");
    }
    List<AssistantChatMessageRequest> messages = new ArrayList<>();
    int totalLength = 0;
    for (AssistantChatMessageRequest message : provided) {
      String role = message == null || message.role() == null
          ? ""
          : message.role().trim().toLowerCase(Locale.ROOT);
      String content = message == null || message.content() == null ? "" : message.content().trim();
      if (!"user".equals(role) && !"assistant".equals(role)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "对话角色仅支持 user 或 assistant");
      }
      if (content.isBlank() || content.length() > MAX_MESSAGE_LENGTH) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "单条消息长度需为 1-4000 字");
      }
      totalLength += content.length();
      if (totalLength > MAX_TOTAL_LENGTH) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "当前对话内容过长，请开始新的咨询");
      }
      messages.add(new AssistantChatMessageRequest(role, content));
    }
    if (!"user".equals(messages.get(messages.size() - 1).role())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "最后一条消息必须为用户问题");
    }
    int from = Math.max(0, messages.size() - MAX_MESSAGES);
    return messages.subList(from, messages.size());
  }

  private ContextBundle buildContext(AssistantChatRequest request) {
    String datasetId = request == null || request.datasetId() == null ? "" : request.datasetId().trim();
    Long recordId = request == null ? null : request.recordId();
    if (datasetId.isBlank()) {
      if (recordId != null) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "记录上下文必须指定数据集");
      }
      StringBuilder overview = new StringBuilder("当前数据集目录：\n");
      List<String> sources = new ArrayList<>();
      for (DatasetCatalogResponse item : displayDatasetService.listDatasetCatalog()) {
        overview.append("- ").append(item.name()).append(" (").append(item.id()).append(")")
            .append("；类型=").append(item.dataType())
            .append("；展示记录=").append(item.displayRecords())
            .append("；元素=").append(join(item.elements()))
            .append("；方法=").append(join(item.calculationMethods()))
            .append("；泛函=").append(join(item.functionals()))
            .append("；性质=").append(join(item.properties()))
            .append('\n');
        sources.add(item.name());
      }
      return new ContextBundle("全部数据集目录", overview.toString(), sources);
    }

    DatasetDetailResponse detail = displayDatasetService.getDatasetDetail(datasetId);
    DatasetCatalogResponse catalog = displayDatasetService.listDatasetCatalog().stream()
        .filter(item -> item.id().equals(datasetId))
        .findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "未知数据集: " + datasetId));
    StringBuilder context = new StringBuilder();
    context.append("当前数据集：").append(catalog.name()).append(" (").append(catalog.id()).append(")\n");
    appendContext(context, "类型", catalog.dataType());
    appendContext(context, "介绍", catalog.intro());
    appendContext(context, "展示方式", catalog.representation());
    appendContext(context, "展示记录数", String.valueOf(catalog.displayRecords()));
    appendContext(context, "源结构/构象数", String.valueOf(catalog.sourceStructures()));
    appendContext(context, "原子数范围", catalog.minAtoms() + "-" + catalog.maxAtoms());
    appendContext(context, "元素", join(catalog.elements()));
    appendContext(context, "计算方法", join(catalog.calculationMethods()));
    appendContext(context, "泛函", join(catalog.functionals()));
    appendContext(context, "基组/赝势", join(catalog.basisSets()));
    appendContext(context, "软件", join(catalog.software()));
    appendContext(context, "已提供性质", join(catalog.properties()));
    appendContext(context, "数据集方法摘要", detail.method());
    for (DatasetLinkResponse link : catalog.links()) {
      appendContext(context, link.label(), link.url());
    }
    List<String> sources = new ArrayList<>(List.of(catalog.name()));
    String label = catalog.name();
    if (recordId != null) {
      DatasetRecordDetailResponse record = displayDatasetService.getRecord(datasetId, recordId);
      context.append("当前展示记录：#").append(record.id()).append('\n');
      appendContext(context, "Source ID", record.sourceRecordId());
      appendContext(context, "Material ID", record.materialId());
      appendContext(context, "名称", record.materialName());
      appendContext(context, "组成", record.composition());
      appendContext(context, "SMILES", record.smiles());
      appendContext(context, "原子数", record.atomCount());
      appendContext(context, "能量", record.energy());
      appendContext(context, "HOMO", record.homo());
      appendContext(context, "LUMO", record.lumo());
      appendContext(context, "HOMO-LUMO gap", record.homoLumoGap());
      appendContext(context, "电荷", record.charge());
      appendContext(context, "自旋", record.spin());
      appendContext(context, "计算软件", record.calculationSoftware());
      appendContext(context, "单位说明", record.unitNote());
      appendContext(context, "三维坐标状态", record.atoms().isEmpty() ? "未提供" : "已提供，共 " + record.atoms().size() + " 个原子");
      if (record.extraProperties() != null) {
        for (Map.Entry<String, String> property : record.extraProperties().entrySet()) {
          appendContext(context, property.getKey(), property.getValue());
        }
      }
      label = catalog.name() + " / 记录 #" + record.id();
      sources.add("当前记录 #" + record.id());
    }
    return new ContextBundle(label, context.toString(), sources);
  }

  private String systemPrompt(AuthUserResponse user, String context) {
    return """
        你是 VASP Show 计算数据平台的科研助手。请使用中文、准确、简洁地回答。
        你必须优先依据下方平台提供的结构化上下文回答数据集和记录相关问题。
        若上下文未提供某字段、单位、性质或事实，明确说明“当前平台上下文未提供”，不得推测为已收录。
        对方法、泛函、基组、能量或结构的解释可以提供通用科研背景，但需与平台事实区分。
        不提供绕过权限或批量抓取数据的方法。当前用户角色：%s。

        [平台受控上下文]
        %s
        """.formatted(user.role(), context);
  }

  private void appendMessage(ArrayNode messages, String role, String content) {
    ObjectNode message = messages.addObject();
    message.put("role", role);
    message.put("content", content);
  }

  private void appendContext(StringBuilder context, String label, String value) {
    if (value != null && !value.isBlank()) {
      context.append("- ").append(label).append(": ").append(value).append('\n');
    }
  }

  private String join(List<String> values) {
    return values == null || values.isEmpty() ? "未提供" : String.join(", ", values);
  }

  private String normalizedBaseUrl() {
    String baseUrl = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().trim();
    if (baseUrl.endsWith("/")) {
      return baseUrl.substring(0, baseUrl.length() - 1);
    }
    return baseUrl;
  }

  private record ContextBundle(String label, String text, List<String> sources) {
  }
}
