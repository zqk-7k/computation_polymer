package com.vaspshow.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaspshow.backend.config.DatasetProperties;
import com.vaspshow.backend.dto.AtomCoordinateResponse;
import com.vaspshow.backend.dto.DatasetStatsResponse;
import com.vaspshow.backend.dto.DatasetCatalogResponse;
import com.vaspshow.backend.dto.DatasetCardResponse;
import com.vaspshow.backend.dto.DatasetDetailResponse;
import com.vaspshow.backend.dto.DatasetLinkResponse;
import com.vaspshow.backend.dto.DatasetRecordDetailResponse;
import com.vaspshow.backend.dto.DatasetRecordSummaryResponse;
import com.vaspshow.backend.dto.DatasetRecordsResponse;
import com.vaspshow.backend.dto.ElementCountResponse;
import com.vaspshow.backend.dto.HistogramBinResponse;
import com.vaspshow.backend.dto.PropertyAvailabilityResponse;
import com.vaspshow.backend.dto.QualityAuditItemResponse;
import com.vaspshow.backend.dto.QualityAuditRuleResponse;
import com.vaspshow.backend.dto.QualityAuditStageResponse;
import com.vaspshow.backend.dto.DatasetQualityResponse;
import com.vaspshow.backend.dto.QualityFieldDictionaryResponse;
import com.vaspshow.backend.dto.QualityCoverageMetricResponse;
import com.vaspshow.backend.dto.QualityGateResponse;
import com.vaspshow.backend.dto.QualityIssueResponse;
import com.vaspshow.backend.dto.QualityOverviewResponse;
import com.vaspshow.backend.dto.QualityRunResponse;
import com.vaspshow.backend.exception.ApiException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DisplayDatasetService {

  public static final String ANI_DATASET_ID = "ani_gdb_s03";
  public static final String LMDB_DATASET_ID = "data0000_aselmdb";
  public static final String OPENPOLY_DATASET_ID = "openpoly_calculated";
  public static final String ANI1X_DATASET_ID = "ani1x_less_is_more";
  public static final String TRANSITION1X_DATASET_ID = "transition1x";
  public static final String TWOD_MATPEDIA_DATASET_ID = "twod_matpedia";
  public static final String JARVIS_3D_DATASET_ID = "jarvis_dft_3d";
  public static final String JARVIS_2D_DATASET_ID = "jarvis_dft_2d";
  public static final String POLYMER_GENOME_DATASET_ID = "polymer_genome_1073";
  public static final String QMOF_DATASET_ID = "qmof_database";
  public static final String MATBENCH_WBM_DATASET_ID = "matbench_wbm_summary";
  public static final String MATBENCH_MP_ENERGIES_DATASET_ID = "matbench_mp_energies";
  public static final String MATBENCH_PHONONDB_DATASET_ID = "matbench_phonondb_pbe_103";
  public static final String HYDROCARBONS_GAP_DATASET_ID = "hydrocarbons_gap_ch";
  public static final String MATBENCH_V01_DIELECTRIC_DATASET_ID = "matbench_v01_dielectric";
  public static final String MATBENCH_V01_JDFT2D_DATASET_ID = "matbench_v01_jdft2d";
  public static final String MATBENCH_V01_PHONONS_DATASET_ID = "matbench_v01_phonons";
  public static final String MATBENCH_V01_PEROVSKITES_DATASET_ID = "matbench_v01_perovskites";
  public static final String MATBENCH_V01_LOG_GVRH_DATASET_ID = "matbench_v01_log_gvrh";
  public static final String MATBENCH_V01_LOG_KVRH_DATASET_ID = "matbench_v01_log_kvrh";
  public static final String QM9_DATASET_ID = "qm9_molecular_dft";
  private static final Pattern COMPOSITION_ELEMENT_PATTERN = Pattern.compile("([A-Z][a-z]?)([0-9]*\\.?[0-9]*)");

  private static final Map<Integer, String> ELEMENTS_BY_NUMBER = Map.ofEntries(
      Map.entry(1, "H"),
      Map.entry(3, "Li"),
      Map.entry(6, "C"),
      Map.entry(7, "N"),
      Map.entry(8, "O"),
      Map.entry(9, "F"),
      Map.entry(11, "Na"),
      Map.entry(12, "Mg"),
      Map.entry(15, "P"),
      Map.entry(16, "S"),
      Map.entry(17, "Cl"),
      Map.entry(20, "Ca"),
      Map.entry(28, "Ni"),
      Map.entry(35, "Br"),
      Map.entry(53, "I")
  );

  private static final Map<Integer, String> ANI_GROUP_FORMULAS = Map.ofEntries(
      Map.entry(0, "C3H8"),
      Map.entry(1, "C2H7N"),
      Map.entry(2, "C2H6O"),
      Map.entry(3, "C2H7N"),
      Map.entry(4, "C2H6O"),
      Map.entry(5, "C3H6"),
      Map.entry(6, "C2H4O"),
      Map.entry(7, "CO2"),
      Map.entry(8, "C2H3N"),
      Map.entry(9, "CH4N2"),
      Map.entry(10, "CH3NO"),
      Map.entry(11, "CH2O2"),
      Map.entry(12, "CH4N2"),
      Map.entry(13, "CH3NO"),
      Map.entry(14, "C3H4"),
      Map.entry(15, "C3H6"),
      Map.entry(16, "C2H5NO"),
      Map.entry(17, "C2H4O"),
      Map.entry(18, "HNO2"),
      Map.entry(19, "H2O3")
  );

  private final DatasetProperties properties;
  private final ObjectMapper objectMapper;
  private volatile String jdbcUrl;
  private volatile Connection keepAliveConnection;
  private volatile List<DatasetCardResponse> datasetCardsCache;
  private volatile List<DatasetCatalogResponse> datasetCatalogCache;
  private volatile QualityOverviewResponse qualityOverviewCache;
  private final Map<String, DatasetDetailResponse> datasetDetailCache = new ConcurrentHashMap<>();
  private final Map<String, DatasetStatsResponse> datasetStatsCache = new ConcurrentHashMap<>();
  private final Map<String, Long> displayRecordCountCache = new ConcurrentHashMap<>();
  private volatile Set<String> displayDoiCache;

  public DisplayDatasetService(DatasetProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @PostConstruct
  public void openReadOnlyDatabase() {
    try {
      keepAliveConnection = openConnection();
      listDatasets();
      listDatasetCatalog();
    } catch (SQLException ex) {
      throw new IllegalStateException("打开展示数据库失败: " + ex.getMessage(), ex);
    }
  }

  @PreDestroy
  public void closeReadOnlyDatabase() {
    if (keepAliveConnection == null) {
      return;
    }
    try {
      keepAliveConnection.close();
    } catch (SQLException ignored) {
      // The process is already shutting down.
    }
  }

  public List<DatasetCardResponse> listDatasets() {
    List<DatasetCardResponse> cached = datasetCardsCache;
    if (cached != null) {
      return cached;
    }
    String sql = """
        SELECT DATASET_KEY, COUNT(*) AS N,
               MIN(CAST(NULLIF(ATOM_COUNT, '') AS INT)) AS MIN_ATOMS,
               MAX(CAST(NULLIF(ATOM_COUNT, '') AS INT)) AS MAX_ATOMS
        FROM DISPLAY_RECORDS
        GROUP BY DATASET_KEY
        ORDER BY DATASET_KEY
        """;
    List<DatasetCardResponse> cards = new ArrayList<>();
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(sql);
         ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        String datasetId = rs.getString("DATASET_KEY");
        long count = rs.getLong("N");
        int minAtoms = rs.getInt("MIN_ATOMS");
        int maxAtoms = rs.getInt("MAX_ATOMS");
        displayRecordCountCache.put(datasetId, count);
        cards.add(cardFor(datasetId, count, minAtoms, maxAtoms));
      }
      List<DatasetCardResponse> loaded = List.copyOf(cards);
      datasetCardsCache = loaded;
      return loaded;
    } catch (SQLException ex) {
      throw databaseError(ex);
    }
  }

  public List<DatasetCatalogResponse> listDatasetCatalog() {
    List<DatasetCatalogResponse> cached = datasetCatalogCache;
    if (cached != null) {
      return cached;
    }
    List<DatasetCatalogResponse> loaded = listDatasets().stream()
        .map(this::catalogFor)
        .toList();
    datasetCatalogCache = loaded;
    return loaded;
  }

  public DatasetDetailResponse getDatasetDetail(String datasetId) {
    requireDataset(datasetId);
    DatasetDetailResponse cached = datasetDetailCache.get(datasetId);
    if (cached != null) {
      return cached;
    }
    String sql = """
        SELECT COUNT(*) AS N,
               MIN(CAST(NULLIF(ATOM_COUNT, '') AS INT)) AS MIN_ATOMS,
               MAX(CAST(NULLIF(ATOM_COUNT, '') AS INT)) AS MAX_ATOMS
        FROM DISPLAY_RECORDS
        WHERE DATASET_KEY = ?
        """;
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, datasetId);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next() || rs.getLong("N") == 0) {
          throw new ApiException(HttpStatus.NOT_FOUND, "未知数据集: " + datasetId);
        }
        long count = rs.getLong("N");
        int minAtoms = rs.getInt("MIN_ATOMS");
        int maxAtoms = rs.getInt("MAX_ATOMS");
        DatasetCardResponse card = cardFor(datasetId, count, minAtoms, maxAtoms);
        DatasetDetailResponse detail = new DatasetDetailResponse(
            datasetId,
            card.name(),
            datasetId.equals(ANI_DATASET_ID) ? "gdb11_s03" : "display_records",
            card.method(),
            card.scale(),
            card.intro(),
            card.moleculeGroups(),
            card.totalConformers(),
            minAtoms,
            maxAtoms,
            card.elements(),
            List.of(),
            linksFor(datasetId)
        );
        datasetDetailCache.putIfAbsent(datasetId, detail);
        return datasetDetailCache.get(datasetId);
      }
    } catch (SQLException ex) {
      throw databaseError(ex);
    }
  }

  public DatasetRecordsResponse listRecords(
      String datasetId,
      String search,
      int offset,
      int limit,
      Double energyMin,
      Double energyMax,
      Integer atomMin,
      Integer atomMax
  ) {
    requireDataset(datasetId);
    int safeOffset = Math.max(0, offset);
    int safeLimit = Math.max(1, Math.min(limit, 80));
    String normalized = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    RecordFilters filters = new RecordFilters(normalized, energyMin, energyMax, atomMin, atomMax);
    String filter = buildRecordFilter(filters);

    String countSql = "SELECT COUNT(*) AS N FROM DISPLAY_RECORDS WHERE DATASET_KEY = ? " + filter;
    String pageSql = """
        SELECT ID, DATASET_KEY, SOURCE_RECORD_ID, DATASET_NAME, MATERIAL_NAME, MATERIAL_ID, SMILES,
               COMPOSITION, ATOM_COUNT, ENERGY, HOMO, LUMO, HOMO_LUMO_GAP, CHARGE, SPIN,
               CALCULATION_SOFTWARE
        FROM DISPLAY_RECORDS
        WHERE DATASET_KEY = ?
        """ + filter + " ORDER BY ID LIMIT ? OFFSET ?";

    try (Connection connection = openConnection()) {
      long total = hasRecordFilters(filters)
          ? countRecords(connection, countSql, datasetId, filters)
          : displayRecordCount(datasetId);
      List<DatasetRecordSummaryResponse> records = new ArrayList<>();
      try (PreparedStatement statement = connection.prepareStatement(pageSql)) {
        int index = bindDatasetSearchAndFilters(statement, datasetId, filters);
        statement.setInt(index++, safeLimit);
        statement.setInt(index, safeOffset);
        try (ResultSet rs = statement.executeQuery()) {
          while (rs.next()) {
            records.add(summaryFrom(rs));
          }
        }
      }
      return new DatasetRecordsResponse(datasetId, total, safeOffset, safeLimit, records);
    } catch (SQLException ex) {
      throw databaseError(ex);
    }
  }

  public DatasetStatsResponse getDatasetStats(String datasetId) {
    requireDataset(datasetId);
    DatasetStatsResponse cached = datasetStatsCache.get(datasetId);
    if (cached != null) {
      return cached;
    }
    String sql = """
        SELECT COMPOSITION, ATOM_COUNT, ENERGY, HOMO_LUMO_GAP
        FROM DISPLAY_RECORDS
        WHERE DATASET_KEY = ?
        """;
    List<Double> atomCounts = new ArrayList<>();
    List<Double> energies = new ArrayList<>();
    List<Double> gaps = new ArrayList<>();
    Map<String, Long> elementCounts = new LinkedHashMap<>();
    long total = 0;

    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, datasetId);
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          total += 1;
          addNumber(atomCounts, value(rs, "ATOM_COUNT"));
          addNumber(energies, value(rs, "ENERGY"));
          addNumber(gaps, value(rs, "HOMO_LUMO_GAP"));
          addElementCounts(elementCounts, value(rs, "COMPOSITION"));
        }
      }
    } catch (SQLException ex) {
      throw databaseError(ex);
    }

    List<ElementCountResponse> elements = elementCounts.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
            .thenComparing(Map.Entry.comparingByKey()))
        .limit(24)
        .map(entry -> new ElementCountResponse(entry.getKey(), entry.getValue()))
        .toList();

    DatasetStatsResponse stats = new DatasetStatsResponse(
        datasetId,
        total,
        histogram(atomCounts, 10),
        histogram(energies, 10),
        histogram(gaps, 10),
        elements,
        List.of(
            new PropertyAvailabilityResponse("atomCount", "原子数", atomCounts.size()),
            new PropertyAvailabilityResponse("energy", "能量", energies.size()),
            new PropertyAvailabilityResponse("gap", "Band gap / HOMO-LUMO gap", gaps.size()),
            new PropertyAvailabilityResponse("composition", "元素组成", elements.isEmpty() ? 0 : total)
        )
    );
    datasetStatsCache.putIfAbsent(datasetId, stats);
    return datasetStatsCache.get(datasetId);
  }

  public QualityOverviewResponse getQualityOverview() {
    QualityOverviewResponse cached = qualityOverviewCache;
    if (cached != null) {
      return cached;
    }

    String sql = """
        SELECT DATASET_KEY,
               COUNT(*) AS TOTAL_RECORDS,
               SUM(CASE WHEN NULLIF(TRIM(SOURCE_RECORD_ID), '') IS NOT NULL THEN 1 ELSE 0 END) AS SOURCE_ID_FILLED,
               SUM(CASE WHEN NULLIF(TRIM(MATERIAL_ID), '') IS NOT NULL
                         OR NULLIF(TRIM(MATERIAL_NAME), '') IS NOT NULL THEN 1 ELSE 0 END) AS MATERIAL_FILLED,
               SUM(CASE WHEN NULLIF(TRIM(COMPOSITION), '') IS NOT NULL THEN 1 ELSE 0 END) AS COMPOSITION_FILLED,
               SUM(CASE WHEN NULLIF(TRIM(ATOM_COUNT), '') IS NOT NULL THEN 1 ELSE 0 END) AS ATOM_COUNT_FILLED,
               SUM(CASE WHEN NULLIF(TRIM(ENERGY), '') IS NOT NULL THEN 1 ELSE 0 END) AS ENERGY_FILLED,
               SUM(CASE WHEN NULLIF(TRIM(HOMO), '') IS NOT NULL
                         OR NULLIF(TRIM(LUMO), '') IS NOT NULL
                         OR NULLIF(TRIM(HOMO_LUMO_GAP), '') IS NOT NULL THEN 1 ELSE 0 END) AS ELECTRONIC_FILLED,
               SUM(CASE WHEN STRUCTURE_JSON IS NOT NULL AND LENGTH(STRUCTURE_JSON) > 2 THEN 1 ELSE 0 END) AS STRUCTURE_FILLED,
               SUM(CASE WHEN FORCES_JSON IS NOT NULL AND LENGTH(FORCES_JSON) > 2 THEN 1 ELSE 0 END) AS FORCE_FILLED,
               SUM(CASE WHEN PROPERTIES_JSON IS NOT NULL AND LENGTH(PROPERTIES_JSON) > 2 THEN 1 ELSE 0 END) AS PROPERTY_FILLED,
               SUM(CASE WHEN NULLIF(TRIM(DOI), '') IS NOT NULL THEN 1 ELSE 0 END) AS DOI_FILLED,
               SUM(CASE WHEN NULLIF(TRIM(CALCULATION_SOFTWARE), '') IS NOT NULL THEN 1 ELSE 0 END) AS SOFTWARE_FILLED,
               SUM(CASE WHEN WARNINGS_JSON IS NOT NULL
                         AND LENGTH(WARNINGS_JSON) > 2
                         AND TRIM(WARNINGS_JSON) <> '[]' THEN 1 ELSE 0 END) AS WARNING_COUNT,
               MIN(CAST(NULLIF(TRIM(ATOM_COUNT), '') AS INT)) AS MIN_ATOMS,
               MAX(CAST(NULLIF(TRIM(ATOM_COUNT), '') AS INT)) AS MAX_ATOMS,
               MIN(CAST(NULLIF(TRIM(ENERGY), '') AS DOUBLE)) AS MIN_ENERGY,
               MAX(CAST(NULLIF(TRIM(ENERGY), '') AS DOUBLE)) AS MAX_ENERGY
        FROM DISPLAY_RECORDS
        GROUP BY DATASET_KEY
        ORDER BY DATASET_KEY
        """;

    Map<String, DatasetCardResponse> cards = new LinkedHashMap<>();
    for (DatasetCardResponse card : listDatasets()) {
      cards.put(card.id(), card);
    }

    List<DatasetQualityResponse> datasets = new ArrayList<>();
    List<QualityIssueResponse> issues = new ArrayList<>();
    long totalRecords = 0;

    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(sql);
         ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        RawQualityRow row = rawQualityRow(rs);
        DatasetCardResponse card = cards.getOrDefault(
            row.datasetId(),
            cardFor(row.datasetId(), row.totalRecords(), row.minAtoms(), row.maxAtoms())
        );
        DatasetQualityResponse quality = buildDatasetQuality(row, card);
        datasets.add(quality);
        issues.addAll(issuesFor(quality));
        totalRecords += row.totalRecords();
      }
    } catch (SQLException ex) {
      throw databaseError(ex);
    }

    datasets.sort(Comparator.comparingInt(DatasetQualityResponse::score).reversed()
        .thenComparing(DatasetQualityResponse::datasetId));
    issues.sort(Comparator.comparingInt((QualityIssueResponse issue) -> severityRank(issue.severity()))
        .thenComparing(QualityIssueResponse::datasetId));

    int averageScore = datasets.isEmpty()
        ? 0
        : (int) Math.round(datasets.stream().mapToInt(DatasetQualityResponse::score).average().orElse(0));

    QualityOverviewResponse loaded = new QualityOverviewResponse(
        Instant.now().toString(),
        datasets.size(),
        totalRecords,
        averageScore,
        "当前质量验证基于统一 H2 展示库 display_records，已包含字段覆盖、结构 JSON 抽样、重复签名、单位/方法证据和适配器成熟度评估；原始 VASP/ORCA/HDF5/LMDB 全量科学认证仍需在后续流水线中补充。",
        qualityGates(datasets),
        List.copyOf(datasets),
        List.copyOf(issues),
        qualityAuditStages(),
        qualityAuditRules()
    );
    qualityOverviewCache = loaded;
    return loaded;
  }

  public List<QualityRunResponse> listQualityRuns() {
    QualityOverviewResponse overview = getQualityOverview();
    return List.of(qualityRunFor(overview));
  }

  public QualityRunResponse getQualityRun(String runId) {
    QualityRunResponse run = qualityRunFor(getQualityOverview());
    if (!run.runId().equals(runId) && !"latest".equalsIgnoreCase(runId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "未知质量运行记录: " + runId);
    }
    return run;
  }

  public DatasetQualityResponse getDatasetQualitySummary(String datasetId) {
    requireDataset(datasetId);
    return getQualityOverview().datasets().stream()
        .filter(item -> item.datasetId().equals(datasetId))
        .findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "找不到数据集质量摘要: " + datasetId));
  }

  public List<QualityIssueResponse> getDatasetQualityIssues(String datasetId) {
    requireDataset(datasetId);
    return getQualityOverview().issues().stream()
        .filter(item -> item.datasetId().equals(datasetId))
        .toList();
  }

  public List<QualityFieldDictionaryResponse> getDatasetFieldDictionary(String datasetId) {
    DatasetQualityResponse summary = getDatasetQualitySummary(datasetId);
    return summary.metrics().stream()
        .map(metric -> new QualityFieldDictionaryResponse(
            metric.key(),
            metric.label(),
            metric.filled(),
            metric.total(),
            metric.ratio(),
            metric.expected(),
            unitForQualityField(metric.key(), datasetId),
            fieldNote(metric, datasetId)
        ))
        .toList();
  }

  public String getQualityRunId() {
    return qualityRunFor(getQualityOverview()).runId();
  }

  public String buildDatasetQualityReportText(
      DatasetQualityResponse summary,
      List<QualityIssueResponse> issues,
      List<QualityFieldDictionaryResponse> fields
  ) {
    StringBuilder builder = new StringBuilder();
    builder.append("# ").append(summary.name()).append(" 质量报告\n\n");
    builder.append("- 数据集 ID：").append(summary.datasetId()).append('\n');
    builder.append("- 记录数：").append(summary.totalRecords()).append('\n');
    builder.append("- 综合评分：").append(summary.score()).append(" / 100（").append(summary.level()).append("）\n");
    builder.append("- 发布等级建议：").append(summary.publishTier()).append('\n');
    builder.append("- 审核状态：").append(summary.reviewStatus()).append('\n');
    builder.append("- 原子数范围：").append(summary.atomCountRange()).append('\n');
    builder.append("- 能量范围：").append(summary.energyRange()).append("\n\n");
    builder.append("## 字段覆盖\n");
    for (QualityFieldDictionaryResponse field : fields) {
      builder.append("- ")
          .append(field.label())
          .append("：")
          .append(Math.round(field.coverage() * 100))
          .append("%")
          .append(field.required() ? "，必需" : "，可选")
          .append(field.unit().isBlank() ? "" : "，单位：" + field.unit())
          .append('\n');
    }
    builder.append("\n## 问题台账\n");
    if (issues.isEmpty()) {
      builder.append("- 当前未发现明显阻断项。\n");
    } else {
      for (QualityIssueResponse issue : issues) {
        builder.append("- [")
            .append(issue.severity())
            .append("] ")
            .append(issue.title())
            .append("：")
            .append(issue.detail())
            .append(" 建议：")
            .append(issue.suggestion())
            .append('\n');
      }
    }
    builder.append("\n## 建议\n");
    for (String recommendation : summary.recommendations()) {
      builder.append("- ").append(recommendation).append('\n');
    }
    return builder.toString();
  }

  private QualityRunResponse qualityRunFor(QualityOverviewResponse overview) {
    String runId = "display-" + overview.generatedAt()
        .replace(":", "")
        .replace(".", "")
        .replace("Z", "z");
    return new QualityRunResponse(
        runId,
        "h2-display-records",
        overview.generatedAt(),
        "SUCCESS",
        overview.totalDatasets(),
        overview.totalRecords(),
        overview.averageScore(),
        "documents/quality_reports/" + runId + ".md"
    );
  }

  private String unitForQualityField(String key, String datasetId) {
    return switch (key) {
      case "energy" -> "Hartree / eV，按数据集来源解释";
      case "forces" -> "Hartree/Bohr 或 eV/A，需按来源确认";
      case "structure" -> "A";
      case "structureValidity" -> "sample pass ratio";
      case "duplicateSignature" -> "unique signature ratio";
      case "unitMethod" -> "evidence score";
      case "adapterReadiness" -> "readiness score";
      case "electronic", "targetLabel" -> targetUnitFor(datasetId);
      case "atomCount" -> "count";
      default -> "";
    };
  }

  private String targetUnitFor(String datasetId) {
    if (datasetId != null && datasetId.startsWith("matbench_v01_log_")) {
      return "log10(GPa)";
    }
    if (MATBENCH_V01_DIELECTRIC_DATASET_ID.equals(datasetId)) {
      return "dimensionless";
    }
    if (MATBENCH_V01_JDFT2D_DATASET_ID.equals(datasetId)) {
      return "meV/atom";
    }
    if (MATBENCH_V01_PHONONS_DATASET_ID.equals(datasetId)) {
      return "cm-1";
    }
    if (POLYMER_GENOME_DATASET_ID.equals(datasetId)) {
      return "eV / polymer property units";
    }
    return "按数据集 extraProperties 或论文说明";
  }

  private String fieldNote(QualityCoverageMetricResponse metric, String datasetId) {
    if ("structureValidity".equals(metric.key())) {
      return "基于展示库结构 JSON 的抽样合法性检查；正式认证仍需 ASE/pymatgen 全量校验。";
    }
    if ("duplicateSignature".equals(metric.key())) {
      return "基于 source/material/composition/atom_count/energy 的疑似唯一签名，不能替代结构哈希去重。";
    }
    if ("unitMethod".equals(metric.key())) {
      return "评估单位、软件、理论层级和引用证据是否足以支撑可复现计算。";
    }
    if ("adapterReadiness".equals(metric.key())) {
      return "评估该数据源进入自动下载、解析、构建 H2 展示库的适配器成熟度。";
    }
    if (!metric.expected()) {
      return "该字段对当前数据类型不是强制项。";
    }
    if (metric.ratio() >= 0.95) {
      return "覆盖率较高，可作为检索和展示字段。";
    }
    if ("structure".equals(metric.key()) && OPENPOLY_DATASET_ID.equals(datasetId)) {
      return "OpenPoly 当前为表格性质数据，无三维坐标时应明确标注。";
    }
    return "覆盖不足，需进入质量问题台账或在详情页标注缺失。";
  }

  public DatasetRecordDetailResponse getRecord(String datasetId, long recordId) {
    requireDataset(datasetId);
    String sql = "SELECT * FROM DISPLAY_RECORDS WHERE DATASET_KEY = ? AND ID = ?";
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, datasetId);
      statement.setLong(2, recordId);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          throw new ApiException(HttpStatus.NOT_FOUND, "找不到数据记录: " + recordId);
        }
        return detailFrom(rs);
      }
    } catch (SQLException ex) {
      throw databaseError(ex);
    }
  }

  public String getRecordCsv(String datasetId, long recordId) {
    DatasetRecordDetailResponse record = getRecord(datasetId, recordId);
    StringBuilder csv = new StringBuilder("index,element,x,y,z\n");
    for (AtomCoordinateResponse atom : record.atoms()) {
      csv.append(atom.index()).append(',')
          .append(atom.element()).append(',')
          .append(format(atom.x())).append(',')
          .append(format(atom.y())).append(',')
          .append(format(atom.z())).append('\n');
    }
    return csv.toString();
  }

  public void writeDatasetCsv(String datasetId, Writer writer) throws IOException {
    requireDataset(datasetId);
    String sql = "SELECT * FROM DISPLAY_RECORDS WHERE DATASET_KEY = ? ORDER BY ID";
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, datasetId);
      try (ResultSet rs = statement.executeQuery()) {
        ResultSetMetaData metadata = rs.getMetaData();
        int columns = metadata.getColumnCount();
        for (int column = 1; column <= columns; column += 1) {
          if (column > 1) {
            writer.write(',');
          }
          writer.write(csvValue(metadata.getColumnLabel(column)));
        }
        writer.write('\n');
        while (rs.next()) {
          for (int column = 1; column <= columns; column += 1) {
            if (column > 1) {
              writer.write(',');
            }
            writer.write(csvValue(rs.getString(column)));
          }
          writer.write('\n');
        }
        writer.flush();
      }
    } catch (SQLException ex) {
      throw databaseError(ex);
    }
  }

  public int ingestRecords(String datasetKey, String datasetName, List<Map<String, String>> rows) {
    if (!properties.isDisplayWritable()) {
      throw new ApiException(HttpStatus.CONFLICT,
          "展示库当前为只读，请设置 vasp.datasets.display-writable=true 并重启后端后再入库");
    }
    if (datasetKey == null || datasetKey.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "缺少数据集标识");
    }
    if (rows == null || rows.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "没有可入库的记录");
    }
    try (Connection connection = openConnection()) {
      List<String> columns = new ArrayList<>();
      try (PreparedStatement probe = connection.prepareStatement("SELECT * FROM DISPLAY_RECORDS WHERE 1=0");
           ResultSet rs = probe.executeQuery()) {
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i += 1) {
          columns.add(md.getColumnLabel(i).toUpperCase(Locale.ROOT));
        }
      }
      long maxId;
      try (PreparedStatement ps = connection.prepareStatement("SELECT COALESCE(MAX(ID), 0) AS M FROM DISPLAY_RECORDS");
           ResultSet rs = ps.executeQuery()) {
        rs.next();
        maxId = rs.getLong("M");
      }
      String columnList = String.join(", ", columns);
      String placeholders = String.join(", ", columns.stream().map(c -> "?").toList());
      String sql = "INSERT INTO DISPLAY_RECORDS (" + columnList + ") VALUES (" + placeholders + ")";
      int inserted = 0;
      boolean previousAutoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try (PreparedStatement insert = connection.prepareStatement(sql)) {
        // Idempotent re-ingest: replace any existing rows for this intake dataset so a
        // second ingest updates in place instead of appending duplicates.
        if (datasetKey.startsWith("intake_")) {
          try (PreparedStatement delete = connection.prepareStatement(
              "DELETE FROM DISPLAY_RECORDS WHERE DATASET_KEY = ?")) {
            delete.setString(1, datasetKey);
            delete.executeUpdate();
          }
        }
        long id = maxId;
        for (Map<String, String> row : rows) {
          id += 1;
          for (int i = 0; i < columns.size(); i += 1) {
            String column = columns.get(i);
            if ("ID".equals(column)) {
              insert.setLong(i + 1, id);
            } else if ("DATASET_KEY".equals(column)) {
              insert.setString(i + 1, datasetKey);
            } else if ("DATASET_NAME".equals(column)) {
              insert.setString(i + 1, row.getOrDefault("DATASET_NAME", datasetName == null ? "" : datasetName));
            } else {
              insert.setString(i + 1, row.getOrDefault(column, ""));
            }
          }
          insert.addBatch();
          inserted += 1;
        }
        insert.executeBatch();
        connection.commit();
      } catch (SQLException ex) {
        connection.rollback();
        throw ex;
      } finally {
        connection.setAutoCommit(previousAutoCommit);
      }
      invalidateCaches();
      return inserted;
    } catch (SQLException ex) {
      throw databaseError(ex);
    }
  }

  /**
   * Count published display records whose DOI matches the given DOI. Used by the
   * discovery validator to flag candidates that are already ingested. Read-only and
   * returns 0 on any error so auto-validation degrades gracefully.
   */
  public int countByDoi(String doi) {
    if (doi == null || doi.isBlank()) {
      return 0;
    }
    return displayDoiSet().contains(normalizeDoiForMatch(doi)) ? 1 : 0;
  }

  /**
   * Cached set of normalized DOIs already present in the display DB. Built once and
   * reused (invalidated on ingest) so the discovery validator's duplicate check is an
   * in-memory lookup instead of a full-table scan per candidate.
   */
  private Set<String> displayDoiSet() {
    Set<String> cached = displayDoiCache;
    if (cached != null) {
      return cached;
    }
    synchronized (this) {
      if (displayDoiCache == null) {
        Set<String> set = new HashSet<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT DOI FROM DISPLAY_RECORDS WHERE NULLIF(TRIM(DOI), '') IS NOT NULL");
             ResultSet rs = statement.executeQuery()) {
          while (rs.next()) {
            String normalized = normalizeDoiForMatch(rs.getString("DOI"));
            if (!normalized.isBlank()) {
              set.add(normalized);
            }
          }
        } catch (SQLException ignored) {
          // Best-effort; an empty set just means "no known duplicates".
        }
        displayDoiCache = set;
      }
      return displayDoiCache;
    }
  }

  private static String normalizeDoiForMatch(String value) {
    if (value == null) {
      return "";
    }
    String v = value.trim().toLowerCase(Locale.ROOT);
    if (v.startsWith("https://doi.org/")) {
      v = v.substring("https://doi.org/".length());
    } else if (v.startsWith("http://doi.org/")) {
      v = v.substring("http://doi.org/".length());
    } else if (v.startsWith("doi:")) {
      v = v.substring("doi:".length());
    }
    while (v.endsWith("/")) {
      v = v.substring(0, v.length() - 1);
    }
    return v;
  }

  private void invalidateCaches() {
    displayDoiCache = null;
    datasetCardsCache = null;
    datasetCatalogCache = null;
    qualityOverviewCache = null;
    datasetDetailCache.clear();
    datasetStatsCache.clear();
    displayRecordCountCache.clear();
  }

  private long countRecords(Connection connection, String sql, String datasetId, RecordFilters filters) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      bindDatasetSearchAndFilters(statement, datasetId, filters);
      try (ResultSet rs = statement.executeQuery()) {
        rs.next();
        return rs.getLong("N");
      }
    }
  }

  private long displayRecordCount(String datasetId) {
    if (displayRecordCountCache.isEmpty()) {
      listDatasets();
    }
    return displayRecordCountCache.getOrDefault(datasetId, 0L);
  }

  private boolean hasRecordFilters(RecordFilters filters) {
    return !filters.search().isBlank()
        || filters.energyMin() != null
        || filters.energyMax() != null
        || filters.atomMin() != null
        || filters.atomMax() != null;
  }

  private String buildRecordFilter(RecordFilters filters) {
    StringBuilder filter = new StringBuilder();
    if (!filters.search().isBlank()) {
      filter.append("""
          AND (
            LOWER(COALESCE(SOURCE_RECORD_ID, '')) LIKE ?
            OR LOWER(COALESCE(MATERIAL_ID, '')) LIKE ?
            OR LOWER(COALESCE(MATERIAL_NAME, '')) LIKE ?
            OR LOWER(COALESCE(SMILES, '')) LIKE ?
            OR LOWER(COALESCE(COMPOSITION, '')) LIKE ?
          )
          """);
    }
    if (filters.energyMin() != null) {
      filter.append(" AND CAST(NULLIF(ENERGY, '') AS DOUBLE) >= ? ");
    }
    if (filters.energyMax() != null) {
      filter.append(" AND CAST(NULLIF(ENERGY, '') AS DOUBLE) <= ? ");
    }
    if (filters.atomMin() != null) {
      filter.append(" AND CAST(NULLIF(ATOM_COUNT, '') AS INT) >= ? ");
    }
    if (filters.atomMax() != null) {
      filter.append(" AND CAST(NULLIF(ATOM_COUNT, '') AS INT) <= ? ");
    }
    return filter.toString();
  }

  private int bindDatasetSearchAndFilters(
      PreparedStatement statement,
      String datasetId,
      RecordFilters filters
  ) throws SQLException {
    int index = 1;
    statement.setString(index++, datasetId);
    if (!filters.search().isBlank()) {
      String pattern = "%" + filters.search() + "%";
      for (int i = 0; i < 5; i += 1) {
        statement.setString(index++, pattern);
      }
    }
    if (filters.energyMin() != null) {
      statement.setDouble(index++, filters.energyMin());
    }
    if (filters.energyMax() != null) {
      statement.setDouble(index++, filters.energyMax());
    }
    if (filters.atomMin() != null) {
      statement.setInt(index++, filters.atomMin());
    }
    if (filters.atomMax() != null) {
      statement.setInt(index++, filters.atomMax());
    }
    return index;
  }

  private record IntakeCardMeta(String name, String description, String software, List<String> elements) {
  }

  /** Read real metadata for an intake_ dataset (name/description/software/elements) from its records. */
  private IntakeCardMeta intakeCardMeta(String datasetId) {
    String name = "";
    String description = "";
    String software = "";
    java.util.LinkedHashSet<String> elements = new java.util.LinkedHashSet<>();
    try (Connection connection = openConnection()) {
      try (PreparedStatement ps = connection.prepareStatement(
          "SELECT DATASET_NAME, DATASET_DESCRIPTION FROM DISPLAY_RECORDS WHERE DATASET_KEY = ? FETCH FIRST 1 ROW ONLY")) {
        ps.setString(1, datasetId);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            name = nz(rs.getString("DATASET_NAME"));
            description = nz(rs.getString("DATASET_DESCRIPTION"));
          }
        }
      }
      try (PreparedStatement ps = connection.prepareStatement(
          "SELECT CALCULATION_SOFTWARE, COMPOSITION FROM DISPLAY_RECORDS WHERE DATASET_KEY = ? FETCH FIRST 200 ROWS ONLY")) {
        ps.setString(1, datasetId);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            if (software.isBlank()) {
              String s = nz(rs.getString("CALCULATION_SOFTWARE"));
              if (!s.isBlank()) {
                software = s;
              }
            }
            parseElements(rs.getString("COMPOSITION"), elements);
          }
        }
      }
    } catch (SQLException ignored) {
      // Card metadata is best-effort; fall back to generic labels.
    }
    return new IntakeCardMeta(name, description, software, new ArrayList<>(elements));
  }

  private static String nz(String value) {
    return value == null ? "" : value.trim();
  }

  private static void parseElements(String composition, java.util.Set<String> into) {
    if (composition == null || composition.isBlank() || into.size() >= 12) {
      return;
    }
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Z][a-z]?)").matcher(composition);
    while (m.find() && into.size() < 12) {
      into.add(m.group(1));
    }
  }

  /** Remove every record of an intake_ dataset from the display DB (withdraw an ingest). */
  public int deleteDataset(String datasetKey) {
    if (!properties.isDisplayWritable()) {
      throw new ApiException(HttpStatus.CONFLICT,
          "展示库当前为只读，请设置 vasp.datasets.display-writable=true 并重启后端后再操作");
    }
    if (datasetKey == null || !datasetKey.startsWith("intake_")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "仅允许撤回自动入库（intake_）数据集");
    }
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(
             "DELETE FROM DISPLAY_RECORDS WHERE DATASET_KEY = ?")) {
      statement.setString(1, datasetKey);
      int deleted = statement.executeUpdate();
      invalidateCaches();
      return deleted;
    } catch (SQLException ex) {
      throw databaseError(ex);
    }
  }

  private DatasetCardResponse cardFor(String datasetId, long count, int minAtoms, int maxAtoms) {
    if (datasetId != null && datasetId.startsWith("intake_")) {
      IntakeCardMeta meta = intakeCardMeta(datasetId);
      String name = meta.name().isBlank()
          ? "自动接入数据集 " + datasetId.replaceFirst("^intake_", "#")
          : meta.name();
      String intro = meta.description().isBlank()
          ? "由“自动发现 → 解析适配 → 半自动入库”写入展示库的数据集；字段按人工确认的映射写入，单位、完整性与科学元数据仍需人工核对后再正式发布。"
          : meta.description();
      String method = meta.software().isBlank() ? "半自动入库（待人工核对）" : meta.software();
      return new DatasetCardResponse(
          datasetId,
          name,
          method,
          count + " 条记录",
          intro,
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          meta.elements(),
          "待确认",
          "待确认"
      );
    }
    if (ANI_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "ANI-GDB s03",
          "DFT 总能量标签",
          count + " 条构象 / 20 个分子组",
          "面向小分子机器学习势函数训练的有机分子构象数据，覆盖 C/H/N/O 体系的非平衡结构、SMILES、原子序列、三维坐标和 DFT 总能量标签，可用于学习分子构象空间中的能量曲面。计算软件为 Gaussian 09；DOI: 10.1038/sdata.2017.193。",
          true,
          20,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("C", "H", "N", "O"),
          "ωB97X",
          "6-31G(d)"
      );
    }
    if (OPENPOLY_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "OpenPoly calculated polymers",
          "DFT 总能量、电子结构与热力学校正标签",
          count + " 条聚合物量化计算记录",
          "面向聚合物多性质预测与基准测试的量化计算数据，收录来自 OMG 聚合物空间的代表性结构，包含 PSMILES/SMILES、聚合反应类型、DFT 总能量、HOMO/LUMO、gap、偶极矩、极化率和热力学校正量。计算软件为 ORCA；DOI: 10.1007/s10118-025-3402-y。",
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("C", "H", "N", "O", "F", "P", "S", "Cl", "Br"),
          "B3LYP; ωB97M-V",
          "def2-TZVP; ma-def2-TZVP"
      );
    }
    if (ANI1X_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "ANI-1x / Less is more",
          "主动学习 DFT 能量与力数据",
          "4,956,005 条构象 / 3,114 个化学式 group",
          "面向通用有机分子势函数训练的主动学习采样数据，覆盖 H/C/N/O 化学空间中的多样构象，并提供 ωB97X/6-31G(d) 能量、力以及部分高层级能量标签，适合用于构建和验证可迁移的分子机器学习势。论文 DOI: 10.1063/1.5023802。",
          true,
          3114,
          4956005,
          "2-63",
          List.of("H", "C", "N", "O"),
          "ωB97X",
          "6-31G(d)"
      );
    }
    if (TRANSITION1X_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "Transition1x",
          "NEB 反应路径 DFT 能量与力数据",
          "9,624,594 条构象 / 10,073 个收敛反应",
          "Transition1x 面向可泛化反应型机器学习势函数，包含有机反应路径及其附近的 DFT 能量和力。数据由 NEB/CINEB 在 H/C/N/O 反应体系上生成，HDF5 按 data/train/val/test、化学式和 reaction group 组织。论文 DOI: 10.1038/s41597-022-01870-w；数据 DOI: 10.6084/m9.figshare.19614657.v4。",
          true,
          10073,
          9624594,
          "4-23",
          List.of("H", "C", "N", "O"),
          "ωB97X",
          "6-31G(d)"
      );
    }
    if (TWOD_MATPEDIA_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "2DMatPedia",
          "二维材料 VASP 高通量 DFT 数据",
          count + " 个二维材料结构",
          "2DMatPedia 是面向二维材料发现与筛选的开放计算数据库，包含从 Materials Project 层状体理论剥离和同族元素替换生成的二维材料结构、电子性质和稳定性相关能量。论文 DOI: 10.1038/s41597-019-0097-3。",
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("H", "B", "C", "N", "O", "F", "S", "Cl", "Br", "I", "Bi"),
          "vdW-optB88; PBE",
          "PAW plane-wave, 520 eV"
      );
    }
    if (JARVIS_3D_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "JARVIS-DFT 3D Bulk Materials",
          "VASP 晶体材料 DFT 性质数据",
          count + " 条三维晶体材料记录",
          "NIST JARVIS-DFT 三维周期晶体材料数据集，覆盖无机晶体结构及 DFT 计算性质，可用于展示结构、形成能、带隙、弹性、介电、磁性、能带和 DOS 等信息。论文 DOI: 10.1038/s41524-020-00440-1。",
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("H", "B", "C", "N", "O", "F", "Si", "P", "S", "Cl", "Ti", "Cu", "As"),
          "vdW-DF-OptB88; TBmBJ/HSE06/PBE0 等部分属性",
          "PAW plane-wave"
      );
    }
    if (JARVIS_2D_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "JARVIS-DFT 2D Materials",
          "二维材料 VASP DFT 性质数据",
          count + " 条二维材料记录",
          "JARVIS-DFT 二维材料数据集，适合展示单层材料结构、形成能、带隙、剥离相关性质、磁性和电子结构性质，主要由 OptB88vdW 方法计算。论文 DOI: 10.1038/s41524-020-00440-1。",
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("H", "B", "C", "N", "O", "F", "S", "Cl", "Co"),
          "vdW-DF-OptB88",
          "PAW plane-wave"
      );
    }
    if (POLYMER_GENOME_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "Polymer Genome 1073",
          "聚合物第一性原理结构与介电性质数据",
          count + " 个聚合物 CIF 结构",
          "Polymer Genome 1073 是面向聚合物材料性质预测与设计的第一性原理数据集，提供优化平衡结构、原子化能、带隙和介电常数等性质。论文 DOI: 10.1038/sdata.2016.12；数据 DOI: 10.5061/dryad.5ht3n。",
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("C", "H", "N", "O", "F", "S", "Cl", "Cd"),
          "vdW-DF2",
          "PAW plane-wave, 400 eV"
      );
    }
    if (QMOF_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "QMOF Database",
          "MOF 周期 DFT 结构与电子性质数据",
          count + " 个 MOF / 配位聚合物结构",
          "QMOF Database 是面向金属有机框架材料和配位聚合物的量子化学性质数据库，包含周期 DFT 优化结构及电子性质标签，基础计算层级为 PBE-D3(BJ)，并扩展 HLE17/HSE06 等结果。论文 DOI: 10.1016/j.matt.2021.02.015；10.1038/s41524-022-00796-6。",
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("C", "H", "N", "O", "S", "Cl", "Cu", "Zn", "Cd", "Ag"),
          "PBE-D3(BJ); HLE17/HSE06",
          "PAW PBE plane-wave, 520 eV"
      );
    }
    if (MATBENCH_WBM_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "Matbench Discovery WBM Summary",
          "晶体稳定性机器学习基准标签",
          count + " 条 WBM 候选材料记录",
          "Matbench Discovery 的 WBM 摘要数据面向无机晶体稳定性筛选，提供 DFT 形成能、凸包能量、PBE 带隙、原型结构和对称性等字段，适合用于材料发现模型和稳定性基准评估。",
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("Ac", "Ag", "Al", "As", "Ba", "C", "Ca", "Cl", "Cu", "F", "Fe", "H", "Li", "Mg", "N", "Na", "O", "P", "S", "Si", "Ti", "U", "Zn"),
          "PBE / MP compatibility",
          "PAW plane-wave"
      );
    }
    if (MATBENCH_MP_ENERGIES_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "Materials Project Reference Energies",
          "Materials Project 形成能与凸包参考表",
          count + " 条 MP 参考能量记录",
          "Matbench Discovery 使用的 Materials Project 参考能量表，包含每原子能量、形成能、凸包能量、分解焓、能量类型、空间群和原型结构等字段，可作为晶体稳定性与能量预测任务的参考数据。",
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("Ag", "Al", "Ar", "As", "Ba", "C", "Ca", "Cl", "Cu", "F", "Fe", "H", "Li", "Mg", "N", "Na", "Ne", "O", "P", "S", "Si", "Ti", "Zn"),
          "GGA / GGA+U / r2SCAN",
          "PAW plane-wave"
      );
    }
    if (MATBENCH_PHONONDB_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "PhononDB PBE 103 Thermal Conductivity",
          "声子与热输运基准子集",
          count + " 条带热导率标签的晶体结构",
          "Matbench Discovery 收录的 PhononDB PBE 103 子集，提供晶体结构、声子相关元数据和晶格热导率标签，适合展示热输运性质、结构-性质关联和小规模热导率预测示例。",
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("Ag", "Al", "Ba", "C", "Ca", "Cl", "Cu", "F", "Ga", "Ge", "Mg", "N", "O", "Pb", "S", "Si", "Sr", "Te", "Zn"),
          "PBE",
          "PAW plane-wave"
      );
    }
    if (MATBENCH_V01_DIELECTRIC_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "Matbench v0.1 Dielectric",
          "Materials Project 介电性质预测基准",
          count + " 条晶体结构-折射率记录",
          "Matbench v0.1 的介电性质预测任务，来自 Materials Project 计算数据，提供晶体结构和折射率 n 标签，适合展示结构-介电性质预测和小规模材料机器学习基准。",
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("Al", "Ba", "Ca", "Cl", "F", "Ga", "Hf", "K", "Mg", "Na", "O", "S", "Si", "Sr", "Ti", "Zr"),
          "PBE / Materials Project workflow",
          "PAW plane-wave"
      );
    }
    if (MATBENCH_V01_JDFT2D_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "Matbench v0.1 JDFT2D",
          "二维材料剥离能预测基准",
          count + " 条二维材料结构-剥离能记录",
          "Matbench v0.1 的 JDFT2D 任务，面向二维材料剥离能预测，提供晶体结构和 exfoliation energy 标签，可与当前 JARVIS-DFT 2D 数据互补。",
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("B", "C", "Cl", "F", "Hf", "N", "O", "S", "Se", "Si", "Te"),
          "OptB88vdW / JARVIS workflow",
          "PAW plane-wave"
      );
    }
    if (MATBENCH_V01_PHONONS_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "Matbench v0.1 Phonons",
          "声子 DOS 峰位预测基准",
          count + " 条晶体结构-声子标签记录",
          "Matbench v0.1 的声子性质任务，提供晶体结构和 phonon DOS 最后峰位标签，适合展示声子/振动性质预测。",
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("Ag", "Al", "Ba", "Ca", "Cl", "Cu", "F", "Ge", "Mg", "O", "S", "Sr", "Te", "Zn"),
          "PBE / Materials Project workflow",
          "PAW plane-wave"
      );
    }
    if (MATBENCH_V01_PEROVSKITES_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "Matbench v0.1 Perovskites",
          "钙钛矿 DFT 形成能预测基准",
          count + " 条 ABX3 结构-形成能记录",
          "Matbench v0.1 的钙钛矿形成能任务，提供 ABX3 类晶体结构和 DFT 形成能标签，适合用于钙钛矿筛选、形成能回归和结构-稳定性关系展示。",
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("B", "Ba", "Ca", "Cl", "F", "K", "N", "O", "Pb", "Rh", "Sr", "Te", "Ti"),
          "PBE / Materials Project workflow",
          "PAW plane-wave"
      );
    }
    if (MATBENCH_V01_LOG_GVRH_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "Matbench v0.1 log GVRH",
          "DFT 弹性剪切模量预测基准",
          count + " 条晶体结构-剪切模量记录",
          "Matbench v0.1 的弹性性质任务，提供晶体结构和 Voigt-Reuss-Hill 剪切模量 log10(G_VRH) 标签，适合展示力学性质机器学习基准。",
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("Ag", "Al", "Ca", "Cl", "Cu", "Fe", "Ge", "Mg", "O", "Si", "Ti", "Zn"),
          "PBE / Materials Project workflow",
          "PAW plane-wave"
      );
    }
    if (MATBENCH_V01_LOG_KVRH_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "Matbench v0.1 log KVRH",
          "DFT 弹性体积模量预测基准",
          count + " 条晶体结构-体积模量记录",
          "Matbench v0.1 的弹性性质任务，提供晶体结构和 Voigt-Reuss-Hill 体积模量 log10(K_VRH) 标签，适合展示力学性质机器学习基准。",
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("Ag", "Al", "Ca", "Cl", "Cu", "Fe", "Ge", "Mg", "O", "Si", "Ti", "Zn"),
          "PBE / Materials Project workflow",
          "PAW plane-wave"
      );
    }
    if (QM9_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "QM9 Molecular DFT Properties",
          "小分子 DFT 电子与热力学性质基准",
          count + " 条小分子 SMILES-性质记录",
          "QM9 是小分子量子化学性质基准，包含约 134k 个 GDB-9 有机小分子的 DFT 电子、热力学和几何相关性质。本项目接入 DeepChem 表格版，展示 SMILES、HOMO/LUMO、gap、偶极矩、极化率和热力学能量；该 CSV 版本不包含三维坐标。",
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("C", "H", "N", "O", "F"),
          "B3LYP",
          "6-31G(2df,p)"
      );
    }
    if (HYDROCARBONS_GAP_DATASET_ID.equals(datasetId)) {
      return new DatasetCardResponse(
          datasetId,
          "Hydrocarbons CH GAP Training Set",
          "C/H 反应体系 DFT 能量-力训练数据",
          count + " 条 extxyz 构型",
          "面向碳氢化合物和氢化碳材料反应型机器学习势的 DFT 标注构型数据，包含原子坐标、能量、力、应力和 config_type 构型类别，可用于 GAP 势函数训练与快速数据质量验证。",
          true,
          (int) count,
          count,
          minAtoms + "-" + maxAtoms,
          List.of("C", "H"),
          "DFT labels; GAP potential",
          "extxyz / QUIP-GAP training format"
      );
    }
    return new DatasetCardResponse(
        datasetId,
        "ASE-LMDB polymers",
        "DFT 能量、力与电子结构标签",
        count + " 条聚合物计算记录",
        "OPoly26 验证集风格的聚合物量子化学计算数据集，提供 DFT 能量、力和电子结构相关属性等标签。计算软件为 ORCA 6.0.0；本地记录包含聚合物组成、原子坐标、总能量、力、HOMO/LUMO、HOMO-LUMO gap、电荷和 ORCA 计算元数据。arXiv: 2512.23117。",
        true,
        (int) count,
        count,
        minAtoms + "-" + maxAtoms,
        List.of("H", "Li", "C", "N", "O", "F", "Na", "Mg", "P", "S", "Cl", "Ca", "Ni", "Br", "I"),
        "ωB97M-V",
        "def2-TZVPD"
    );
  }

  private DatasetCatalogResponse catalogFor(DatasetCardResponse card) {
    String datasetId = card.id();
    return new DatasetCatalogResponse(
        datasetId,
        card.name(),
        typeFor(datasetId),
        card.intro(),
        card.scale(),
        card.moleculeGroups(),
        card.totalConformers(),
        parseMinAtoms(card.atomCountRange()),
        parseMaxAtoms(card.atomCountRange()),
        card.elements(),
        methodsFor(datasetId),
        splitMetadata(card.functional()),
        splitMetadata(card.basisSet()),
        softwareFor(datasetId),
        propertiesFor(datasetId),
        representationFor(datasetId),
        linksFor(datasetId)
    );
  }

  private String typeFor(String datasetId) {
    if (ANI_DATASET_ID.equals(datasetId) || ANI1X_DATASET_ID.equals(datasetId) || QM9_DATASET_ID.equals(datasetId)) {
      return "小分子构象";
    }
    if (HYDROCARBONS_GAP_DATASET_ID.equals(datasetId)) {
      return "碳氢分子/材料";
    }
    if (LMDB_DATASET_ID.equals(datasetId)
        || OPENPOLY_DATASET_ID.equals(datasetId)
        || POLYMER_GENOME_DATASET_ID.equals(datasetId)) {
      return "聚合物";
    }
    if (TRANSITION1X_DATASET_ID.equals(datasetId)) {
      return "反应路径";
    }
    if (TWOD_MATPEDIA_DATASET_ID.equals(datasetId)
        || JARVIS_2D_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_JDFT2D_DATASET_ID.equals(datasetId)) {
      return "二维材料";
    }
    if (JARVIS_3D_DATASET_ID.equals(datasetId)
        || MATBENCH_WBM_DATASET_ID.equals(datasetId)
        || MATBENCH_MP_ENERGIES_DATASET_ID.equals(datasetId)
        || MATBENCH_PHONONDB_DATASET_ID.equals(datasetId)
        || isMatbenchV01CrystalDataset(datasetId)) {
      return "晶体材料";
    }
    return "MOF";
  }

  private boolean isMatbenchV01CrystalDataset(String datasetId) {
    return MATBENCH_V01_DIELECTRIC_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_PHONONS_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_PEROVSKITES_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_LOG_GVRH_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_LOG_KVRH_DATASET_ID.equals(datasetId);
  }

  private List<String> methodsFor(String datasetId) {
    if (MATBENCH_WBM_DATASET_ID.equals(datasetId)
        || MATBENCH_MP_ENERGIES_DATASET_ID.equals(datasetId)) {
      return List.of("DFT", "高通量筛选", "ML benchmark");
    }
    if (MATBENCH_PHONONDB_DATASET_ID.equals(datasetId)) {
      return List.of("DFT", "声子", "热导率");
    }
    if (MATBENCH_V01_DIELECTRIC_DATASET_ID.equals(datasetId)) {
      return List.of("DFT", "ML benchmark", "介电性质");
    }
    if (MATBENCH_V01_JDFT2D_DATASET_ID.equals(datasetId)) {
      return List.of("DFT", "ML benchmark", "二维材料剥离能");
    }
    if (MATBENCH_V01_PHONONS_DATASET_ID.equals(datasetId)) {
      return List.of("DFT", "ML benchmark", "声子");
    }
    if (MATBENCH_V01_PEROVSKITES_DATASET_ID.equals(datasetId)) {
      return List.of("DFT", "ML benchmark", "形成能");
    }
    if (MATBENCH_V01_LOG_GVRH_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_LOG_KVRH_DATASET_ID.equals(datasetId)) {
      return List.of("DFT", "ML benchmark", "弹性性质");
    }
    if (QM9_DATASET_ID.equals(datasetId)) {
      return List.of("DFT", "分子量化计算", "ML benchmark");
    }
    if (HYDROCARBONS_GAP_DATASET_ID.equals(datasetId)) {
      return List.of("DFT", "机器学习势", "GAP");
    }
    if (ANI1X_DATASET_ID.equals(datasetId)) {
      return List.of("DFT", "主动学习");
    }
    if (TRANSITION1X_DATASET_ID.equals(datasetId)) {
      return List.of("DFT", "NEB/CINEB");
    }
    if (TWOD_MATPEDIA_DATASET_ID.equals(datasetId)
        || JARVIS_3D_DATASET_ID.equals(datasetId)
        || JARVIS_2D_DATASET_ID.equals(datasetId)) {
      return List.of("DFT", "高通量筛选");
    }
    if (ANI_DATASET_ID.equals(datasetId)
        || LMDB_DATASET_ID.equals(datasetId)
        || OPENPOLY_DATASET_ID.equals(datasetId)) {
      return List.of("DFT", "分子量化计算");
    }
    return List.of("DFT", "周期材料计算");
  }

  private List<String> softwareFor(String datasetId) {
    if (ANI_DATASET_ID.equals(datasetId)) {
      return List.of("Gaussian 09");
    }
    if (LMDB_DATASET_ID.equals(datasetId)) {
      return List.of("ORCA 6.0.0");
    }
    if (OPENPOLY_DATASET_ID.equals(datasetId)) {
      return List.of("ORCA");
    }
    if (TRANSITION1X_DATASET_ID.equals(datasetId)) {
      return List.of("ORCA 5.0.2", "ASE 3.22.1");
    }
    if (TWOD_MATPEDIA_DATASET_ID.equals(datasetId)
        || JARVIS_3D_DATASET_ID.equals(datasetId)
        || JARVIS_2D_DATASET_ID.equals(datasetId)
        || POLYMER_GENOME_DATASET_ID.equals(datasetId)
        || QMOF_DATASET_ID.equals(datasetId)
        || MATBENCH_WBM_DATASET_ID.equals(datasetId)
        || MATBENCH_MP_ENERGIES_DATASET_ID.equals(datasetId)
        || MATBENCH_PHONONDB_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_DIELECTRIC_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_JDFT2D_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_PHONONS_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_PEROVSKITES_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_LOG_GVRH_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_LOG_KVRH_DATASET_ID.equals(datasetId)) {
      return List.of("VASP");
    }
    if (QM9_DATASET_ID.equals(datasetId)) {
      return List.of("Gaussian 09");
    }
    if (HYDROCARBONS_GAP_DATASET_ID.equals(datasetId)) {
      return List.of("DFT", "QUIP-GAP");
    }
    return List.of();
  }

  private List<String> propertiesFor(String datasetId) {
    if (ANI_DATASET_ID.equals(datasetId)) {
      return List.of("三维坐标", "能量", "总能量", "SMILES");
    }
    if (LMDB_DATASET_ID.equals(datasetId)) {
      return List.of("三维坐标", "能量", "总能量", "力", "HOMO/LUMO", "HOMO-LUMO gap", "电荷");
    }
    if (OPENPOLY_DATASET_ID.equals(datasetId)) {
      return List.of("能量", "总能量", "HOMO/LUMO", "HOMO-LUMO gap", "偶极矩", "极化率", "热力学校正", "SMILES");
    }
    if (ANI1X_DATASET_ID.equals(datasetId)) {
      return List.of("三维坐标", "能量", "总能量", "力", "偶极矩", "CCSD(T) 能量");
    }
    if (TRANSITION1X_DATASET_ID.equals(datasetId)) {
      return List.of("三维坐标", "能量", "总能量", "力", "反应路径");
    }
    if (TWOD_MATPEDIA_DATASET_ID.equals(datasetId)) {
      return List.of("三维坐标", "能量", "每原子能量", "Band gap", "剥离能", "分解能", "晶胞/空间群");
    }
    if (JARVIS_3D_DATASET_ID.equals(datasetId)) {
      return List.of("三维坐标", "能量", "Band gap", "形成能", "密度", "稳定性/凸包能", "磁性", "晶胞/空间群");
    }
    if (JARVIS_2D_DATASET_ID.equals(datasetId)) {
      return List.of("三维坐标", "能量", "Band gap", "形成能", "密度", "稳定性/凸包能", "剥离能", "磁性", "晶胞/空间群");
    }
    if (POLYMER_GENOME_DATASET_ID.equals(datasetId)) {
      return List.of("三维坐标", "能量", "原子化能", "Band gap", "介电常数", "晶胞/空间群");
    }
    if (MATBENCH_WBM_DATASET_ID.equals(datasetId)) {
      return List.of("能量", "形成能", "稳定性/凸包能", "Band gap", "晶胞/空间群", "原型结构");
    }
    if (MATBENCH_MP_ENERGIES_DATASET_ID.equals(datasetId)) {
      return List.of("能量", "每原子能量", "形成能", "稳定性/凸包能", "分解焓", "晶胞/空间群");
    }
    if (MATBENCH_PHONONDB_DATASET_ID.equals(datasetId)) {
      return List.of("三维坐标", "热导率", "声子频率", "晶胞/空间群");
    }
    if (MATBENCH_V01_DIELECTRIC_DATASET_ID.equals(datasetId)) {
      return List.of("三维坐标", "介电常数", "折射率", "晶胞/空间群");
    }
    if (MATBENCH_V01_JDFT2D_DATASET_ID.equals(datasetId)) {
      return List.of("三维坐标", "剥离能", "二维材料", "晶胞/空间群");
    }
    if (MATBENCH_V01_PHONONS_DATASET_ID.equals(datasetId)) {
      return List.of("三维坐标", "声子", "声子 DOS 峰", "晶胞/空间群");
    }
    if (MATBENCH_V01_PEROVSKITES_DATASET_ID.equals(datasetId)) {
      return List.of("三维坐标", "能量", "形成能", "钙钛矿", "晶胞/空间群");
    }
    if (MATBENCH_V01_LOG_GVRH_DATASET_ID.equals(datasetId)) {
      return List.of("三维坐标", "力学性能", "剪切模量", "弹性性质", "晶胞/空间群");
    }
    if (MATBENCH_V01_LOG_KVRH_DATASET_ID.equals(datasetId)) {
      return List.of("三维坐标", "力学性能", "体积模量", "弹性性质", "晶胞/空间群");
    }
    if (QM9_DATASET_ID.equals(datasetId)) {
      return List.of("能量", "HOMO/LUMO", "HOMO-LUMO gap", "偶极矩", "极化率", "热力学校正", "SMILES");
    }
    if (HYDROCARBONS_GAP_DATASET_ID.equals(datasetId)) {
      return List.of("三维坐标", "能量", "力", "应力", "构型类型");
    }
    return List.of("三维坐标", "能量", "总能量", "Band gap", "晶胞/空间群");
  }

  private String representationFor(String datasetId) {
    if (ANI1X_DATASET_ID.equals(datasetId)) {
      return "化学式 group 索引，展示代表构象";
    }
    if (TRANSITION1X_DATASET_ID.equals(datasetId)) {
      return "reaction 索引，展示代表反应图像";
    }
    if (MATBENCH_WBM_DATASET_ID.equals(datasetId) || MATBENCH_MP_ENERGIES_DATASET_ID.equals(datasetId)) {
      return "性质表记录展开，无三维结构";
    }
    if (MATBENCH_PHONONDB_DATASET_ID.equals(datasetId)) {
      return "结构与热输运标签展开";
    }
    if (MATBENCH_V01_DIELECTRIC_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_JDFT2D_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_PHONONS_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_PEROVSKITES_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_LOG_GVRH_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_LOG_KVRH_DATASET_ID.equals(datasetId)) {
      return "Matbench v0.1 结构-性质基准记录展开";
    }
    if (QM9_DATASET_ID.equals(datasetId)) {
      return "DeepChem QM9 表格属性记录展开，无三维结构";
    }
    if (HYDROCARBONS_GAP_DATASET_ID.equals(datasetId)) {
      return "extxyz 构型全量展开";
    }
    return "展示记录全量展开";
  }

  private static List<String> splitMetadata(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Pattern.compile(";\\s*").splitAsStream(value)
        .map(String::trim)
        .filter(part -> !part.isBlank())
        .toList();
  }

  private static int parseMinAtoms(String atomCountRange) {
    return Integer.parseInt(atomCountRange.split("-", 2)[0].trim());
  }

  private static int parseMaxAtoms(String atomCountRange) {
    return Integer.parseInt(atomCountRange.split("-", 2)[1].trim());
  }

  private List<DatasetLinkResponse> linksFor(String datasetId) {
    if (ANI_DATASET_ID.equals(datasetId)) {
      return List.of(
          new DatasetLinkResponse("论文 DOI", "paper", "https://doi.org/10.1038/sdata.2017.193"),
          new DatasetLinkResponse("数据仓库", "data", "https://github.com/isayev/ANI1_dataset")
      );
    }
    if (LMDB_DATASET_ID.equals(datasetId)) {
      return List.of(new DatasetLinkResponse("arXiv 论文", "paper", "https://doi.org/10.48550/arXiv.2512.23117"));
    }
    if (OPENPOLY_DATASET_ID.equals(datasetId)) {
      return List.of(new DatasetLinkResponse("论文 DOI", "paper", "https://doi.org/10.1007/s10118-025-3402-y"));
    }
    if (ANI1X_DATASET_ID.equals(datasetId)) {
      return List.of(
          new DatasetLinkResponse("论文 DOI", "paper", "https://doi.org/10.1063/1.5023802"),
          new DatasetLinkResponse("数据发布页", "data", "https://doi.org/10.6084/m9.figshare.c.4712477.v1")
      );
    }
    if (TRANSITION1X_DATASET_ID.equals(datasetId)) {
      return List.of(
          new DatasetLinkResponse("论文 DOI", "paper", "https://doi.org/10.1038/s41597-022-01870-w"),
          new DatasetLinkResponse("数据下载页", "data", "https://doi.org/10.6084/m9.figshare.19614657.v4")
      );
    }
    if (TWOD_MATPEDIA_DATASET_ID.equals(datasetId)) {
      return List.of(
          new DatasetLinkResponse("论文 DOI", "paper", "https://doi.org/10.1038/s41597-019-0097-3"),
          new DatasetLinkResponse("数据发布页", "data", "https://doi.org/10.6084/m9.figshare.7699910.v1")
      );
    }
    if (JARVIS_3D_DATASET_ID.equals(datasetId) || JARVIS_2D_DATASET_ID.equals(datasetId)) {
      return List.of(
          new DatasetLinkResponse("论文 DOI", "paper", "https://doi.org/10.1038/s41524-020-00440-1"),
          new DatasetLinkResponse("JARVIS 数据门户", "data", "https://jarvis.nist.gov/")
      );
    }
    if (POLYMER_GENOME_DATASET_ID.equals(datasetId)) {
      return List.of(
          new DatasetLinkResponse("论文 DOI", "paper", "https://doi.org/10.1038/sdata.2016.12"),
          new DatasetLinkResponse("Dryad 数据下载", "data", "https://doi.org/10.5061/dryad.5ht3n")
      );
    }
    if (QMOF_DATASET_ID.equals(datasetId)) {
      return List.of(
          new DatasetLinkResponse("原始论文 DOI", "paper", "https://doi.org/10.1016/j.matt.2021.02.015"),
          new DatasetLinkResponse("热力学扩展论文", "paper", "https://doi.org/10.1038/s41524-022-00796-6"),
          new DatasetLinkResponse("QMOF 数据下载", "data", "https://doi.org/10.6084/m9.figshare.13147324")
      );
    }
    if (MATBENCH_WBM_DATASET_ID.equals(datasetId)
        || MATBENCH_MP_ENERGIES_DATASET_ID.equals(datasetId)
        || MATBENCH_PHONONDB_DATASET_ID.equals(datasetId)) {
      return List.of(
          new DatasetLinkResponse("Matbench Discovery 论文", "paper", "https://doi.org/10.1038/s42256-025-01055-1"),
          new DatasetLinkResponse("Matbench Discovery 数据页", "data", "https://matbench-discovery.materialsproject.org/data"),
          new DatasetLinkResponse("Matbench Discovery GitHub", "code", "https://github.com/janosh/matbench-discovery")
      );
    }
    if (MATBENCH_V01_DIELECTRIC_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_JDFT2D_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_PHONONS_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_PEROVSKITES_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_LOG_GVRH_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_LOG_KVRH_DATASET_ID.equals(datasetId)) {
      return List.of(
          new DatasetLinkResponse("Matbench 论文 DOI", "paper", "https://doi.org/10.1038/s41524-020-00406-3"),
          new DatasetLinkResponse("Matbench v0.1 数据下载", "data", "https://hackingmaterials.lbl.gov/automatminer/datasets.html")
      );
    }
    if (QM9_DATASET_ID.equals(datasetId)) {
      return List.of(
          new DatasetLinkResponse("QM9 论文 DOI", "paper", "https://doi.org/10.1038/sdata.2014.22"),
          new DatasetLinkResponse("DeepChem QM9 CSV", "data", "https://deepchemdata.s3-us-west-1.amazonaws.com/datasets/qm9.csv")
      );
    }
    if (HYDROCARBONS_GAP_DATASET_ID.equals(datasetId)) {
      return List.of(
          new DatasetLinkResponse("arXiv 论文", "paper", "https://doi.org/10.48550/arXiv.2409.08194"),
          new DatasetLinkResponse("arXiv 页面", "paper", "https://arxiv.org/abs/2409.08194")
      );
    }
    return List.of();
  }

  private RawQualityRow rawQualityRow(ResultSet rs) throws SQLException {
    return new RawQualityRow(
        rs.getString("DATASET_KEY"),
        rs.getLong("TOTAL_RECORDS"),
        rs.getLong("SOURCE_ID_FILLED"),
        rs.getLong("MATERIAL_FILLED"),
        rs.getLong("COMPOSITION_FILLED"),
        rs.getLong("ATOM_COUNT_FILLED"),
        rs.getLong("ENERGY_FILLED"),
        rs.getLong("ELECTRONIC_FILLED"),
        rs.getLong("STRUCTURE_FILLED"),
        rs.getLong("FORCE_FILLED"),
        rs.getLong("PROPERTY_FILLED"),
        rs.getLong("DOI_FILLED"),
        rs.getLong("SOFTWARE_FILLED"),
        rs.getLong("WARNING_COUNT"),
        rs.getInt("MIN_ATOMS"),
        rs.getInt("MAX_ATOMS"),
        rs.getDouble("MIN_ENERGY"),
        rs.getDouble("MAX_ENERGY")
    );
  }

  private DatasetQualityResponse buildDatasetQuality(RawQualityRow row, DatasetCardResponse card) {
    boolean structureExpected = structureExpectedFor(row.datasetId());
    boolean forceExpected = forceExpectedFor(row.datasetId());
    boolean periodicExpected = periodicStructureExpectedFor(row.datasetId());
    QualityScientificProbe probe = scientificProbeFor(
        row.datasetId(),
        structureExpected,
        forceExpected,
        periodicExpected,
        row.totalRecords()
    );
    double identifierCoverage = Math.max(ratio(row.sourceIdFilled(), row.totalRecords()),
        ratio(row.materialFilled(), row.totalRecords()));
    double compositionCoverage = ratio(row.compositionFilled(), row.totalRecords());
    double atomCoverage = ratio(row.atomCountFilled(), row.totalRecords());
    double energyCoverage = ratio(row.energyFilled(), row.totalRecords());
    double electronicCoverage = ratio(row.electronicFilled(), row.totalRecords());
    double structureCoverage = ratio(row.structureFilled(), row.totalRecords());
    double forceCoverage = ratio(row.forceFilled(), row.totalRecords());
    double propertyCoverage = ratio(row.propertyFilled(), row.totalRecords());
    double softwareCoverage = ratio(row.softwareFilled(), row.totalRecords());
    double doiCoverage = ratio(row.doiFilled(), row.totalRecords());
    double datasetLinkCoverage = linksFor(row.datasetId()).isEmpty() ? 0.0 : 1.0;
    double targetLabelCoverage = Math.max(
        Math.max(energyCoverage, electronicCoverage),
        Math.max(propertyCoverage, forceExpected ? forceCoverage : 0.0)
    );

    double completenessScore = average(identifierCoverage, compositionCoverage, atomCoverage);
    double structureScore = structureExpected ? structureCoverage : 1.0;
    double numericScore = average(atomCoverage, targetLabelCoverage);
    double traceabilityScore = 0.40 * identifierCoverage
        + 0.25 * softwareCoverage
        + 0.35 * Math.max(doiCoverage, datasetLinkCoverage);
    double structureValidityScore = structureExpected ? probe.structureValidityScore() : 1.0;
    double duplicateScore = probe.duplicateScore();
    double unitMethodScore = unitMethodScoreFor(row.datasetId(), energyCoverage, forceCoverage, softwareCoverage, doiCoverage);
    double adapterReadinessScore = adapterReadinessScoreFor(row.datasetId(), structureCoverage, targetLabelCoverage, traceabilityScore);
    double warningPenalty = Math.min(0.12, ratio(row.warningCount(), row.totalRecords()) * 0.6);
    double scientificPenalty = Math.min(0.18,
        (1.0 - structureValidityScore) * 0.10
            + (1.0 - duplicateScore) * 0.06
            + (1.0 - unitMethodScore) * 0.06
            + ratio(probe.invalidAtomCount(), row.totalRecords()) * 0.08);
    int score = clampScore(100.0 * (
        0.22 * completenessScore
            + 0.18 * structureScore
            + 0.18 * numericScore
            + 0.16 * traceabilityScore
            + 0.10 * structureValidityScore
            + 0.06 * duplicateScore
            + 0.06 * unitMethodScore
            + 0.04 * adapterReadinessScore
            - warningPenalty
            - scientificPenalty
    ));

    List<QualityCoverageMetricResponse> metrics = List.of(
        metric("identifier", "标识/材料名", Math.max(row.sourceIdFilled(), row.materialFilled()), row.totalRecords(), true),
        metric("composition", "组成", row.compositionFilled(), row.totalRecords(), true),
        metric("atomCount", "原子数", row.atomCountFilled(), row.totalRecords(), true),
        metric("energy", "能量标签", row.energyFilled(), row.totalRecords(), true),
        metric("structure", "三维结构", row.structureFilled(), row.totalRecords(), structureExpected),
        metric("forces", "力标签", row.forceFilled(), row.totalRecords(), forceExpected),
        metric("electronic", "电子性质", row.electronicFilled(), row.totalRecords(), false),
        metric("targetLabel", "目标性质标签", Math.max(row.energyFilled(),
            Math.max(row.electronicFilled(), Math.max(row.propertyFilled(), forceExpected ? row.forceFilled() : 0))),
            row.totalRecords(), true),
        metric("software", "计算软件", row.softwareFilled(), row.totalRecords(), true),
        metric("doi", "DOI/数据链接", Math.max(row.doiFilled(), datasetLinkCoverage > 0 ? row.totalRecords() : 0),
            row.totalRecords(), true),
        metric("structureValidity", "结构抽样合法性", Math.round(structureValidityScore * row.totalRecords()), row.totalRecords(), structureExpected),
        metric("duplicateSignature", "疑似重复签名", Math.round(duplicateScore * row.totalRecords()), row.totalRecords(), true),
        metric("unitMethod", "单位/方法证据", Math.round(unitMethodScore * row.totalRecords()), row.totalRecords(), true),
        metric("adapterReadiness", "解析适配成熟度", Math.round(adapterReadinessScore * row.totalRecords()), row.totalRecords(), true)
    );
    List<QualityAuditItemResponse> auditItems = auditItemsFor(
        row,
        metrics,
        completenessScore,
        numericScore,
        traceabilityScore,
        structureExpected,
        forceExpected,
        probe
    );
    List<String> missingFields = missingFieldsFor(row, metrics, structureExpected, forceExpected);
    String reviewStatus = reviewStatusFor(score, auditItems);
    String publishTier = publishTierFor(score, auditItems);

    return new DatasetQualityResponse(
        row.datasetId(),
        card.name(),
        typeFor(row.datasetId()),
        row.totalRecords(),
        score,
        levelFor(score),
        roundRatio(completenessScore),
        roundRatio(structureScore),
        roundRatio(numericScore),
        roundRatio(traceabilityScore),
        structureExpected,
        forceExpected,
        row.warningCount(),
        row.minAtoms() + "-" + row.maxAtoms(),
        energyRange(row.minEnergy(), row.maxEnergy()),
        metrics,
        recommendationsFor(row, score, structureExpected, forceExpected, traceabilityScore, probe),
        auditItems,
        missingFields,
        reviewStatus,
        publishTier,
        auditSummary(auditItems)
    );
  }

  private List<QualityIssueResponse> issuesFor(DatasetQualityResponse quality) {
    List<QualityIssueResponse> issues = new ArrayList<>();
    Map<String, QualityCoverageMetricResponse> metrics = new LinkedHashMap<>();
    for (QualityCoverageMetricResponse metric : quality.metrics()) {
      metrics.put(metric.key(), metric);
    }
    addIssueIfLow(issues, quality, metrics.get("identifier"), 0.98,
        "记录标识不完整", "补充 source_record_id、material_id 或标准材料名，保证每条记录可引用。");
    addIssueIfLow(issues, quality, metrics.get("composition"), 0.95,
        "组成字段覆盖不足", "从结构或原始文件中自动反推 composition，并统一元素大小写。");
    addIssueIfLow(issues, quality, metrics.get("atomCount"), 0.95,
        "原子数字段覆盖不足", "入库时从 structure_json 或原始结构重新计算 atom_count。");
    addIssueIfLow(issues, quality, metrics.get("targetLabel"), 0.80,
        "目标性质标签不足", "明确该数据集的核心学习目标；补齐能量、力、电子性质、声子、模量或介电等标签及单位。");
    addIssueIfLow(issues, quality, metrics.get("structure"), 0.80,
        "结构坐标不足", "补充三维坐标、晶胞和周期性信息；无法补齐时在详情页标为表格型数据。");
    addIssueIfLow(issues, quality, metrics.get("forces"), 0.80,
        "力标签不足", "势函数训练数据应补齐 forces_json，并明确单位 eV/A 或 Hartree/Bohr。");
    addIssueIfLow(issues, quality, metrics.get("structureValidity"), 0.95,
        "结构科学抽样未通过", "对全量结构调用 ASE/pymatgen，检查原子数、元素、晶胞、周期性、有限坐标和异常键长。");
    addIssueIfLow(issues, quality, metrics.get("duplicateSignature"), 0.90,
        "疑似重复记录比例偏高", "生成结构哈希和记录签名，区分真实构象重复、版本重复和入库重复。");
    addIssueIfLow(issues, quality, metrics.get("unitMethod"), 0.85,
        "单位或理论层级证据不足", "补充能量/力/应力/带隙等单位、泛函、基组或赝势、软件版本与收敛阈值。");
    addIssueIfLow(issues, quality, metrics.get("adapterReadiness"), 0.80,
        "自动入库适配器尚不成熟", "为该数据源补充 adapter manifest、字段映射、单位转换、抽样报告和回滚脚本。");
    if (quality.traceabilityScore() < 0.75) {
      issues.add(new QualityIssueResponse(
          quality.datasetId(),
          quality.name(),
          "中",
          "可追溯性不足",
          "软件、DOI 或数据链接覆盖不够，专家难以复现计算来源。",
          "补充论文链接、数据下载链接、计算软件版本、泛函、基组/赝势和原始文件索引。"
      ));
    }
    if (quality.warningCount() > 0) {
      issues.add(new QualityIssueResponse(
          quality.datasetId(),
          quality.name(),
          "低",
          "存在入库警告",
          "当前数据集中有 " + quality.warningCount() + " 条记录带 warnings_json。",
          "将 warning 类型拆分成可筛选字段，例如缺坐标、缺力、单位不明、解析失败。"
      ));
    }
    return issues;
  }

  private void addIssueIfLow(
      List<QualityIssueResponse> issues,
      DatasetQualityResponse quality,
      QualityCoverageMetricResponse metric,
      double threshold,
      String title,
      String suggestion
  ) {
    if (metric == null || !metric.expected() || metric.ratio() >= threshold) {
      return;
    }
    issues.add(new QualityIssueResponse(
        quality.datasetId(),
        quality.name(),
        metric.ratio() < 0.50 ? "高" : "中",
        title,
        metric.label() + "覆盖率为 " + Math.round(metric.ratio() * 100) + "%。",
        suggestion
    ));
  }

  private List<String> recommendationsFor(
      RawQualityRow row,
      int score,
      boolean structureExpected,
      boolean forceExpected,
      double traceabilityScore,
      QualityScientificProbe probe
  ) {
    List<String> recommendations = new ArrayList<>();
    if (!structureExpected) {
      recommendations.add("该数据集定位为性质表/统计表，不强制要求三维结构，但详情页需明确不可做结构可视化。");
    } else if (ratio(row.structureFilled(), row.totalRecords()) < 0.95) {
      recommendations.add("补齐 structure_json，并校验原子数与 composition 是否一致。");
    }
    if (forceExpected && ratio(row.forceFilled(), row.totalRecords()) < 0.95) {
      recommendations.add("补齐 forces_json 与力单位，避免势函数训练时标签不一致。");
    }
    long targetLabelFilled = Math.max(row.energyFilled(),
        Math.max(row.electronicFilled(), Math.max(row.propertyFilled(), forceExpected ? row.forceFilled() : 0)));
    if (ratio(targetLabelFilled, row.totalRecords()) < 0.95) {
      recommendations.add("补充核心目标性质字段和单位说明，区分总能、形成能、带隙、声子、介电、模量等标签。");
    }
    if (traceabilityScore < 0.85) {
      recommendations.add("补充论文/数据 DOI、原始文件路径、软件版本、泛函、基组或赝势。");
    }
    if (row.warningCount() > 0) {
      recommendations.add("将 warnings_json 拆成标准质量标签，支持按异常类型筛选。");
    }
    if (structureExpected && probe.structureSampled() > 0 && probe.structureValidityScore() < 0.95) {
      recommendations.add("结构抽样发现原子数不一致、坐标异常或周期晶胞缺失风险，需用 ASE/pymatgen 对全量结构做合法性复核。");
    }
    if (probe.duplicateScore() < 0.90) {
      recommendations.add("存在较高疑似重复签名比例，需基于 composition + atom_count + energy + source id 做去重确认。");
    }
    if (probe.invalidAtomCount() > 0) {
      recommendations.add("发现原子数小于等于 0 的异常记录，正式发布前应阻断或修复。");
    }
    if (recommendations.isEmpty() && score >= 85) {
      recommendations.add("字段覆盖较好，可优先用于专家演示、数据发现和基线建模。");
    }
    return recommendations;
  }

  private List<QualityAuditItemResponse> auditItemsFor(
      RawQualityRow row,
      List<QualityCoverageMetricResponse> metrics,
      double completenessScore,
      double numericScore,
      double traceabilityScore,
      boolean structureExpected,
      boolean forceExpected,
      QualityScientificProbe probe
  ) {
    QualityCoverageMetricResponse identifier = metricByKey(metrics, "identifier");
    QualityCoverageMetricResponse composition = metricByKey(metrics, "composition");
    QualityCoverageMetricResponse atomCount = metricByKey(metrics, "atomCount");
    QualityCoverageMetricResponse structure = metricByKey(metrics, "structure");
    QualityCoverageMetricResponse forces = metricByKey(metrics, "forces");
    QualityCoverageMetricResponse energy = metricByKey(metrics, "energy");
    QualityCoverageMetricResponse targetLabel = metricByKey(metrics, "targetLabel");
    QualityCoverageMetricResponse software = metricByKey(metrics, "software");
    QualityCoverageMetricResponse doi = metricByKey(metrics, "doi");
    QualityCoverageMetricResponse structureValidity = metricByKey(metrics, "structureValidity");
    QualityCoverageMetricResponse duplicateSignature = metricByKey(metrics, "duplicateSignature");
    QualityCoverageMetricResponse unitMethod = metricByKey(metrics, "unitMethod");
    QualityCoverageMetricResponse adapterReadiness = metricByKey(metrics, "adapterReadiness");

    List<QualityAuditItemResponse> items = new ArrayList<>();
    items.add(auditItem(
        "source",
        "来源与引用审核",
        traceabilityScore,
        "记录标识覆盖 " + percent(identifier.ratio()) + "，软件覆盖 " + percent(software.ratio())
            + "，DOI/数据链接覆盖 " + percent(doi.ratio()) + "。",
        "补齐论文 DOI、数据下载链接、原始文件索引、计算软件版本和记录级 source id。"
    ));
    items.add(auditItem(
        "schema",
        "字段映射审核",
        completenessScore,
        "组成覆盖 " + percent(composition.ratio()) + "，原子数覆盖 " + percent(atomCount.ratio())
            + "，唯一标识覆盖 " + percent(identifier.ratio()) + "。",
        "检查解析器和字段映射表，缺失字段进入 extraProperties 之前应先尝试标准化。"
    ));
    if (structureExpected) {
      items.add(auditItem(
          "structure",
          "结构一致性审核",
          structure.ratio(),
          "三维结构覆盖 " + percent(structure.ratio()) + "，原子数范围 " + row.minAtoms() + "-" + row.maxAtoms() + "。",
          "校验 structure_json 中原子数量、元素、晶胞、周期性、NaN 坐标和异常键长。"
      ));
    } else {
      items.add(new QualityAuditItemResponse(
          "structure",
          "结构一致性审核",
          "不适用",
          100,
          "该数据集定位为表格型性质/基准数据，当前不强制要求三维结构。",
          "详情页应明确标注无三维结构，避免被误用于结构模型。"
      ));
    }
    items.add(auditItem(
        "scientificStructureProbe",
        "结构科学抽样审计",
        structureExpected ? structureValidity.ratio() : 1.0,
        "抽样 " + probe.structureSampled() + " 条结构；解析失败 " + probe.structureParseFailures()
            + "，原子数不一致 " + probe.atomCountMismatches()
            + "，坐标异常 " + probe.coordinateAnomalies()
            + "，周期晶胞缺失 " + probe.latticeMissing() + "。",
        "正式认证前应使用 ASE/pymatgen 对全量结构检查元素、原子数、晶胞、周期性、有限坐标和异常键长。"
    ));
    items.add(auditItem(
        "duplicate",
        "重复与唯一性审计",
        duplicateSignature.ratio(),
        "疑似唯一签名 " + probe.distinctSignatures() + " / " + row.totalRecords()
            + "；重复风险评分 " + percent(duplicateSignature.ratio()) + "。",
        "用 source_record_id、结构哈希、composition、atom_count、energy 和 DOI 版本号做正式去重。"
    ));
    double labelScore = forceExpected
        ? average(numericScore, forces.ratio(), targetLabel.ratio())
        : average(numericScore, targetLabel.ratio());
    items.add(auditItem(
        "label",
        "数值标签审核",
        labelScore,
        "能量覆盖 " + percent(energy.ratio()) + "，目标性质覆盖 " + percent(targetLabel.ratio()) + "，力标签覆盖 "
            + (forceExpected ? percent(forces.ratio()) : "非必需") + "。",
        "统一能量/力/带隙/模量等单位，区分总能、每原子能、形成能、吸附能和校正能。"
    ));
    double methodScore = doi.ratio() >= 0.95
        ? Math.max(0.65, average(software.ratio(), doi.ratio()))
        : average(software.ratio(), doi.ratio());
    items.add(auditItem(
        "method",
        "理论层级审核",
        average(methodScore, unitMethod.ratio()),
        "计算软件覆盖 " + percent(software.ratio()) + "，可追溯链接覆盖 " + percent(doi.ratio()) + "。",
        "补充 VASP/ORCA/Gaussian 版本、泛函、基组或赝势、色散校正、收敛阈值。"
    ));
    items.add(auditItem(
        "adapter",
        "自动入库适配器就绪度",
        adapterReadiness.ratio(),
        "解析适配成熟度 " + percent(adapterReadiness.ratio()) + "；结构覆盖 " + percent(structure.ratio())
            + "，目标标签覆盖 " + percent(targetLabel.ratio()) + "，追溯评分 " + percent(traceabilityScore) + "。",
        "进入全自动入库前需要适配器 manifest、字段映射、单位转换、抽样报告和回滚脚本。"
    ));
    if (isPolymerDataset(row.datasetId())) {
      double polymerScore = average(identifier.ratio(), composition.ratio(), Math.max(energy.ratio(), 0.55), software.ratio());
      items.add(auditItem(
          "polymerDomain",
          "高分子专项审核",
          polymerScore,
          "聚合物数据需重点确认重复单元/链段标识、组成、理论层级和目标性质。当前组成覆盖 "
              + percent(composition.ratio()) + "。",
          "补齐 repeat unit、SMILES/PSMILES、聚合反应类型、Rg/密度/Tg/力学性质等高分子专属字段。"
      ));
    } else {
      items.add(new QualityAuditItemResponse(
          "polymerDomain",
          "高分子专项审核",
          "不适用",
          100,
          "该数据集不是高分子主数据集，可作为分子/晶体/MOF/反应路径参考数据。",
          "在数据发现页保持领域标签，避免和高分子专属 benchmark 混用。"
      ));
    }
    return List.copyOf(items);
  }

  private QualityAuditItemResponse auditItem(
      String key,
      String label,
      double scoreRatio,
      String evidence,
      String action
  ) {
    int score = clampScore(scoreRatio * 100.0);
    return new QualityAuditItemResponse(key, label, auditStatusFor(score), score, evidence, action);
  }

  private List<String> missingFieldsFor(
      RawQualityRow row,
      List<QualityCoverageMetricResponse> metrics,
      boolean structureExpected,
      boolean forceExpected
  ) {
    List<String> fields = new ArrayList<>();
    addMissing(fields, metricByKey(metrics, "identifier"), 0.98, "唯一记录标识/材料名");
    addMissing(fields, metricByKey(metrics, "composition"), 0.95, "元素组成");
    addMissing(fields, metricByKey(metrics, "atomCount"), 0.95, "原子数");
    addMissing(fields, metricByKey(metrics, "targetLabel"), 0.80, "能量或目标性质标签");
    if (structureExpected) {
      addMissing(fields, metricByKey(metrics, "structure"), 0.80, "三维结构/晶胞");
    }
    if (forceExpected) {
      addMissing(fields, metricByKey(metrics, "forces"), 0.80, "力标签");
    }
    addMissing(fields, metricByKey(metrics, "software"), 0.85, "计算软件/版本");
    addMissing(fields, metricByKey(metrics, "doi"), 0.85, "论文 DOI/数据下载链接");
    addMissing(fields, metricByKey(metrics, "structureValidity"), 0.95, "结构合法性抽样证据");
    addMissing(fields, metricByKey(metrics, "duplicateSignature"), 0.90, "去重/唯一性证据");
    addMissing(fields, metricByKey(metrics, "unitMethod"), 0.85, "单位与理论层级证据");
    addMissing(fields, metricByKey(metrics, "adapterReadiness"), 0.80, "自动入库适配器 manifest");
    if (row.warningCount() > 0) {
      fields.add("标准化 warning 标签");
    }
    if (isPolymerDataset(row.datasetId())) {
      fields.add("高分子专属字段：repeat unit、PSMILES、聚合反应类型、Rg/密度/Tg/力学性质");
    }
    return fields.isEmpty() ? List.of("暂无关键阻断字段") : List.copyOf(fields);
  }

  private void addMissing(
      List<String> fields,
      QualityCoverageMetricResponse metric,
      double threshold,
      String label
  ) {
    if (metric != null && metric.expected() && metric.ratio() < threshold) {
      fields.add(label);
    }
  }

  private QualityCoverageMetricResponse metricByKey(List<QualityCoverageMetricResponse> metrics, String key) {
    return metrics.stream()
        .filter(metric -> key.equals(metric.key()))
        .findFirst()
        .orElse(new QualityCoverageMetricResponse(key, key, 0, 0, 0.0, false));
  }

  private double metricRatio(DatasetQualityResponse quality, String key) {
    return quality.metrics().stream()
        .filter(metric -> key.equals(metric.key()))
        .map(QualityCoverageMetricResponse::ratio)
        .findFirst()
        .orElse(0.0);
  }

  private String reviewStatusFor(int score, List<QualityAuditItemResponse> auditItems) {
    boolean blocked = auditItems.stream().anyMatch(item -> "阻断".equals(item.status()));
    boolean review = auditItems.stream().anyMatch(item -> "复核".equals(item.status()));
    if (blocked || score < 55) {
      return "暂缓发布";
    }
    if (review || score < 80) {
      return "专家复核";
    }
    return "通过发布";
  }

  private String publishTierFor(int score, List<QualityAuditItemResponse> auditItems) {
    boolean blocked = auditItems.stream().anyMatch(item -> "阻断".equals(item.status()));
    if (blocked || score < 55) {
      return "Quarantine / 内部排查";
    }
    if (score >= 90) {
      return "Gold / 标杆训练集";
    }
    if (score >= 80) {
      return "Silver / 展示与建模";
    }
    return "Bronze / 可展示需补元数据";
  }

  private String auditSummary(List<QualityAuditItemResponse> auditItems) {
    long passed = auditItems.stream().filter(item -> "通过".equals(item.status()) || "不适用".equals(item.status())).count();
    long review = auditItems.stream().filter(item -> "复核".equals(item.status())).count();
    long blocked = auditItems.stream().filter(item -> "阻断".equals(item.status())).count();
    return passed + "/" + auditItems.size() + " 项通过，" + review + " 项需复核，" + blocked + " 项阻断。";
  }

  private String auditStatusFor(int score) {
    if (score >= 85) {
      return "通过";
    }
    if (score >= 65) {
      return "复核";
    }
    return "阻断";
  }

  private static String percent(double ratio) {
    return Math.round(ratio * 100.0) + "%";
  }

  private boolean isPolymerDataset(String datasetId) {
    return LMDB_DATASET_ID.equals(datasetId)
        || OPENPOLY_DATASET_ID.equals(datasetId)
        || POLYMER_GENOME_DATASET_ID.equals(datasetId);
  }

  private boolean periodicStructureExpectedFor(String datasetId) {
    return TWOD_MATPEDIA_DATASET_ID.equals(datasetId)
        || JARVIS_3D_DATASET_ID.equals(datasetId)
        || JARVIS_2D_DATASET_ID.equals(datasetId)
        || QMOF_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_JDFT2D_DATASET_ID.equals(datasetId)
        || MATBENCH_V01_PEROVSKITES_DATASET_ID.equals(datasetId);
  }

  private double unitMethodScoreFor(
      String datasetId,
      double energyCoverage,
      double forceCoverage,
      double softwareCoverage,
      double doiCoverage
  ) {
    double curatedLinkEvidence = linksFor(datasetId).isEmpty() ? 0.35 : 0.90;
    double softwareEvidence = Math.max(softwareCoverage, curatedLinkEvidence * 0.75);
    double sourceEvidence = Math.max(doiCoverage, curatedLinkEvidence);
    double labelEvidence = Math.max(energyCoverage, forceCoverage);
    return clampRatio(0.38 * softwareEvidence + 0.34 * sourceEvidence + 0.28 * labelEvidence);
  }

  private double adapterReadinessScoreFor(
      String datasetId,
      double structureCoverage,
      double targetLabelCoverage,
      double traceabilityScore
  ) {
    double adapterEvidence = curatedAdapterDataset(datasetId) ? 0.85 : 0.55;
    double structureEvidence = structureExpectedFor(datasetId) ? structureCoverage : 1.0;
    return clampRatio(0.30 * adapterEvidence + 0.25 * structureEvidence + 0.25 * targetLabelCoverage + 0.20 * traceabilityScore);
  }

  private boolean curatedAdapterDataset(String datasetId) {
    return List.of(
        ANI_DATASET_ID,
        LMDB_DATASET_ID,
        OPENPOLY_DATASET_ID,
        ANI1X_DATASET_ID,
        TRANSITION1X_DATASET_ID,
        TWOD_MATPEDIA_DATASET_ID,
        JARVIS_3D_DATASET_ID,
        JARVIS_2D_DATASET_ID,
        POLYMER_GENOME_DATASET_ID,
        QMOF_DATASET_ID,
        MATBENCH_WBM_DATASET_ID,
        MATBENCH_MP_ENERGIES_DATASET_ID,
        MATBENCH_PHONONDB_DATASET_ID,
        HYDROCARBONS_GAP_DATASET_ID,
        MATBENCH_V01_DIELECTRIC_DATASET_ID,
        MATBENCH_V01_JDFT2D_DATASET_ID,
        MATBENCH_V01_PHONONS_DATASET_ID,
        MATBENCH_V01_PEROVSKITES_DATASET_ID,
        MATBENCH_V01_LOG_GVRH_DATASET_ID,
        MATBENCH_V01_LOG_KVRH_DATASET_ID,
        QM9_DATASET_ID
    ).contains(datasetId);
  }

  private QualityScientificProbe scientificProbeFor(
      String datasetId,
      boolean structureExpected,
      boolean forceExpected,
      boolean periodicExpected,
      long totalRecords
  ) {
    long distinctSignatures = totalRecords;
    long invalidAtomCount = 0;
    String aggregateSql = """
        SELECT
          COUNT(DISTINCT
            COALESCE(NULLIF(TRIM(SOURCE_RECORD_ID), ''), '') || '|' ||
            COALESCE(NULLIF(TRIM(MATERIAL_ID), ''), '') || '|' ||
            COALESCE(NULLIF(TRIM(COMPOSITION), ''), '') || '|' ||
            COALESCE(NULLIF(TRIM(ATOM_COUNT), ''), '') || '|' ||
            COALESCE(NULLIF(TRIM(ENERGY), ''), '')
          ) AS DISTINCT_SIGNATURES,
          SUM(CASE
            WHEN NULLIF(TRIM(ATOM_COUNT), '') IS NOT NULL
             AND CAST(NULLIF(TRIM(ATOM_COUNT), '') AS INT) <= 0 THEN 1
            ELSE 0
          END) AS INVALID_ATOM_COUNT
        FROM DISPLAY_RECORDS
        WHERE DATASET_KEY = ?
        """;
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(aggregateSql)) {
      statement.setString(1, datasetId);
      try (ResultSet rs = statement.executeQuery()) {
        if (rs.next()) {
          distinctSignatures = rs.getLong("DISTINCT_SIGNATURES");
          invalidAtomCount = rs.getLong("INVALID_ATOM_COUNT");
        }
      }
    } catch (SQLException ex) {
      throw databaseError(ex);
    }

    long structureSampled = 0;
    long structureParseFailures = 0;
    long atomCountMismatches = 0;
    long coordinateAnomalies = 0;
    long latticeMissing = 0;
    long forceSampled = 0;
    long forceVectorMismatches = 0;
    String sampleSql = """
        SELECT ATOM_COUNT, STRUCTURE_JSON, FORCES_JSON
        FROM DISPLAY_RECORDS
        WHERE DATASET_KEY = ?
          AND (
            (STRUCTURE_JSON IS NOT NULL AND LENGTH(STRUCTURE_JSON) > 2)
            OR (FORCES_JSON IS NOT NULL AND LENGTH(FORCES_JSON) > 2)
          )
        ORDER BY ID
        LIMIT 160
        """;
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(sampleSql)) {
      statement.setString(1, datasetId);
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          Integer atomCount = parseInteger(rs.getString("ATOM_COUNT"));
          String structureJson = rs.getString("STRUCTURE_JSON");
          String forcesJson = rs.getString("FORCES_JSON");
          if (structureJson != null && structureJson.length() > 2) {
            structureSampled++;
            StructureProbeResult result = inspectStructureJson(structureJson, atomCount, periodicExpected);
            if (!result.parsed()) structureParseFailures++;
            if (result.atomCountMismatch()) atomCountMismatches++;
            if (result.coordinateAnomaly()) coordinateAnomalies++;
            if (result.latticeMissing()) latticeMissing++;
          }
          if (forceExpected && forcesJson != null && forcesJson.length() > 2) {
            forceSampled++;
            if (atomCount != null && atomCount > 0 && countForceVectors(forcesJson) != atomCount) {
              forceVectorMismatches++;
            }
          }
        }
      }
    } catch (SQLException ex) {
      throw databaseError(ex);
    }
    return new QualityScientificProbe(
        totalRecords,
        distinctSignatures,
        invalidAtomCount,
        structureSampled,
        structureParseFailures,
        atomCountMismatches,
        coordinateAnomalies,
        latticeMissing,
        forceSampled,
        forceVectorMismatches
    );
  }

  private StructureProbeResult inspectStructureJson(String structureJson, Integer atomCount, boolean periodicExpected) {
    try {
      JsonNode root = objectMapper.readTree(structureJson);
      JsonNode atoms = root.path("atoms");
      if (!atoms.isArray()) {
        return new StructureProbeResult(false, false, false, periodicExpected);
      }
      boolean coordinateAnomaly = false;
      int atomRows = 0;
      for (JsonNode atom : atoms) {
        atomRows++;
        if (!validCoordinate(atom.path("x")) || !validCoordinate(atom.path("y")) || !validCoordinate(atom.path("z"))) {
          coordinateAnomaly = true;
        }
        String element = atom.path("element").asText("");
        if (element.isBlank() && !atom.has("atomic_number")) {
          coordinateAnomaly = true;
        }
      }
      boolean mismatch = atomCount != null && atomCount > 0 && atomRows != atomCount;
      boolean missingLattice = periodicExpected && !validLattice(root.path("lattice"));
      return new StructureProbeResult(true, mismatch, coordinateAnomaly, missingLattice);
    } catch (Exception ex) {
      return new StructureProbeResult(false, false, false, periodicExpected);
    }
  }

  private boolean validCoordinate(JsonNode node) {
    if (!node.isNumber()) {
      return false;
    }
    double value = node.asDouble();
    return Double.isFinite(value) && Math.abs(value) < 1000.0;
  }

  private boolean validLattice(JsonNode lattice) {
    if (!lattice.isArray() || lattice.size() != 3) {
      return false;
    }
    for (JsonNode row : lattice) {
      if (!row.isArray() || row.size() < 3) {
        return false;
      }
      for (int i = 0; i < 3; i++) {
        if (!validCoordinate(row.get(i))) {
          return false;
        }
      }
    }
    return true;
  }

  private int countForceVectors(String forcesJson) {
    try {
      JsonNode root = objectMapper.readTree(forcesJson);
      JsonNode array = root.isArray() ? root : root.path("forces");
      if (!array.isArray()) {
        return -1;
      }
      return array.size();
    } catch (Exception ex) {
      return -1;
    }
  }

  private Integer parseInteger(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static double clampRatio(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  private List<QualityGateResponse> qualityGates(List<DatasetQualityResponse> datasets) {
    int completeness = averageScore(datasets.stream().map(DatasetQualityResponse::completenessScore).toList());
    int structure = averageScore(datasets.stream().map(DatasetQualityResponse::structureScore).toList());
    int numeric = averageScore(datasets.stream().map(DatasetQualityResponse::numericScore).toList());
    int traceability = averageScore(datasets.stream().map(DatasetQualityResponse::traceabilityScore).toList());
    int scientific = averageScore(datasets.stream()
        .map(item -> average(
            metricRatio(item, "structureValidity"),
            metricRatio(item, "duplicateSignature"),
            metricRatio(item, "unitMethod")
        ))
        .toList());
    int adapter = averageScore(datasets.stream()
        .map(item -> metricRatio(item, "adapterReadiness"))
        .toList());
    long modelReady = datasets.stream().filter(item -> item.score() >= 80).count();
    int modelReadyScore = datasets.isEmpty() ? 0 : clampScore(modelReady * 100.0 / datasets.size());
    List<Double> polymerScores = datasets.stream()
        .filter(item -> isPolymerDataset(item.datasetId()))
        .map(item -> item.score() / 100.0)
        .toList();
    int polymerScore = polymerScores.isEmpty() ? 100 : averageScore(polymerScores);
    return List.of(
        gate("identity", "基础字段完整性", completeness, "标识、组成、原子数三类基础字段的平均覆盖。"),
        gate("structure", "结构/坐标可用性", structure, "按数据集用途校验三维坐标；表格型数据不强制扣分。"),
        gate("numeric", "数值标签可建模性", numeric, "能量、电子性质、力标签等可训练字段的覆盖情况。"),
        gate("trace", "来源与复现链路", traceability, "DOI/数据链接、软件版本和记录标识的可追溯程度。"),
        gate("polymer", "高分子专项门控", polymerScore, "对聚合物数据集额外关注重复单元、链段/聚合物标识、目标性质和理论层级。"),
        gate("scientific", "科学一致性审计", scientific, "抽样检查结构 JSON、原子数、坐标有限性、周期晶胞、重复签名和单位/方法证据。"),
        gate("adapter", "自动入库闭环", adapter, "评估数据源是否具备自动下载、解析适配、字段映射、单位转换、重建展示库和回滚条件。"),
        gate("modelReady", "可直接建模数据集", modelReadyScore, modelReady + " / " + datasets.size() + " 个数据集评分达到 80 分以上。")
    );
  }

  private List<QualityAuditStageResponse> qualityAuditStages() {
    return List.of(
        new QualityAuditStageResponse(
            "source",
            "来源登记",
            "注册用户/管理员",
            "已接入",
            "登记论文 DOI、数据下载链接、许可证、数据说明和联系人。",
            "数据接入中心已支持最小字段提交，来源进入超级管理员审核。"
        ),
        new QualityAuditStageResponse(
            "source-review",
            "来源审核",
            "超级管理员",
            "已接入",
            "确认链接可访问、论文与数据对应、许可可复用、与现有数据不重复。",
            "通过后进入解析适配；不通过则保留驳回原因。"
        ),
        new QualityAuditStageResponse(
            "adapter",
            "解析适配",
            "数据管理员",
            "部分接入",
            "识别 HDF5/LMDB/CSV/JSON/CIF/extxyz 等格式，并抽样扫描字段。",
            "当前展示库由构建脚本解析；后续可为每类数据源增加独立 adapter。"
        ),
        new QualityAuditStageResponse(
            "mapping",
            "字段映射",
            "数据管理员",
            "已接入",
            "映射到统一字段：结构、元素、原子数、能量、力、性质、方法、链接和 extraProperties。",
            "缺失字段不阻断入库，但会进入质量评分和缺失字段清单。"
        ),
        new QualityAuditStageResponse(
            "validation",
            "质量验证",
            "系统自动 + 专家复核",
            "已接入",
            "生成完整性、结构、数值、追溯、高分子专项和建模可用性评分。",
            "本页展示自动质检结果；原始 VASP 输出级校验仍需后续扩展。"
        ),
        new QualityAuditStageResponse(
            "release",
            "发布与回滚",
            "超级管理员",
            "待增强",
            "按 Gold/Silver/Bronze/Quarantine 等级决定公开展示、下载和建模权限。",
            "建议后续增加版本号、构建日志、发布确认和一键回滚。"
        )
    );
  }

  private List<QualityAuditRuleResponse> qualityAuditRules() {
    return List.of(
        new QualityAuditRuleResponse(
            "source",
            "来源可信与可引用",
            "来源",
            16,
            "有 DOI 或稳定数据链接，记录可追溯到原始文件。",
            "暂缓公开下载，只允许内部排查。"
        ),
        new QualityAuditRuleResponse(
            "schema",
            "统一字段映射",
            "格式",
            14,
            "标识、组成、原子数、目标性质至少达到展示级覆盖。",
            "补解析器或映射表，重新构建 H2。"
        ),
        new QualityAuditRuleResponse(
            "structure",
            "结构与原子一致性",
            "结构",
            14,
            "需要结构的数据集应有坐标/晶胞，且原子数与组成一致。",
            "标记为表格型或补齐结构文件。"
        ),
        new QualityAuditRuleResponse(
            "label",
            "数值标签与单位",
            "性质",
            14,
            "能量、力、band gap、模量等字段有明确含义和单位。",
            "限制建模使用，前端显示字段缺失或单位待确认。"
        ),
        new QualityAuditRuleResponse(
            "method",
            "理论层级与复现参数",
            "计算",
            14,
            "明确软件、版本、泛函、基组或赝势、色散校正和收敛设置。",
            "降级为可浏览数据，不进入标杆训练集。"
        ),
        new QualityAuditRuleResponse(
            "scientific",
            "科学一致性与异常检测",
            "科学审计",
            14,
            "抽样结构应能解析，原子数、元素、坐标、周期晶胞、力数组和重复签名应基本一致。",
            "进入专家复核；正式发布前必须补做 ASE/pymatgen 全量结构验证、结构哈希、异常键长和单位检查。"
        ),
        new QualityAuditRuleResponse(
            "adapter",
            "自动入库闭环就绪度",
            "工程闭环",
            8,
            "应具备下载链接、文件格式识别、解析适配器、字段映射、单位转换、构建日志和回滚方案。",
            "不得直接自动上线；只能生成候选接入申请，由管理员确认后开发或启用适配器。"
        ),
        new QualityAuditRuleResponse(
            "polymer",
            "高分子专属信息",
            "高分子",
            8,
            "聚合物数据应尽量包含重复单元、PSMILES/SMILES、链段/反应类型和热力学/力学性质。",
            "保留展示，但在高分子筛选和模型训练中提示适用性风险。"
        )
    );
  }

  private QualityGateResponse gate(String key, String title, int score, String description) {
    return new QualityGateResponse(key, title, statusFor(score), score, description);
  }

  private static QualityCoverageMetricResponse metric(
      String key,
      String label,
      long filled,
      long total,
      boolean expected
  ) {
    return new QualityCoverageMetricResponse(key, label, filled, total, roundRatio(ratio(filled, total)), expected);
  }

  private boolean structureExpectedFor(String datasetId) {
    return !OPENPOLY_DATASET_ID.equals(datasetId)
        && !MATBENCH_WBM_DATASET_ID.equals(datasetId)
        && !MATBENCH_MP_ENERGIES_DATASET_ID.equals(datasetId)
        && !QM9_DATASET_ID.equals(datasetId);
  }

  private boolean forceExpectedFor(String datasetId) {
    return LMDB_DATASET_ID.equals(datasetId)
        || ANI1X_DATASET_ID.equals(datasetId)
        || TRANSITION1X_DATASET_ID.equals(datasetId)
        || HYDROCARBONS_GAP_DATASET_ID.equals(datasetId);
  }

  private static double ratio(long filled, long total) {
    if (total <= 0) {
      return 0.0;
    }
    return Math.max(0.0, Math.min(1.0, (double) filled / (double) total));
  }

  private static double average(double... values) {
    if (values.length == 0) {
      return 0.0;
    }
    double sum = 0.0;
    for (double value : values) {
      sum += value;
    }
    return sum / values.length;
  }

  private static int averageScore(List<Double> values) {
    if (values.isEmpty()) {
      return 0;
    }
    return clampScore(values.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100.0);
  }

  private static double roundRatio(double value) {
    return Math.round(value * 1000.0) / 1000.0;
  }

  private static int clampScore(double value) {
    return Math.max(0, Math.min(100, (int) Math.round(value)));
  }

  private static String levelFor(int score) {
    if (score >= 85) {
      return "优";
    }
    if (score >= 70) {
      return "良";
    }
    if (score >= 55) {
      return "需补充";
    }
    return "高风险";
  }

  private static String statusFor(int score) {
    if (score >= 85) {
      return "通过";
    }
    if (score >= 70) {
      return "观察";
    }
    if (score >= 55) {
      return "需补充";
    }
    return "高风险";
  }

  private static int severityRank(String severity) {
    if ("高".equals(severity)) {
      return 0;
    }
    if ("中".equals(severity)) {
      return 1;
    }
    return 2;
  }

  private static String energyRange(double minEnergy, double maxEnergy) {
    if (!Double.isFinite(minEnergy) || !Double.isFinite(maxEnergy)) {
      return "无可统计能量";
    }
    return formatLabel(minEnergy) + " - " + formatLabel(maxEnergy);
  }

  private static void addNumber(List<Double> values, String raw) {
    if (raw == null || raw.isBlank()) {
      return;
    }
    try {
      double value = Double.parseDouble(raw.trim());
      if (Double.isFinite(value)) {
        values.add(value);
      }
    } catch (NumberFormatException ignored) {
      // Some source fields are JSON-like or textual; those are not chartable.
    }
  }

  private static void addElementCounts(Map<String, Long> counts, String composition) {
    if (composition == null || composition.isBlank()) {
      return;
    }
    Matcher matcher = COMPOSITION_ELEMENT_PATTERN.matcher(composition);
    while (matcher.find()) {
      String element = matcher.group(1);
      double amount = 1.0;
      String amountText = matcher.group(2);
      if (amountText != null && !amountText.isBlank()) {
        try {
          amount = Double.parseDouble(amountText);
        } catch (NumberFormatException ignored) {
          amount = 1.0;
        }
      }
      long increment = Math.max(1L, Math.round(amount));
      counts.merge(element, increment, Long::sum);
    }
  }

  private static List<HistogramBinResponse> histogram(List<Double> values, int requestedBins) {
    if (values.isEmpty()) {
      return List.of();
    }
    double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
    double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(min);
    int bins = Math.max(1, Math.min(requestedBins, values.size()));
    if (Double.compare(min, max) == 0) {
      return List.of(new HistogramBinResponse(formatLabel(min), min, max, values.size()));
    }

    long[] counts = new long[bins];
    double span = max - min;
    for (double value : values) {
      int index = (int) Math.floor(((value - min) / span) * bins);
      if (index >= bins) {
        index = bins - 1;
      }
      if (index < 0) {
        index = 0;
      }
      counts[index] += 1;
    }

    List<HistogramBinResponse> result = new ArrayList<>();
    for (int i = 0; i < bins; i += 1) {
      double start = min + span * i / bins;
      double end = min + span * (i + 1) / bins;
      result.add(new HistogramBinResponse(formatLabel(start) + " - " + formatLabel(end), start, end, counts[i]));
    }
    return result;
  }

  private static String formatLabel(double value) {
    double abs = Math.abs(value);
    if ((abs >= 10000 || abs < 0.001) && abs > 0) {
      return String.format(Locale.ROOT, "%.2e", value);
    }
    if (abs >= 100) {
      return String.format(Locale.ROOT, "%.0f", value);
    }
    if (abs >= 10) {
      return String.format(Locale.ROOT, "%.1f", value);
    }
    return String.format(Locale.ROOT, "%.2f", value);
  }

  private DatasetRecordSummaryResponse summaryFrom(ResultSet rs) throws SQLException {
    return new DatasetRecordSummaryResponse(
        rs.getLong("ID"),
        rs.getString("DATASET_KEY"),
        value(rs, "SOURCE_RECORD_ID"),
        value(rs, "DATASET_NAME"),
        displayMaterialName(
            rs.getString("DATASET_KEY"),
            value(rs, "SOURCE_RECORD_ID"),
            value(rs, "MATERIAL_NAME"),
            value(rs, "MATERIAL_ID"),
            value(rs, "COMPOSITION"),
            value(rs, "ATOM_COUNT")
        ),
        value(rs, "MATERIAL_ID"),
        value(rs, "SMILES"),
        value(rs, "COMPOSITION"),
        value(rs, "ATOM_COUNT"),
        value(rs, "ENERGY"),
        value(rs, "HOMO"),
        value(rs, "LUMO"),
        value(rs, "HOMO_LUMO_GAP"),
        value(rs, "CHARGE"),
        value(rs, "SPIN"),
        value(rs, "CALCULATION_SOFTWARE")
    );
  }

  private DatasetRecordDetailResponse detailFrom(ResultSet rs) throws SQLException {
    return new DatasetRecordDetailResponse(
        rs.getLong("ID"),
        rs.getString("DATASET_KEY"),
        value(rs, "SOURCE_RECORD_ID"),
        value(rs, "DATASET_NAME"),
        value(rs, "DATASET_SIZE"),
        value(rs, "DATASET_DESCRIPTION"),
        displayMaterialName(
            rs.getString("DATASET_KEY"),
            value(rs, "SOURCE_RECORD_ID"),
            value(rs, "MATERIAL_NAME"),
            value(rs, "MATERIAL_ID"),
            value(rs, "COMPOSITION"),
            value(rs, "ATOM_COUNT")
        ),
        value(rs, "MATERIAL_ID"),
        value(rs, "FORCE_FIELD"),
        value(rs, "SIMULATION_TYPE"),
        value(rs, "VALIDATED_STATUS"),
        value(rs, "SMILES"),
        value(rs, "POLYMERIZATION_DEGREE"),
        value(rs, "RADIUS_GYRATION_RG"),
        value(rs, "CHAIN_CONFORMATION"),
        value(rs, "CALCULATION_SOFTWARE"),
        value(rs, "ENSEMBLE"),
        value(rs, "TEMPERATURE"),
        value(rs, "DENSITY"),
        value(rs, "GLASS_TRANSITION_TEMPERATURE_TG"),
        value(rs, "YOUNGS_MODULUS"),
        value(rs, "TENSILE_STRENGTH"),
        value(rs, "HOMO"),
        value(rs, "LUMO"),
        value(rs, "HOMO_LUMO_GAP"),
        value(rs, "DOI"),
        value(rs, "CATEGORY"),
        value(rs, "CALCULATION_PLATFORM"),
        value(rs, "CALCULATION_TIME"),
        value(rs, "ENERGY"),
        value(rs, "COMPOSITION"),
        value(rs, "ATOM_COUNT"),
        value(rs, "CHARGE"),
        value(rs, "SPIN"),
        parseWarnings(value(rs, "WARNINGS_JSON")),
        parseExtraProperties(value(rs, "PROPERTIES_JSON")),
        parseAtoms(value(rs, "STRUCTURE_JSON")),
        parseLattice(value(rs, "STRUCTURE_JSON")),
        "数据文件未提供统一单位；坐标、能量、力和电子结构字段均按原始记录展示。LMDB 的 cell 当前不可用于真实周期晶胞，3D 视图使用坐标包围盒辅助观察。"
    );
  }

  private String displayMaterialName(
      String datasetId,
      String sourceRecordId,
      String materialName,
      String materialId,
      String composition,
      String atomCount
  ) {
    if (materialName != null && !materialName.isBlank()) {
      return materialName;
    }
    if (ANI_DATASET_ID.equals(datasetId)) {
      return aniDisplayName(sourceRecordId, materialId, atomCount);
    }
    String suffix = sourceRecordId == null || sourceRecordId.isBlank()
        ? materialId
        : sourceRecordId.substring(0, Math.min(8, sourceRecordId.length()));
    String descriptor = composition == null || composition.isBlank()
        ? "未命名聚合物"
        : composition;
    String atoms = atomCount == null || atomCount.isBlank() ? "" : " · " + atomCount + " 原子";
    String unique = suffix == null || suffix.isBlank() ? "" : " · " + suffix;
    return "聚合物 " + descriptor + atoms + unique;
  }

  private String aniDisplayName(String sourceRecordId, String materialId, String atomCount) {
    String groupId = materialId == null || materialId.isBlank() ? sourceRecordId : materialId;
    int groupIndex = parseAniGroupIndex(groupId);
    String formula = ANI_GROUP_FORMULAS.getOrDefault(groupIndex, "");
    String conformer = "";
    int hash = sourceRecordId == null ? -1 : sourceRecordId.indexOf('#');
    if (hash >= 0 && hash + 1 < sourceRecordId.length()) {
      try {
        conformer = " · 构象 " + (Integer.parseInt(sourceRecordId.substring(hash + 1)) + 1);
      } catch (NumberFormatException ignored) {
        conformer = "";
      }
    }
    String formulaPart = formula.isBlank() ? "" : " · " + formula;
    String atoms = atomCount == null || atomCount.isBlank() ? "" : " · " + atomCount + " 原子";
    return "ANI 分子组 " + (groupIndex >= 0 ? groupIndex : groupId) + formulaPart + atoms + conformer;
  }

  private int parseAniGroupIndex(String groupId) {
    if (groupId == null) {
      return -1;
    }
    int dash = groupId.lastIndexOf('-');
    if (dash < 0 || dash + 1 >= groupId.length()) {
      return -1;
    }
    try {
      return Integer.parseInt(groupId.substring(dash + 1));
    } catch (NumberFormatException ex) {
      return -1;
    }
  }

  private List<AtomCoordinateResponse> parseAtoms(String structureJson) {
    if (structureJson == null || structureJson.isBlank()) {
      return List.of();
    }
    try {
      JsonNode atomsNode = objectMapper.readTree(structureJson).path("atoms");
      if (!atomsNode.isArray()) {
        return List.of();
      }
      List<AtomCoordinateResponse> atoms = new ArrayList<>();
      int index = 1;
      for (JsonNode atom : atomsNode) {
        String element = atom.path("element").asText("");
        if (element.isBlank() && atom.has("atomic_number")) {
          element = ELEMENTS_BY_NUMBER.getOrDefault(atom.path("atomic_number").asInt(), "X");
        }
        atoms.add(new AtomCoordinateResponse(
            index++,
            element,
            atom.path("x").asDouble(),
            atom.path("y").asDouble(),
            atom.path("z").asDouble()
        ));
      }
      return atoms;
    } catch (Exception ex) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "结构 JSON 解析失败");
    }
  }

  private List<List<Double>> parseLattice(String structureJson) {
    if (structureJson == null || structureJson.isBlank()) {
      return List.of();
    }
    try {
      JsonNode latticeNode = objectMapper.readTree(structureJson).path("lattice");
      if (!latticeNode.isArray()) {
        return List.of();
      }
      List<List<Double>> lattice = new ArrayList<>();
      for (JsonNode row : latticeNode) {
        if (!row.isArray() || row.size() < 3) {
          return List.of();
        }
        lattice.add(List.of(row.get(0).asDouble(), row.get(1).asDouble(), row.get(2).asDouble()));
      }
      return lattice.size() == 3 ? lattice : List.of();
    } catch (Exception ex) {
      return List.of();
    }
  }

  private List<String> parseWarnings(String warningsJson) {
    if (warningsJson == null || warningsJson.isBlank()) {
      return List.of();
    }
    try {
      JsonNode node = objectMapper.readTree(warningsJson);
      if (!node.isArray()) {
        return List.of(warningsJson);
      }
      List<String> warnings = new ArrayList<>();
      for (JsonNode item : node) {
        warnings.add(item.asText());
      }
      return warnings;
    } catch (Exception ex) {
      return List.of(warningsJson);
    }
  }

  private Map<String, String> parseExtraProperties(String propertiesJson) {
    if (propertiesJson == null || propertiesJson.isBlank()) {
      return Map.of();
    }
    try {
      JsonNode node = objectMapper.readTree(propertiesJson);
      if (!node.isObject()) {
        return Map.of();
      }
      Map<String, String> properties = new LinkedHashMap<>();
      node.fields().forEachRemaining(entry -> properties.put(entry.getKey(), entry.getValue().asText("")));
      return properties;
    } catch (Exception ex) {
      return Map.of();
    }
  }

  private Connection openConnection() throws SQLException {
    return DriverManager.getConnection(resolveJdbcUrl(), "sa", "");
  }

  private String resolveJdbcUrl() {
    String cached = jdbcUrl;
    if (cached != null) {
      return cached;
    }
    synchronized (this) {
      if (jdbcUrl == null) {
        Path path = resolveDbPath();
        String normalized = stripMvDb(path).toAbsolutePath().normalize().toString().replace('\\', '/');
        String mode = properties.isDisplayWritable() ? ";IFEXISTS=TRUE" : ";ACCESS_MODE_DATA=r;IFEXISTS=TRUE";
        jdbcUrl = "jdbc:h2:" + normalized + mode;
      }
      return jdbcUrl;
    }
  }

  private Path resolveDbPath() {
    Path configured = Paths.get(properties.getDisplayDbPath());
    Path cwd = Paths.get("").toAbsolutePath();
    List<Path> candidates = configured.isAbsolute()
        ? List.of(configured)
        : List.of(
            cwd.resolve(configured).normalize(),
            cwd.resolve("..").resolve(configured).normalize()
        );
    return candidates.stream()
        .filter(DisplayDatasetService::dbExists)
        .findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
            "找不到 H2 展示数据库: " + properties.getDisplayDbPath()));
  }

  private static boolean dbExists(Path path) {
    return Files.exists(path) || Files.exists(Paths.get(path + ".mv.db"));
  }

  private static Path stripMvDb(Path path) {
    String value = path.toString();
    if (value.endsWith(".mv.db")) {
      return Paths.get(value.substring(0, value.length() - ".mv.db".length()));
    }
    return path;
  }

  private static String value(ResultSet rs, String column) throws SQLException {
    String value = rs.getString(column);
    return value == null ? "" : value;
  }

  private static void requireDataset(String datasetId) {
    if (!ANI_DATASET_ID.equals(datasetId)
        && !LMDB_DATASET_ID.equals(datasetId)
        && !OPENPOLY_DATASET_ID.equals(datasetId)
        && !ANI1X_DATASET_ID.equals(datasetId)
        && !TRANSITION1X_DATASET_ID.equals(datasetId)
        && !TWOD_MATPEDIA_DATASET_ID.equals(datasetId)
        && !JARVIS_3D_DATASET_ID.equals(datasetId)
        && !JARVIS_2D_DATASET_ID.equals(datasetId)
        && !POLYMER_GENOME_DATASET_ID.equals(datasetId)
        && !QMOF_DATASET_ID.equals(datasetId)
        && !MATBENCH_WBM_DATASET_ID.equals(datasetId)
        && !MATBENCH_MP_ENERGIES_DATASET_ID.equals(datasetId)
        && !MATBENCH_PHONONDB_DATASET_ID.equals(datasetId)
        && !HYDROCARBONS_GAP_DATASET_ID.equals(datasetId)
        && !MATBENCH_V01_DIELECTRIC_DATASET_ID.equals(datasetId)
        && !MATBENCH_V01_JDFT2D_DATASET_ID.equals(datasetId)
        && !MATBENCH_V01_PHONONS_DATASET_ID.equals(datasetId)
        && !MATBENCH_V01_PEROVSKITES_DATASET_ID.equals(datasetId)
        && !MATBENCH_V01_LOG_GVRH_DATASET_ID.equals(datasetId)
        && !MATBENCH_V01_LOG_KVRH_DATASET_ID.equals(datasetId)
        && !QM9_DATASET_ID.equals(datasetId)
        && !(datasetId != null && datasetId.startsWith("intake_"))) {
      throw new ApiException(HttpStatus.NOT_FOUND, "未知数据集: " + datasetId);
    }
  }

  private static ApiException databaseError(SQLException ex) {
    return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "读取展示数据库失败: " + ex.getMessage());
  }

  private record RecordFilters(
      String search,
      Double energyMin,
      Double energyMax,
      Integer atomMin,
      Integer atomMax
  ) {
  }

  private record RawQualityRow(
      String datasetId,
      long totalRecords,
      long sourceIdFilled,
      long materialFilled,
      long compositionFilled,
      long atomCountFilled,
      long energyFilled,
      long electronicFilled,
      long structureFilled,
      long forceFilled,
      long propertyFilled,
      long doiFilled,
      long softwareFilled,
      long warningCount,
      int minAtoms,
      int maxAtoms,
      double minEnergy,
      double maxEnergy
  ) {
  }

  private record QualityScientificProbe(
      long totalRecords,
      long distinctSignatures,
      long invalidAtomCount,
      long structureSampled,
      long structureParseFailures,
      long atomCountMismatches,
      long coordinateAnomalies,
      long latticeMissing,
      long forceSampled,
      long forceVectorMismatches
  ) {
    private double duplicateScore() {
      if (totalRecords <= 0) {
        return 1.0;
      }
      double duplicateRatio = 1.0 - Math.max(0.0, Math.min(1.0, (double) distinctSignatures / (double) totalRecords));
      double allowed = 0.05;
      return Math.max(0.0, Math.min(1.0, 1.0 - Math.max(0.0, duplicateRatio - allowed) * 2.0));
    }

    private double structureValidityScore() {
      if (structureSampled <= 0) {
        return 0.0;
      }
      double weightedProblems = structureParseFailures
          + atomCountMismatches
          + coordinateAnomalies
          + latticeMissing
          + forceVectorMismatches * 0.5;
      return Math.max(0.0, Math.min(1.0, 1.0 - weightedProblems / Math.max(1.0, structureSampled)));
    }
  }

  private record StructureProbeResult(
      boolean parsed,
      boolean atomCountMismatch,
      boolean coordinateAnomaly,
      boolean latticeMissing
  ) {
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.9f", value);
  }

  private static String csvValue(String value) {
    if (value == null) {
      return "";
    }
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }
}
