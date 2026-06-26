package com.vaspshow.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaspshow.backend.exception.ApiException;
import com.vaspshow.backend.dto.ConformerResponse;
import com.vaspshow.backend.dto.DatasetDetailResponse;
import com.vaspshow.backend.service.AniDatasetService;
import com.vaspshow.backend.service.DatasetIntakeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
    "vasp.auth.db-path=target/test-data/vasp_auth",
    "vasp.intake.db-path=target/test-data/vasp_intake",
    "vasp.auth.admin-password=TestAdminPassword2026!",
    "vasp.auth.super-admin-password=TestSuperAdminPassword2026!"
})
@AutoConfigureMockMvc
class AniDatasetServiceTest {

  private static HttpServer assistantStub;
  private static volatile String lastAssistantRequest = "";

  @DynamicPropertySource
  static void assistantConfiguration(DynamicPropertyRegistry registry) {
    startAssistantStub();
    registry.add("vasp.assistant.base-url",
        () -> "http://127.0.0.1:" + assistantStub.getAddress().getPort());
  }

  @AfterAll
  static void closeAssistantStub() {
    if (assistantStub != null) {
      assistantStub.stop(0);
    }
  }

  @Autowired
  private AniDatasetService service;

  @Autowired
  private DatasetIntakeService intakeService;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void readsExpectedH5Summary() {
    DatasetDetailResponse detail = service.getDatasetDetail(AniDatasetService.DATASET_ID);

    assertThat(detail.rootGroup()).isEqualTo("gdb11_s03");
    assertThat(detail.moleculeGroupCount()).isEqualTo(20);
    assertThat(detail.totalConformers()).isEqualTo(151_200);
    assertThat(detail.minAtoms()).isEqualTo(3);
    assertThat(detail.maxAtoms()).isEqualTo(11);
    assertThat(detail.elements()).containsExactly("C", "H", "N", "O");
    assertThat(detail.groups().get(0).id()).isEqualTo("gdb11_s03-0");
    assertThat(detail.groups().get(0).conformerCount()).isEqualTo(12_960);
  }

  @Test
  void readsSingleConformerCoordinates() {
    ConformerResponse conformer = service.getConformer(
        AniDatasetService.DATASET_ID,
        "gdb11_s03-0",
        0
    );

    assertThat(conformer.groupId()).isEqualTo("gdb11_s03-0");
    assertThat(conformer.index()).isZero();
    assertThat(conformer.smiles()).contains("C");
    assertThat(conformer.species()).hasSize(11);
    assertThat(conformer.atoms()).hasSize(11);
    assertThat(conformer.atoms().get(6).x()).isCloseTo(1.1129515, within(0.000001));
    assertThat(conformer.radiusOfGyration()).isPositive();
  }

  @Test
  void exposesDatasetEndpoint() throws Exception {
    mockMvc.perform(get("/api/datasets/ani_gdb_s03"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rootGroup").value("gdb11_s03"))
        .andExpect(jsonPath("$.moleculeGroupCount").value(20))
        .andExpect(jsonPath("$.totalConformers").value(151200))
        .andExpect(jsonPath("$.links[0].type").value("paper"))
        .andExpect(jsonPath("$.links[1].type").value("data"));
  }

  @Test
  void exposesDisplayDatasets() throws Exception {
    mockMvc.perform(get("/api/datasets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == 'ani_gdb_s03')]").exists())
        .andExpect(jsonPath("$[?(@.id == 'data0000_aselmdb')]").exists())
        .andExpect(jsonPath("$[?(@.id == 'openpoly_calculated')]").exists())
        .andExpect(jsonPath("$[?(@.id == 'ani1x_less_is_more')]").exists())
        .andExpect(jsonPath("$[?(@.id == 'transition1x')]").exists())
        .andExpect(jsonPath("$[?(@.id == 'twod_matpedia')]").exists())
        .andExpect(jsonPath("$[?(@.id == 'jarvis_dft_3d')]").exists())
        .andExpect(jsonPath("$[?(@.id == 'jarvis_dft_2d')]").exists())
        .andExpect(jsonPath("$[?(@.id == 'polymer_genome_1073')]").exists())
        .andExpect(jsonPath("$[?(@.id == 'qmof_database')]").exists());
  }

  @Test
  void exposesDiscoveryCatalogCapabilities() throws Exception {
    mockMvc.perform(get("/api/datasets/catalog"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == 'transition1x')].dataType").value("反应路径"))
        .andExpect(jsonPath("$[?(@.id == 'transition1x')].properties[?(@ == '反应路径')]").exists())
        .andExpect(jsonPath("$[?(@.id == 'qmof_database')].software[?(@ == 'VASP')]").exists())
        .andExpect(jsonPath("$[?(@.id == 'ani1x_less_is_more')].displayRecords").value(3114));
  }

  @Test
  void exposesLmdbDisplayRecords() throws Exception {
    mockMvc.perform(get("/api/datasets/data0000_aselmdb/records?limit=1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2524))
        .andExpect(jsonPath("$.records[0].atomCount").value("114"));
  }

  @Test
  void exposesOpenPolyDisplayRecords() throws Exception {
    mockMvc.perform(get("/api/datasets/openpoly_calculated/records?limit=1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1000))
        .andExpect(jsonPath("$.records[0].materialId").value("OMG_polymer_189323"));

    mockMvc.perform(get("/api/datasets/openpoly_calculated/records/153725"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.extraProperties['Theory level']").exists())
        .andExpect(jsonPath("$.atoms").isArray());
  }

  @Test
  void filtersDisplayRecordsOnServer() throws Exception {
    mockMvc.perform(get("/api/datasets/openpoly_calculated/records?limit=1&search=OMG_polymer_189323&atomMax=20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.records[0].materialId").value("OMG_polymer_189323"));

    mockMvc.perform(get("/api/datasets/ani_gdb_s03/records?limit=1&atomMax=5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(12960));

    mockMvc.perform(get("/api/datasets/ani_gdb_s03/records?limit=1&energyMin=-119.063&energyMax=-119.062"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.allOf(
            org.hamcrest.Matchers.greaterThan(0),
            org.hamcrest.Matchers.lessThan(151200)
        )));
  }

  @Test
  @SuppressWarnings("unchecked")
  void hdf5StructureAdapterExtractsAniRows() throws Exception {
    byte[] bytes = Files.readAllBytes(Path.of("src/main/resources/data/ani_gdb_s03.h5"));
    Object sample = downloadSample(bytes, false, "ani_gdb_s03.h5");
    Method method = hdf5StructureMethod();

    List<Map<String, String>> rows = (List<Map<String, String>>) method.invoke(
        intakeService, "HDF5", sample, 2, "intake_hdf5", "ANI HDF5 smoke");

    assertThat(rows).hasSize(2);
    Map<String, String> first = rows.get(0);
    assertThat(first.get("SOURCE_RECORD_ID")).contains("gdb11_s03-0#0");
    assertThat(first.get("ATOM_COUNT")).isEqualTo("11");
    assertThat(first.get("COMPOSITION")).isEqualTo("C3H8");
    assertThat(Double.parseDouble(first.get("ENERGY"))).isCloseTo(-119.062799006, within(1e-9));

    JsonNode structure = objectMapper.readTree(first.get("STRUCTURE_JSON"));
    assertThat(structure.path("format").asText()).isEqualTo("hdf5");
    assertThat(structure.path("sourceGroup").asText()).contains("gdb11_s03-0");
    assertThat(structure.path("conformerIndex").asInt()).isZero();
    assertThat(structure.path("atoms").size()).isEqualTo(11);
    assertThat(structure.path("atoms").get(0).path("element").asText()).isEqualTo("C");
    assertThat(structure.path("atoms").get(0).path("x").asDouble()).isCloseTo(-0.042555, within(1e-6));
  }

  @Test
  void hdf5StructureAdapterRejectsTruncatedSamples() throws Exception {
    byte[] bytes = Files.readAllBytes(Path.of("src/main/resources/data/ani_gdb_s03.h5"));
    Object sample = downloadSample(java.util.Arrays.copyOf(bytes, 4096), true, "ani_gdb_s03.h5");
    Method method = hdf5StructureMethod();

    assertThatThrownBy(() -> method.invoke(
        intakeService, "HDF5", sample, 1, "intake_hdf5", "ANI HDF5 smoke"))
        .hasCauseInstanceOf(ApiException.class);
  }

  @Test
  void rejectsInvalidConformerIndex() throws Exception {
    mockMvc.perform(get("/api/datasets/ani_gdb_s03/groups/gdb11_s03-0/conformers/999999"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("超出范围")));
  }

  @Test
  void guestCannotDownloadConformerCsv() throws Exception {
    mockMvc.perform(get("/api/datasets/ani_gdb_s03/groups/gdb11_s03-0/conformers/0/download.csv"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void registeredUserDownloadsConformerCsv() throws Exception {
    String token = registerTestUser();
    mockMvc.perform(get("/api/datasets/ani_gdb_s03/groups/gdb11_s03-0/conformers/0/download.csv")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"))
        .andExpect(content().string(org.hamcrest.Matchers.startsWith("index,element,x,y,z")));
  }

  @Test
  void registeredUserDownloadsSingleRecordJson() throws Exception {
    String token = registerTestUser();
    mockMvc.perform(get("/api/datasets/openpoly_calculated/records/153725/download.json")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.materialId").value("OMG_polymer_189323"));
  }

  @Test
  void datasetDownloadRequiresAdministrator() throws Exception {
    mockMvc.perform(get("/api/datasets/openpoly_calculated/download.csv"))
        .andExpect(status().isUnauthorized());

    String userToken = registerTestUser();
    mockMvc.perform(get("/api/datasets/openpoly_calculated/download.csv")
            .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());

    String adminResponse = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"username":"admin","password":"TestAdminPassword2026!"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.role").value("ADMIN"))
        .andReturn()
        .getResponse()
        .getContentAsString();
    String adminToken = objectMapper.readTree(adminResponse).path("token").asText();

    mockMvc.perform(get("/api/datasets/openpoly_calculated/download.csv")
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"));
  }

  @Test
  void superAdministratorIsFirstInManagedUsers() throws Exception {
    String response = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"username":"superadmin","password":"TestSuperAdminPassword2026!"}
                """))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
    String token = objectMapper.readTree(response).path("token").asText();

    mockMvc.perform(get("/api/auth/users")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].username").value("superadmin"))
        .andExpect(jsonPath("$[0].role").value("SUPER_ADMIN"));
  }

  @Test
  void exposesPublicDatasetSources() throws Exception {
    mockMvc.perform(get("/api/intake/sources"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.key == 'jarvis')]").exists())
        .andExpect(jsonPath("$[?(@.key == 'figshare-doi')]").exists());
  }

  @Test
  void registeredSubmissionRequiresSuperAdministratorReview() throws Exception {
    String userToken = registerTestUser();
    String submissionResponse = mockMvc.perform(post("/api/intake/submissions")
            .header("Authorization", "Bearer " + userToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "datasetName": "候选公开计算数据集",
                  "dataType": "晶体材料",
                  "description": "用于测试审核与受控接入流程。",
                  "paperUrl": "https://doi.org/10.1234/example",
                  "dataUrl": "https://example.org/dataset.json",
                  "dataFormat": "JSON",
                  "license": "CC BY 4.0",
                  "providedFields": "structure, energy, band gap"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andReturn().getResponse().getContentAsString();
    long id = objectMapper.readTree(submissionResponse).path("id").asLong();

    mockMvc.perform(patch("/api/intake/submissions/" + id + "/review")
            .header("Authorization", "Bearer " + userToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"decision\":\"APPROVED\",\"note\":\"\"}"))
        .andExpect(status().isForbidden());

    String administratorResponse = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"username":"superadmin","password":"TestSuperAdminPassword2026!"}
                """))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    String administratorToken = objectMapper.readTree(administratorResponse).path("token").asText();

    mockMvc.perform(patch("/api/intake/submissions/" + id + "/review")
            .header("Authorization", "Bearer " + administratorToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"decision\":\"APPROVED\",\"note\":\"来源清晰\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pipelineStage").value("SOURCE_APPROVED"));

    mockMvc.perform(post("/api/intake/submissions/" + id + "/prepare")
            .header("Authorization", "Bearer " + administratorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pipelineStage").value("ADAPTER_REQUIRED"));
  }

  @Test
  void minimalDatasetSubmissionUsesDefaults() throws Exception {
    String userToken = registerTestUser();
    mockMvc.perform(post("/api/intake/submissions")
            .header("Authorization", "Bearer " + userToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "description": "这是一条只提供说明和论文链接的候选数据集线索。",
                  "paperUrl": "https://doi.org/10.1234/minimal-example"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.datasetName").value("待命名数据集"))
        .andExpect(jsonPath("$.dataType").value("待分类"))
        .andExpect(jsonPath("$.dataFormat").value("待识别"))
        .andExpect(jsonPath("$.providedFields").value("待管理员核对"));
  }

  @Test
  void assistantRequiresRegisteredUser() throws Exception {
    mockMvc.perform(post("/api/assistant/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"messages":[{"role":"user","content":"有哪些数据集？"}]}
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("登录后可使用智能助手"));
  }

  @Test
  void assistantUsesGroundedRecordContext() throws Exception {
    String token = registerTestUser();

    mockMvc.perform(post("/api/assistant/chat")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "datasetId": "openpoly_calculated",
                  "recordId": 153725,
                  "messages": [{"role":"user","content":"这个记录有哪些已提供属性？"}]
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.model").value("qwen3:8b"))
        .andExpect(jsonPath("$.answer").value("基于受控上下文的测试回答。"))
        .andExpect(jsonPath("$.contextLabel").value(org.hamcrest.Matchers.containsString("记录 #153725")));

    assertThat(lastAssistantRequest)
        .contains("openpoly_calculated")
        .contains("OMG_polymer_189323")
        .contains("当前平台上下文未提供")
        .contains("\"think\":false");
  }

  private String registerTestUser() throws Exception {
    String username = "user-" + UUID.randomUUID().toString().substring(0, 8);
    String response = mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"username":"%s","displayName":"测试用户","password":"TestPassword2026!"}
                """.formatted(username)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.role").value("USER"))
        .andReturn()
        .getResponse()
        .getContentAsString();
    JsonNode session = objectMapper.readTree(response);
    return session.path("token").asText();
  }

  private static Object downloadSample(byte[] bytes, boolean truncated, String fileName) throws Exception {
    Class<?> sampleType = Class.forName("com.vaspshow.backend.service.DatasetIntakeService$DownloadSample");
    Constructor<?> constructor = sampleType.getDeclaredConstructor(
        byte[].class, long.class, boolean.class, String.class, String.class);
    constructor.setAccessible(true);
    return constructor.newInstance(bytes, (long) bytes.length, truncated, fileName, "application/x-hdf5");
  }

  private static Method hdf5StructureMethod() throws Exception {
    Class<?> sampleType = Class.forName("com.vaspshow.backend.service.DatasetIntakeService$DownloadSample");
    Method method = DatasetIntakeService.class.getDeclaredMethod(
        "extractStructureRecords", String.class, sampleType, int.class, String.class, String.class);
    method.setAccessible(true);
    return method;
  }

  private static synchronized void startAssistantStub() {
    if (assistantStub != null) {
      return;
    }
    try {
      assistantStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      assistantStub.createContext("/api/chat", exchange -> {
        lastAssistantRequest = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        byte[] response = """
            {"model":"qwen3:8b","message":{"role":"assistant","content":"基于受控上下文的测试回答。"},"done":true}
            """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
      });
      assistantStub.start();
    } catch (IOException ex) {
      throw new IllegalStateException("无法启动助手测试服务", ex);
    }
  }
}
