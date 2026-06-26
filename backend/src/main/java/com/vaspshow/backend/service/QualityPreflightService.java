package com.vaspshow.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaspshow.backend.dto.FileFieldProfileResponse;
import com.vaspshow.backend.dto.FilePreviewResponse;
import com.vaspshow.backend.dto.LinkCheckResponse;
import com.vaspshow.backend.dto.LinkValidationRequest;
import com.vaspshow.backend.dto.LinkValidationResponse;
import com.vaspshow.backend.exception.ApiException;
import io.jhdf.HdfFile;
import io.jhdf.api.Dataset;
import io.jhdf.api.Group;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class QualityPreflightService {

  private static final long MAX_PREVIEW_BYTES = 30L * 1024L * 1024L;
  private static final int MAX_RECORDS = 40;
  private static final int MAX_FIELDS = 80;

  private final ObjectMapper objectMapper;
  private final String pythonCommand;
  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(6))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();

  public QualityPreflightService(
      ObjectMapper objectMapper,
      @Value("${vasp.quality.python-command:python}") String pythonCommand
  ) {
    this.objectMapper = objectMapper;
    this.pythonCommand = pythonCommand == null || pythonCommand.isBlank() ? "python" : pythonCommand.trim();
  }

  public LinkValidationResponse validateLinks(LinkValidationRequest request) {
    List<LinkCheckResponse> checks = new ArrayList<>();
    addCheck(checks, "doi", "论文 DOI", normalizeDoi(request == null ? null : request.doi()));
    addCheck(checks, "paperUrl", "论文链接", cleanUrl(request == null ? null : request.paperUrl()));
    addCheck(checks, "dataUrl", "数据下载链接", cleanUrl(request == null ? null : request.dataUrl()));
    long reachable = checks.stream().filter(LinkCheckResponse::reachable).count();
    int score = checks.isEmpty() ? 0 : (int) Math.round(reachable * 100.0 / checks.size());
    List<String> recommendations = new ArrayList<>();
    if (checks.isEmpty()) {
      recommendations.add("请至少填写 DOI、论文链接或数据下载链接之一。");
    }
    checks.stream()
        .filter(check -> !check.reachable())
        .forEach(check -> recommendations.add(check.label() + "暂未验证通过，请确认链接可公开访问。"));
    if (score == 100) {
      recommendations.add("链接可访问性验证通过，可进入字段预检或提交审核。");
    }
    return new LinkValidationResponse(score, List.copyOf(checks), List.copyOf(recommendations));
  }

  public FilePreviewResponse previewFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "请上传一个数据集文件");
    }
    if (file.getSize() > MAX_PREVIEW_BYTES) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "预检文件不能超过 30MB；大文件请先上传抽样文件或前若干条记录");
    }
    String filename = file.getOriginalFilename() == null ? "dataset" : file.getOriginalFilename();
    String format = detectFormat(filename);
    try {
      PreviewAccumulator accumulator = switch (format) {
        case "csv", "tsv" -> parseDelimited(file, "csv".equals(format) ? "," : "\t", format);
        case "json", "jsonl" -> parseJson(file, format);
        case "xyz" -> parseXyz(file);
        case "cif" -> parseCif(file);
        case "hdf5" -> parseHdf5(file);
        default -> parseTextFallback(file, format);
      };
      accumulator.scientificNotes.addAll(runScientificPreview(file, filename, format));
      return buildPreview(filename, format, file.getSize(), accumulator);
    } catch (IOException ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "文件预检失败: " + ex.getMessage());
    }
  }

  private void addCheck(List<LinkCheckResponse> checks, String key, String label, String url) {
    if (url.isBlank()) {
      return;
    }
    checks.add(checkUrl(key, label, url));
  }

  private LinkCheckResponse checkUrl(String key, String label, String url) {
    try {
      URI uri = URI.create(url);
      if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
        return new LinkCheckResponse(key, label, url, false, 0, "", "仅支持 http/https 链接");
      }
      HttpRequest head = HttpRequest.newBuilder(uri)
          .method("HEAD", HttpRequest.BodyPublishers.noBody())
          .timeout(Duration.ofSeconds(8))
          .header("User-Agent", "VASP-Show-quality-preflight")
          .build();
      HttpResponse<Void> response = httpClient.send(head, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() == 405 || response.statusCode() == 403) {
        HttpRequest get = HttpRequest.newBuilder(uri)
            .GET()
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", "VASP-Show-quality-preflight")
            .build();
        response = httpClient.send(get, HttpResponse.BodyHandlers.discarding());
      }
      boolean ok = response.statusCode() >= 200 && response.statusCode() < 400;
      return new LinkCheckResponse(
          key,
          label,
          url,
          ok,
          response.statusCode(),
          response.uri().toString(),
          ok ? "可访问" : "HTTP " + response.statusCode()
      );
    } catch (Exception ex) {
      return new LinkCheckResponse(key, label, url, false, 0, "", "验证失败: " + ex.getMessage());
    }
  }

  private PreviewAccumulator parseDelimited(MultipartFile file, String delimiter, String format) throws IOException {
    PreviewAccumulator acc = new PreviewAccumulator();
    try (BufferedReader reader = openTextReader(file)) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        return acc;
      }
      List<String> headers = splitDelimited(headerLine, delimiter);
      String line;
      while ((line = reader.readLine()) != null && acc.records < MAX_RECORDS) {
        if (line.isBlank()) {
          continue;
        }
        List<String> values = splitDelimited(line, delimiter);
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(headers.size(), values.size()); i++) {
          row.put(headers.get(i), values.get(i));
        }
        acc.addRecord(row);
      }
    }
    acc.formatNote = format.toUpperCase(Locale.ROOT) + " 表格";
    return acc;
  }

  private PreviewAccumulator parseJson(MultipartFile file, String format) throws IOException {
    PreviewAccumulator acc = new PreviewAccumulator();
    if ("jsonl".equals(format)) {
      try (BufferedReader reader = openTextReader(file)) {
        String line;
        while ((line = reader.readLine()) != null && acc.records < MAX_RECORDS) {
          if (!line.isBlank()) {
            addJsonObject(acc, objectMapper.readTree(line));
          }
        }
      }
    } else {
      JsonNode root = objectMapper.readTree(file.getInputStream());
      JsonNode array = root.isArray() ? root : firstArray(root);
      if (array != null && array.isArray()) {
        for (JsonNode node : array) {
          if (acc.records >= MAX_RECORDS) {
            break;
          }
          addJsonObject(acc, node);
        }
      } else {
        addJsonObject(acc, root);
      }
    }
    acc.formatNote = format.toUpperCase(Locale.ROOT) + " 结构化数据";
    return acc;
  }

  private PreviewAccumulator parseXyz(MultipartFile file) throws IOException {
    PreviewAccumulator acc = new PreviewAccumulator();
    try (BufferedReader reader = openTextReader(file)) {
      String first = reader.readLine();
      int atoms = parseInt(first);
      String comment = reader.readLine();
      Map<String, String> row = new LinkedHashMap<>();
      row.put("atom_count", atoms > 0 ? String.valueOf(atoms) : "");
      row.put("comment", comment == null ? "" : comment);
      row.put("structure", "XYZ coordinates");
      Set<String> elements = new LinkedHashSet<>();
      for (int i = 0; i < atoms && i < 300; i++) {
        String line = reader.readLine();
        if (line == null) {
          break;
        }
        String[] parts = line.trim().split("\\s+");
        if (parts.length >= 4) {
          elements.add(parts[0]);
        }
      }
      row.put("composition", String.join("", elements));
      acc.addRecord(row);
    }
    acc.formatNote = "XYZ 坐标文件";
    return acc;
  }

  private PreviewAccumulator parseCif(MultipartFile file) throws IOException {
    PreviewAccumulator acc = new PreviewAccumulator();
    Map<String, String> row = new LinkedHashMap<>();
    try (BufferedReader reader = openTextReader(file)) {
      String line;
      int count = 0;
      while ((line = reader.readLine()) != null && count < 500) {
        count++;
        String trimmed = line.trim();
        if (trimmed.startsWith("_")) {
          String[] parts = trimmed.split("\\s+", 2);
          row.put(parts[0], parts.length > 1 ? parts[1] : "present");
        }
      }
    }
    row.putIfAbsent("structure", "CIF crystallographic structure");
    acc.addRecord(row);
    acc.formatNote = "CIF 晶体结构";
    return acc;
  }

  private PreviewAccumulator parseHdf5(MultipartFile file) throws IOException {
    Path temp = Files.createTempFile("vasp-show-preview-", ".h5");
    try {
      file.transferTo(temp);
      PreviewAccumulator acc = new PreviewAccumulator();
      try (HdfFile hdf = new HdfFile(temp)) {
        collectHdf5Datasets("", hdf, acc, 0);
      }
      acc.records = Math.max(1, acc.records);
      acc.formatNote = "HDF5/H5 分层数据";
      return acc;
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  private PreviewAccumulator parseTextFallback(MultipartFile file, String format) throws IOException {
    PreviewAccumulator acc = new PreviewAccumulator();
    try (BufferedReader reader = openTextReader(file)) {
      String line;
      int rows = 0;
      while ((line = reader.readLine()) != null && rows < 20) {
        if (!line.isBlank()) {
          acc.addField("line_" + rows, "text", line);
          rows++;
        }
      }
      acc.records = rows;
    }
    acc.formatNote = format + " 文本预览";
    return acc;
  }

  private void collectHdf5Datasets(String path, Group group, PreviewAccumulator acc, int depth) {
    if (depth > 4 || acc.fields.size() >= MAX_FIELDS) {
      return;
    }
    group.getChildren().forEach((name, node) -> {
      if (acc.fields.size() >= MAX_FIELDS) {
        return;
      }
      String current = path + "/" + name;
      if (node instanceof Dataset dataset) {
        int[] dims = dataset.getDimensions();
        acc.addField(current, "dataset" + dimensionsLabel(dims), sampleHdfValue(dataset));
        acc.records = Math.max(acc.records, dims.length > 0 ? Math.min(dims[0], MAX_RECORDS) : 1);
      } else if (node instanceof Group child) {
        collectHdf5Datasets(current, child, acc, depth + 1);
      }
    });
  }

  private List<String> runScientificPreview(MultipartFile file, String filename, String format) {
    if (!supportsScientificProbe(format)) {
      if ("hdf5".equals(format)) {
        return List.of("科学结构预检：HDF5/H5 已完成层级字段扫描；复杂 group 语义需要专用解析适配器后再做 ASE/pymatgen 全量验证。");
      }
      return List.of();
    }
    Path script = resolveScientificProbeScript();
    if (script == null) {
      return List.of("科学结构预检：未找到 python/quality_scientific_probe.py，暂未执行 ASE/pymatgen 抽样验证。");
    }

    String suffix = suffixFor(filename, format);
    Path temp = null;
    try {
      temp = Files.createTempFile("vasp-show-science-preview-", suffix);
      try (InputStream input = file.getInputStream()) {
        Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
      }
      Process process = new ProcessBuilder(
          pythonCommand,
          script.toString(),
          "--file",
          temp.toString(),
          "--format",
          format,
          "--max-records",
          String.valueOf(MAX_RECORDS)
      )
          .redirectErrorStream(true)
          .start();
      boolean finished = process.waitFor(20, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        return List.of("科学结构预检：ASE/pymatgen 抽样验证超过 20 秒，已终止；建议在后台任务中执行全量验证。");
      }
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      if (output.isBlank()) {
        return List.of("科学结构预检：Python 脚本未返回结果，请检查 Python 环境。");
      }
      JsonNode result = objectMapper.readTree(output.substring(Math.max(0, output.indexOf("{"))));
      List<String> notes = new ArrayList<>();
      String engine = result.path("engine").asText("ASE/pymatgen");
      int score = result.path("score").asInt(0);
      String summary = result.path("summary").asText("未返回摘要");
      notes.add("科学结构预检：" + engine + " 得分 " + score + "；" + summary);
      JsonNode recommendations = result.path("recommendations");
      if (recommendations.isArray()) {
        for (JsonNode item : recommendations) {
          String text = item.asText("");
          if (!text.isBlank()) {
            notes.add(text);
          }
        }
      }
      return List.copyOf(notes);
    } catch (Exception ex) {
      return List.of("科学结构预检：未能调用 Python/ASE/pymatgen，原因：" + ex.getMessage());
    } finally {
      if (temp != null) {
        try {
          Files.deleteIfExists(temp);
        } catch (IOException ignored) {
          // Temporary preview files are best-effort cleanup only.
        }
      }
    }
  }

  private boolean supportsScientificProbe(String format) {
    return "xyz".equals(format) || "cif".equals(format);
  }

  private Path resolveScientificProbeScript() {
    List<Path> candidates = List.of(
        Path.of("python", "quality_scientific_probe.py"),
        Path.of("..", "python", "quality_scientific_probe.py")
    );
    return candidates.stream()
        .filter(Files::isRegularFile)
        .findFirst()
        .orElse(null);
  }

  private String suffixFor(String filename, String format) {
    String cleaned = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
    int dot = cleaned.lastIndexOf('.');
    if (dot >= 0 && dot < cleaned.length() - 1) {
      String suffix = cleaned.substring(dot);
      if (suffix.length() <= 12 && suffix.matches("\\.[a-z0-9]+")) {
        return suffix;
      }
    }
    return switch (format) {
      case "cif" -> ".cif";
      case "xyz" -> ".xyz";
      default -> ".dat";
    };
  }

  private FilePreviewResponse buildPreview(String filename, String format, long size, PreviewAccumulator acc) {
    List<FileFieldProfileResponse> fields = acc.fields.values().stream()
        .limit(MAX_FIELDS)
        .map(FieldProfile::toResponse)
        .toList();
    Set<String> mapped = new LinkedHashSet<>();
    fields.forEach(field -> {
      if (!field.mappedField().isBlank()) {
        mapped.add(field.mappedField());
      }
    });
    List<String> required = List.of("标识", "组成/元素", "原子数", "结构坐标", "目标性质", "来源链接/DOI", "计算方法");
    List<String> missing = required.stream()
        .filter(item -> !mapped.contains(item))
        .toList();
    int score = Math.max(5, (int) Math.round((required.size() - missing.size()) * 100.0 / required.size()));
    List<String> recommendations = new ArrayList<>();
    if (mapped.contains("结构坐标")) {
      recommendations.add("检测到结构字段，后续应校验坐标单位、晶胞、周期性和原子数一致性。");
    }
    if (!mapped.contains("目标性质")) {
      recommendations.add("未识别到明确目标性质字段，请补充 energy、force、band gap、模量、介电等标签说明。");
    }
    if (!mapped.contains("来源链接/DOI")) {
      recommendations.add("文件内未发现 DOI 或来源字段，建议在提交审核时填写论文链接和数据下载链接。");
    }
    if (missing.isEmpty()) {
      recommendations.add("字段覆盖较完整，可以提交超级管理员审核并配置正式入库适配器。");
    }
    recommendations.addAll(acc.scientificNotes);
    return new FilePreviewResponse(
        filename,
        acc.formatNote == null ? format : acc.formatNote,
        size,
        acc.records,
        score,
        "抽样 " + acc.records + " 条/组记录，识别到 " + fields.size() + " 个字段。",
        fields,
        List.copyOf(mapped),
        missing,
        List.copyOf(recommendations)
    );
  }

  private void addJsonObject(PreviewAccumulator acc, JsonNode node) {
    if (node == null || !node.isObject()) {
      return;
    }
    Map<String, String> row = new LinkedHashMap<>();
    node.fields().forEachRemaining(entry -> row.put(entry.getKey(), scalarPreview(entry.getValue())));
    acc.addRecord(row);
  }

  private JsonNode firstArray(JsonNode node) {
    if (node == null || !node.isObject()) {
      return null;
    }
    for (JsonNode child : node) {
      if (child.isArray()) {
        return child;
      }
    }
    return null;
  }

  private BufferedReader openTextReader(MultipartFile file) throws IOException {
    if (file.getOriginalFilename() != null && file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".gz")) {
      return new BufferedReader(new InputStreamReader(new GZIPInputStream(file.getInputStream()), StandardCharsets.UTF_8));
    }
    return new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
  }

  private List<String> splitDelimited(String line, String delimiter) {
    List<String> values = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    char sep = delimiter.charAt(0);
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') {
        quoted = !quoted;
      } else if (ch == sep && !quoted) {
        values.add(current.toString().trim());
        current.setLength(0);
      } else {
        current.append(ch);
      }
    }
    values.add(current.toString().trim());
    return values;
  }

  private String detectFormat(String filename) {
    String lower = filename.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".csv") || lower.endsWith(".csv.gz")) return "csv";
    if (lower.endsWith(".tsv") || lower.endsWith(".txt")) return "tsv";
    if (lower.endsWith(".jsonl") || lower.endsWith(".ndjson")) return "jsonl";
    if (lower.endsWith(".json") || lower.endsWith(".json.gz")) return "json";
    if (lower.endsWith(".xyz") || lower.endsWith(".extxyz")) return "xyz";
    if (lower.endsWith(".cif")) return "cif";
    if (lower.endsWith(".h5") || lower.endsWith(".hdf5")) return "hdf5";
    return "unknown";
  }

  private String mappedFieldFor(String name) {
    String key = name.toLowerCase(Locale.ROOT);
    if (key.matches(".*(id|identifier|material|mp-|jid|source).*")) return "标识";
    if (key.matches(".*(composition|formula|species|element|atom_types|atomic_numbers).*")) return "组成/元素";
    if (key.matches(".*(atom_count|natoms|num_atoms|n_atoms).*")) return "原子数";
    if (key.matches(".*(coord|position|structure|geometry|lattice|cell|cif|xyz).*")) return "结构坐标";
    if (key.matches(".*(energy|force|gap|homo|lumo|modulus|dielectric|phonon|target|label|property).*")) return "目标性质";
    if (key.matches(".*(doi|url|link|source|paper).*")) return "来源链接/DOI";
    if (key.matches(".*(functional|basis|software|calculator|method|theory|vasp|orca|gaussian|pbe|wb97).*")) return "计算方法";
    return "";
  }

  private String inferType(String value) {
    if (value == null || value.isBlank()) return "empty";
    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) return "boolean";
    try {
      Double.parseDouble(value);
      return "number";
    } catch (NumberFormatException ignored) {
      return value.startsWith("[") || value.startsWith("{") ? "object/array" : "text";
    }
  }

  private String scalarPreview(JsonNode node) {
    if (node == null || node.isNull()) return "";
    if (node.isValueNode()) return node.asText();
    if (node.isArray()) return "array[" + node.size() + "]";
    if (node.isObject()) return "object{" + node.size() + "}";
    return node.toString();
  }

  private String sampleHdfValue(Dataset dataset) {
    try {
      Object data = dataset.getData();
      if (data == null) return "";
      return data.getClass().isArray() ? "array" : String.valueOf(data);
    } catch (Exception ex) {
      return "present";
    }
  }

  private String dimensionsLabel(int[] dims) {
    if (dims == null || dims.length == 0) return "";
    StringBuilder label = new StringBuilder("[");
    for (int i = 0; i < dims.length; i++) {
      if (i > 0) label.append("x");
      label.append(dims[i]);
    }
    return label.append("]").toString();
  }

  private int parseInt(String raw) {
    try {
      return Integer.parseInt(raw == null ? "" : raw.trim());
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  private String normalizeDoi(String value) {
    String raw = value == null ? "" : value.trim();
    if (raw.isBlank()) return "";
    if (raw.startsWith("http://") || raw.startsWith("https://")) return raw;
    return "https://doi.org/" + raw.replaceFirst("^doi:\\s*", "");
  }

  private String cleanUrl(String value) {
    return value == null ? "" : value.trim();
  }

  private final class PreviewAccumulator {
    private final Map<String, FieldProfile> fields = new LinkedHashMap<>();
    private final List<String> scientificNotes = new ArrayList<>();
    private int records = 0;
    private String formatNote;

    private void addRecord(Map<String, String> row) {
      records++;
      row.forEach((key, value) -> addField(key, inferType(value), value));
    }

    private void addField(String name, String type, String value) {
      if (fields.size() >= MAX_FIELDS && !fields.containsKey(name)) {
        return;
      }
      fields.computeIfAbsent(name, key -> new FieldProfile(key, mappedFieldFor(key)))
          .add(type, value);
    }
  }

  private static final class FieldProfile {
    private final String name;
    private final String mappedField;
    private long filled;
    private long sampled;
    private String type = "";
    private final List<String> examples = new ArrayList<>();

    private FieldProfile(String name, String mappedField) {
      this.name = name;
      this.mappedField = mappedField;
    }

    private void add(String inferredType, String value) {
      sampled++;
      if (type.isBlank() && inferredType != null) {
        type = inferredType;
      }
      if (value != null && !value.isBlank()) {
        filled++;
        if (examples.size() < 3) {
          examples.add(value.length() > 80 ? value.substring(0, 80) + "..." : value);
        }
      }
    }

    private FileFieldProfileResponse toResponse() {
      return new FileFieldProfileResponse(
          name,
          type.isBlank() ? "unknown" : type,
          filled,
          sampled,
          mappedField,
          List.copyOf(examples)
      );
    }
  }
}
