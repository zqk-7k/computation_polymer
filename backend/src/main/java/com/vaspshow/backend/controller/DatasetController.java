package com.vaspshow.backend.controller;

import com.vaspshow.backend.dto.ConformerResponse;
import com.vaspshow.backend.dto.DatasetCardResponse;
import com.vaspshow.backend.dto.DatasetCatalogResponse;
import com.vaspshow.backend.dto.DatasetDetailResponse;
import com.vaspshow.backend.dto.DatasetPublicationRequest;
import com.vaspshow.backend.dto.DatasetPublicationResponse;
import com.vaspshow.backend.dto.DatasetQualityReportResponse;
import com.vaspshow.backend.dto.DatasetQualityResponse;
import com.vaspshow.backend.dto.DatasetRecordDetailResponse;
import com.vaspshow.backend.dto.DatasetRecordsResponse;
import com.vaspshow.backend.dto.DatasetStatsResponse;
import com.vaspshow.backend.dto.GroupDetailResponse;
import com.vaspshow.backend.dto.HealthResponse;
import com.vaspshow.backend.dto.FilePreviewResponse;
import com.vaspshow.backend.dto.LinkValidationRequest;
import com.vaspshow.backend.dto.LinkValidationResponse;
import com.vaspshow.backend.dto.QualityFieldDictionaryResponse;
import com.vaspshow.backend.dto.QualityIssueResponse;
import com.vaspshow.backend.dto.QualityIssueReviewRequest;
import com.vaspshow.backend.dto.QualityIssueReviewResponse;
import com.vaspshow.backend.dto.QualityOverviewResponse;
import com.vaspshow.backend.dto.QualityPublishDecisionRequest;
import com.vaspshow.backend.dto.QualityPublishDecisionResponse;
import com.vaspshow.backend.dto.QualityRunResponse;
import com.vaspshow.backend.dto.QualityRunReviewRequest;
import com.vaspshow.backend.dto.QualityRunReviewResponse;
import com.vaspshow.backend.service.AniDatasetService;
import com.vaspshow.backend.service.AuthService;
import com.vaspshow.backend.service.DatasetGovernanceService;
import com.vaspshow.backend.service.DisplayDatasetService;
import com.vaspshow.backend.service.QualityPreflightService;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api")
public class DatasetController {

  private final AniDatasetService aniDatasetService;
  private final DisplayDatasetService displayDatasetService;
  private final AuthService authService;
  private final DatasetGovernanceService governanceService;
  private final QualityPreflightService preflightService;

  public DatasetController(
      AniDatasetService aniDatasetService,
      DisplayDatasetService displayDatasetService,
      AuthService authService,
      DatasetGovernanceService governanceService,
      QualityPreflightService preflightService
  ) {
    this.aniDatasetService = aniDatasetService;
    this.displayDatasetService = displayDatasetService;
    this.authService = authService;
    this.governanceService = governanceService;
    this.preflightService = preflightService;
  }

  @GetMapping("/health")
  public HealthResponse health() {
    return new HealthResponse("ok",
        DisplayDatasetService.ANI_DATASET_ID + ","
            + DisplayDatasetService.LMDB_DATASET_ID + ","
            + DisplayDatasetService.OPENPOLY_DATASET_ID + ","
            + DisplayDatasetService.ANI1X_DATASET_ID + ","
            + DisplayDatasetService.TRANSITION1X_DATASET_ID + ","
            + DisplayDatasetService.TWOD_MATPEDIA_DATASET_ID + ","
            + DisplayDatasetService.JARVIS_3D_DATASET_ID + ","
            + DisplayDatasetService.JARVIS_2D_DATASET_ID + ","
            + DisplayDatasetService.POLYMER_GENOME_DATASET_ID + ","
            + DisplayDatasetService.QMOF_DATASET_ID + ","
            + DisplayDatasetService.MATBENCH_WBM_DATASET_ID + ","
            + DisplayDatasetService.MATBENCH_MP_ENERGIES_DATASET_ID + ","
            + DisplayDatasetService.MATBENCH_PHONONDB_DATASET_ID + ","
            + DisplayDatasetService.HYDROCARBONS_GAP_DATASET_ID + ","
            + DisplayDatasetService.MATBENCH_V01_DIELECTRIC_DATASET_ID + ","
            + DisplayDatasetService.MATBENCH_V01_JDFT2D_DATASET_ID + ","
            + DisplayDatasetService.MATBENCH_V01_PHONONS_DATASET_ID + ","
            + DisplayDatasetService.MATBENCH_V01_PEROVSKITES_DATASET_ID + ","
            + DisplayDatasetService.MATBENCH_V01_LOG_GVRH_DATASET_ID + ","
            + DisplayDatasetService.MATBENCH_V01_LOG_KVRH_DATASET_ID + ","
            + DisplayDatasetService.QM9_DATASET_ID);
  }

  @GetMapping("/datasets")
  public List<DatasetCardResponse> datasets(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    return visibleDatasets(authorization);
  }

  @GetMapping("/datasets/catalog")
  public List<DatasetCatalogResponse> datasetCatalog(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    if (governanceService.canViewHidden(authorization)) {
      return displayDatasetService.listDatasetCatalog();
    }
    return displayDatasetService.listDatasetCatalog().stream()
        .filter(item -> governanceService.isPublished(item.id()))
        .toList();
  }

  @GetMapping("/datasets/{datasetId}")
  public DatasetDetailResponse dataset(
      @PathVariable String datasetId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    governanceService.requireVisible(datasetId, authorization);
    return displayDatasetService.getDatasetDetail(datasetId);
  }

  @GetMapping("/datasets/{datasetId}/records")
  public DatasetRecordsResponse records(
      @PathVariable String datasetId,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Integer offset,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) Double energyMin,
      @RequestParam(required = false) Double energyMax,
      @RequestParam(required = false) Integer atomMin,
      @RequestParam(required = false) Integer atomMax,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    governanceService.requireVisible(datasetId, authorization);
    return displayDatasetService.listRecords(
        datasetId,
        search,
        offset == null ? 0 : offset,
        limit == null ? 24 : limit,
        energyMin,
        energyMax,
        atomMin,
        atomMax
    );
  }

  @GetMapping("/datasets/{datasetId}/stats")
  public DatasetStatsResponse stats(
      @PathVariable String datasetId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    governanceService.requireVisible(datasetId, authorization);
    return displayDatasetService.getDatasetStats(datasetId);
  }

  @GetMapping("/quality/overview")
  public QualityOverviewResponse qualityOverview(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    QualityOverviewResponse overview = displayDatasetService.getQualityOverview();
    if (governanceService.canViewHidden(authorization)) {
      return overview;
    }
    List<String> visibleIds = visibleDatasets(authorization).stream().map(DatasetCardResponse::id).toList();
    List<com.vaspshow.backend.dto.DatasetQualityResponse> datasets = overview.datasets().stream()
        .filter(item -> visibleIds.contains(item.datasetId()))
        .toList();
    List<com.vaspshow.backend.dto.QualityIssueResponse> issues = List.of();
    long totalRecords = datasets.stream().mapToLong(com.vaspshow.backend.dto.DatasetQualityResponse::totalRecords).sum();
    int averageScore = datasets.isEmpty()
        ? 0
        : (int) Math.round(datasets.stream().mapToInt(com.vaspshow.backend.dto.DatasetQualityResponse::score).average().orElse(0));
    return new QualityOverviewResponse(
        overview.generatedAt(),
        datasets.size(),
        totalRecords,
        averageScore,
        overview.scope() + " 当前视图仅包含已发布数据集。",
        overview.gates(),
        datasets,
        issues,
        List.of(),
        List.of()
    );
  }

  @GetMapping("/quality/runs")
  public List<QualityRunResponse> qualityRuns(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    authService.requireQualityReviewAccess(authorization);
    return displayDatasetService.listQualityRuns();
  }

  @GetMapping("/quality/runs/{runId}")
  public QualityRunResponse qualityRun(
      @PathVariable String runId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    authService.requireQualityReviewAccess(authorization);
    return displayDatasetService.getQualityRun(runId);
  }

  @GetMapping("/quality/datasets/{datasetId}/summary")
  public DatasetQualityResponse datasetQualitySummary(
      @PathVariable String datasetId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    governanceService.requireVisible(datasetId, authorization);
    return displayDatasetService.getDatasetQualitySummary(datasetId);
  }

  @GetMapping("/quality/datasets/{datasetId}/issues")
  public List<QualityIssueResponse> datasetQualityIssues(
      @PathVariable String datasetId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    authService.requireQualityReviewAccess(authorization);
    governanceService.requireVisible(datasetId, authorization);
    return displayDatasetService.getDatasetQualityIssues(datasetId);
  }

  @GetMapping("/quality/datasets/{datasetId}/field-dictionary")
  public List<QualityFieldDictionaryResponse> datasetFieldDictionary(
      @PathVariable String datasetId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    authService.requireQualityReviewAccess(authorization);
    governanceService.requireVisible(datasetId, authorization);
    return displayDatasetService.getDatasetFieldDictionary(datasetId);
  }

  @GetMapping("/quality/datasets/{datasetId}/report")
  public DatasetQualityReportResponse datasetQualityReport(
      @PathVariable String datasetId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    authService.requireQualityReviewAccess(authorization);
    governanceService.requireVisible(datasetId, authorization);
    DatasetQualityResponse summary = displayDatasetService.getDatasetQualitySummary(datasetId);
    List<QualityIssueResponse> issues = displayDatasetService.getDatasetQualityIssues(datasetId);
    List<QualityFieldDictionaryResponse> fields = displayDatasetService.getDatasetFieldDictionary(datasetId);
    DatasetCardResponse card = displayDatasetService.listDatasets().stream()
        .filter(item -> item.id().equals(datasetId))
        .findFirst()
        .orElseThrow(() -> new com.vaspshow.backend.exception.ApiException(
            org.springframework.http.HttpStatus.NOT_FOUND,
            "未知数据集: " + datasetId
        ));
    return new DatasetQualityReportResponse(
        displayDatasetService.getQualityRunId(),
        summary,
        issues,
        fields,
        governanceService.getPublication(card),
        displayDatasetService.buildDatasetQualityReportText(summary, issues, fields)
    );
  }

  @PatchMapping("/quality/issues/{issueId}/review")
  public QualityIssueReviewResponse reviewQualityIssue(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @PathVariable String issueId,
      @RequestBody QualityIssueReviewRequest request
  ) {
    return governanceService.reviewIssue(authorization, issueId, request);
  }

  @PostMapping("/quality/runs/{runId}/submit-review")
  public QualityRunReviewResponse submitQualityRunReview(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @PathVariable String runId,
      @RequestBody QualityRunReviewRequest request
  ) {
    displayDatasetService.getQualityRun(runId);
    return governanceService.submitRunReview(authorization, runId, request);
  }

  @PostMapping("/quality/datasets/{datasetId}/publish-decision")
  public QualityPublishDecisionResponse publishQualityDecision(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @PathVariable String datasetId,
      @RequestBody QualityPublishDecisionRequest request
  ) {
    DatasetCardResponse card = displayDatasetService.listDatasets().stream()
        .filter(item -> item.id().equals(datasetId))
        .findFirst()
        .orElseThrow(() -> new com.vaspshow.backend.exception.ApiException(
            org.springframework.http.HttpStatus.NOT_FOUND,
            "未知数据集: " + datasetId
        ));
    return governanceService.publishDecision(authorization, card, request);
  }

  @PostMapping("/quality/validate-links")
  public LinkValidationResponse validateLinks(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @RequestBody LinkValidationRequest request
  ) {
    authService.requireDatasetSubmissionAccess(authorization);
    return preflightService.validateLinks(request);
  }

  @PostMapping(value = "/quality/preview-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public FilePreviewResponse previewFile(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @RequestParam("file") MultipartFile file
  ) {
    authService.requireDatasetSubmissionAccess(authorization);
    return preflightService.previewFile(file);
  }

  @GetMapping("/datasets/publication")
  public List<DatasetPublicationResponse> datasetPublication(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    return governanceService.listPublication(authorization, displayDatasetService.listDatasets());
  }

  @PatchMapping("/datasets/{datasetId}/publication")
  public DatasetPublicationResponse updateDatasetPublication(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @PathVariable String datasetId,
      @RequestBody DatasetPublicationRequest request
  ) {
    DatasetCardResponse card = displayDatasetService.listDatasets().stream()
        .filter(item -> item.id().equals(datasetId))
        .findFirst()
        .orElseThrow(() -> new com.vaspshow.backend.exception.ApiException(
            org.springframework.http.HttpStatus.NOT_FOUND,
            "未知数据集: " + datasetId
        ));
    return governanceService.updatePublication(authorization, card, request);
  }

  @GetMapping("/datasets/{datasetId}/records/{recordId}")
  public DatasetRecordDetailResponse record(
      @PathVariable String datasetId,
      @PathVariable long recordId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    governanceService.requireVisible(datasetId, authorization);
    return displayDatasetService.getRecord(datasetId, recordId);
  }

  @GetMapping("/datasets/{datasetId}/groups/{groupId}")
  public GroupDetailResponse group(
      @PathVariable String datasetId,
      @PathVariable String groupId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    governanceService.requireVisible(datasetId, authorization);
    return aniDatasetService.getGroup(datasetId, groupId);
  }

  @GetMapping("/datasets/{datasetId}/groups/{groupId}/conformers/{index}")
  public ConformerResponse conformer(
      @PathVariable String datasetId,
      @PathVariable String groupId,
      @PathVariable int index,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    governanceService.requireVisible(datasetId, authorization);
    return aniDatasetService.getConformer(datasetId, groupId, index);
  }

  @GetMapping(
      value = "/datasets/{datasetId}/groups/{groupId}/conformers/{index}/download.csv",
      produces = "text/csv"
  )
  public ResponseEntity<byte[]> downloadConformerCsv(
      @PathVariable String datasetId,
      @PathVariable String groupId,
      @PathVariable int index,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    governanceService.requireVisible(datasetId, authorization);
    authService.requireRegistered(authorization);
    String csv = aniDatasetService.getConformerCsv(datasetId, groupId, index);
    byte[] body = csv.getBytes(StandardCharsets.UTF_8);
    String filename = groupId + "-conformer-" + index + ".csv";
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
        .contentLength(body.length)
        .body(body);
  }

  @GetMapping(
      value = "/datasets/{datasetId}/records/{recordId}/download.csv",
      produces = "text/csv"
  )
  public ResponseEntity<byte[]> downloadRecordCsv(
      @PathVariable String datasetId,
      @PathVariable long recordId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    governanceService.requireVisible(datasetId, authorization);
    authService.requireRegistered(authorization);
    String csv = displayDatasetService.getRecordCsv(datasetId, recordId);
    byte[] body = csv.getBytes(StandardCharsets.UTF_8);
    String filename = datasetId + "-record-" + recordId + ".csv";
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
        .contentLength(body.length)
        .body(body);
  }

  @GetMapping(
      value = "/datasets/{datasetId}/records/{recordId}/download.json",
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public ResponseEntity<DatasetRecordDetailResponse> downloadRecordJson(
      @PathVariable String datasetId,
      @PathVariable long recordId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    governanceService.requireVisible(datasetId, authorization);
    authService.requireRegistered(authorization);
    DatasetRecordDetailResponse record = displayDatasetService.getRecord(datasetId, recordId);
    String filename = datasetId + "-record-" + recordId + ".json";
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
        .body(record);
  }

  @GetMapping(
      value = "/datasets/{datasetId}/download.csv",
      produces = "text/csv"
  )
  public ResponseEntity<StreamingResponseBody> downloadDatasetCsv(
      @PathVariable String datasetId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    authService.requireAdmin(authorization);
    StreamingResponseBody body = outputStream -> {
      OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
      displayDatasetService.writeDatasetCsv(datasetId, writer);
    };
    String filename = datasetId + "-all-display-records.csv";
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
        .body(body);
  }

  private List<DatasetCardResponse> visibleDatasets(String authorization) {
    List<DatasetCardResponse> datasets = displayDatasetService.listDatasets();
    if (governanceService.canViewHidden(authorization)) {
      return datasets;
    }
    return datasets.stream()
        .filter(item -> governanceService.isPublished(item.id()))
        .toList();
  }
}
