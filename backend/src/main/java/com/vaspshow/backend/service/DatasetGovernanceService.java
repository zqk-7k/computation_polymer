package com.vaspshow.backend.service;

import com.vaspshow.backend.config.IntakeProperties;
import com.vaspshow.backend.dto.AuthUserResponse;
import com.vaspshow.backend.dto.DatasetCardResponse;
import com.vaspshow.backend.dto.DatasetPublicationRequest;
import com.vaspshow.backend.dto.DatasetPublicationResponse;
import com.vaspshow.backend.dto.QualityIssueReviewRequest;
import com.vaspshow.backend.dto.QualityIssueReviewResponse;
import com.vaspshow.backend.dto.QualityPublishDecisionRequest;
import com.vaspshow.backend.dto.QualityPublishDecisionResponse;
import com.vaspshow.backend.dto.QualityRunReviewRequest;
import com.vaspshow.backend.dto.QualityRunReviewResponse;
import com.vaspshow.backend.exception.ApiException;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DatasetGovernanceService {

  private final IntakeProperties properties;
  private final AuthService authService;
  private volatile String jdbcUrl;

  public DatasetGovernanceService(IntakeProperties properties, AuthService authService) {
    this.properties = properties;
    this.authService = authService;
  }

  @PostConstruct
  public void initialize() {
    try (Connection connection = openConnection();
         Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE IF NOT EXISTS DATASET_PUBLICATION (
            DATASET_ID VARCHAR(120) PRIMARY KEY,
            PUBLISHED BOOLEAN NOT NULL,
            NOTE VARCHAR(1000) NOT NULL,
            UPDATED_BY VARCHAR(64) NOT NULL,
            UPDATED_AT VARCHAR(40) NOT NULL,
            GRADE VARCHAR(32) NOT NULL DEFAULT 'Silver',
            RUN_ID VARCHAR(80) NOT NULL DEFAULT '',
            DECISION VARCHAR(32) NOT NULL DEFAULT 'PUBLISH'
          )
          """);
      statement.execute("ALTER TABLE DATASET_PUBLICATION ADD COLUMN IF NOT EXISTS GRADE VARCHAR(32) NOT NULL DEFAULT 'Silver'");
      statement.execute("ALTER TABLE DATASET_PUBLICATION ADD COLUMN IF NOT EXISTS RUN_ID VARCHAR(80) NOT NULL DEFAULT ''");
      statement.execute("ALTER TABLE DATASET_PUBLICATION ADD COLUMN IF NOT EXISTS DECISION VARCHAR(32) NOT NULL DEFAULT 'PUBLISH'");
      statement.execute("""
          CREATE TABLE IF NOT EXISTS QUALITY_ISSUE_REVIEW (
            ISSUE_ID VARCHAR(180) PRIMARY KEY,
            STATUS VARCHAR(32) NOT NULL,
            OWNER VARCHAR(120) NOT NULL,
            NOTE VARCHAR(1000) NOT NULL,
            REVIEWER VARCHAR(64) NOT NULL,
            UPDATED_AT VARCHAR(40) NOT NULL
          )
          """);
      statement.execute("""
          CREATE TABLE IF NOT EXISTS QUALITY_RUN_REVIEW (
            REVIEW_ID VARCHAR(80) PRIMARY KEY,
            RUN_ID VARCHAR(80) NOT NULL,
            REVIEWER VARCHAR(64) NOT NULL,
            REVIEWER_ROLE VARCHAR(120) NOT NULL,
            CONCLUSION VARCHAR(32) NOT NULL,
            NOTE VARCHAR(1000) NOT NULL,
            CREATED_AT VARCHAR(40) NOT NULL
          )
          """);
      statement.execute("""
          CREATE TABLE IF NOT EXISTS PUBLISH_DECISION (
            DECISION_ID VARCHAR(80) PRIMARY KEY,
            DATASET_ID VARCHAR(120) NOT NULL,
            RUN_ID VARCHAR(80) NOT NULL,
            REVIEWER VARCHAR(64) NOT NULL,
            DECISION VARCHAR(32) NOT NULL,
            GRADE VARCHAR(32) NOT NULL,
            PUBLISHED BOOLEAN NOT NULL,
            COMMENT VARCHAR(1000) NOT NULL,
            CREATED_AT VARCHAR(40) NOT NULL
          )
          """);
    } catch (SQLException ex) {
      throw databaseError(ex);
    }
  }

  public boolean canViewHidden(String authorization) {
    AuthUserResponse user = authService.me(authorization);
    return AuthService.ROLE_SUPER_ADMIN.equals(user.role()) || AuthService.ROLE_ADMIN.equals(user.role());
  }

  public boolean isPublished(String datasetId) {
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(
             "SELECT PUBLISHED FROM DATASET_PUBLICATION WHERE DATASET_ID = ?")) {
      statement.setString(1, datasetId);
      try (ResultSet rs = statement.executeQuery()) {
        return !rs.next() || rs.getBoolean("PUBLISHED");
      }
    } catch (SQLException ex) {
      throw databaseError(ex);
    }
  }

  public void requireVisible(String datasetId, String authorization) {
    if (isPublished(datasetId) || canViewHidden(authorization)) {
      return;
    }
    throw new ApiException(HttpStatus.NOT_FOUND, "该数据集暂未发布");
  }

  public List<DatasetPublicationResponse> listPublication(
      String authorization,
      List<DatasetCardResponse> datasets
  ) {
    authService.requireDatasetReviewAccess(authorization);
    List<DatasetPublicationResponse> rows = new ArrayList<>();
    for (DatasetCardResponse dataset : datasets) {
      rows.add(publicationFor(dataset));
    }
    return rows;
  }

  public DatasetPublicationResponse updatePublication(
      String authorization,
      DatasetCardResponse dataset,
      DatasetPublicationRequest request
  ) {
    AuthUserResponse user = authService.me(authorization);
    authService.requireDatasetReviewAccess(authorization);
    String note = request == null || request.note() == null ? "" : request.note().trim();
    if (note.length() > 1000) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "发布说明不能超过 1000 字");
    }
    String grade = normalizeGrade(request == null ? null : request.grade(), request != null && request.published());
    String runId = safeText(request == null ? null : request.runId(), 80);
    String decision = normalizeDecision(request == null ? null : request.decision(), request != null && request.published());
    String now = Instant.now().toString();
    try {
      upsertPublication(dataset.id(), request != null && request.published(), note, user.username(), now, grade, runId, decision);
      return publicationFor(dataset);
    } catch (SQLException ex) {
      throw databaseError(ex);
    }
  }

  public DatasetPublicationResponse getPublication(DatasetCardResponse dataset) {
    return publicationFor(dataset);
  }

  public QualityIssueReviewResponse reviewIssue(
      String authorization,
      String issueIdValue,
      QualityIssueReviewRequest request
  ) {
    AuthUserResponse user = authService.me(authorization);
    authService.requireAdmin(authorization);
    String issueId = safeRequired(issueIdValue, "issueId", 180);
    String status = normalizeReviewStatus(request == null ? null : request.status());
    String owner = safeText(request == null ? null : request.owner(), 120);
    String note = safeText(request == null ? null : request.note(), 1000);
    String now = Instant.now().toString();
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement("""
             MERGE INTO QUALITY_ISSUE_REVIEW (ISSUE_ID, STATUS, OWNER, NOTE, REVIEWER, UPDATED_AT)
             KEY (ISSUE_ID)
             VALUES (?, ?, ?, ?, ?, ?)
             """)) {
      statement.setString(1, issueId);
      statement.setString(2, status);
      statement.setString(3, owner);
      statement.setString(4, note);
      statement.setString(5, user.username());
      statement.setString(6, now);
      statement.executeUpdate();
      return new QualityIssueReviewResponse(issueId, status, owner, note, user.username(), now);
    } catch (SQLException ex) {
      throw databaseError(ex);
    }
  }

  public QualityRunReviewResponse submitRunReview(
      String authorization,
      String runIdValue,
      QualityRunReviewRequest request
  ) {
    AuthUserResponse user = authService.me(authorization);
    authService.requireAdmin(authorization);
    String reviewId = "run-review-" + UUID.randomUUID();
    String runId = safeRequired(runIdValue, "runId", 80);
    String reviewerRole = safeText(request == null ? null : request.reviewerRole(), 120);
    String conclusion = normalizeRunConclusion(request == null ? null : request.conclusion());
    String note = safeText(request == null ? null : request.note(), 1000);
    String now = Instant.now().toString();
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement("""
             INSERT INTO QUALITY_RUN_REVIEW
               (REVIEW_ID, RUN_ID, REVIEWER, REVIEWER_ROLE, CONCLUSION, NOTE, CREATED_AT)
             VALUES (?, ?, ?, ?, ?, ?, ?)
             """)) {
      statement.setString(1, reviewId);
      statement.setString(2, runId);
      statement.setString(3, user.username());
      statement.setString(4, reviewerRole);
      statement.setString(5, conclusion);
      statement.setString(6, note);
      statement.setString(7, now);
      statement.executeUpdate();
      return new QualityRunReviewResponse(reviewId, runId, user.username(), reviewerRole, conclusion, note, now);
    } catch (SQLException ex) {
      throw databaseError(ex);
    }
  }

  public QualityPublishDecisionResponse publishDecision(
      String authorization,
      DatasetCardResponse dataset,
      QualityPublishDecisionRequest request
  ) {
    AuthUserResponse user = authService.me(authorization);
    authService.requireDatasetReviewAccess(authorization);
    String decisionId = "publish-" + UUID.randomUUID();
    String runId = safeText(request == null ? null : request.runId(), 80);
    String decision = normalizeDecision(request == null ? null : request.decision(), request != null && request.published());
    String grade = normalizeGrade(request == null ? null : request.grade(), request != null && request.published());
    boolean published = request != null && request.published();
    String comment = safeText(request == null ? null : request.comment(), 1000);
    String now = Instant.now().toString();
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement("""
             INSERT INTO PUBLISH_DECISION
               (DECISION_ID, DATASET_ID, RUN_ID, REVIEWER, DECISION, GRADE, PUBLISHED, COMMENT, CREATED_AT)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
             """)) {
      statement.setString(1, decisionId);
      statement.setString(2, dataset.id());
      statement.setString(3, runId);
      statement.setString(4, user.username());
      statement.setString(5, decision);
      statement.setString(6, grade);
      statement.setBoolean(7, published);
      statement.setString(8, comment);
      statement.setString(9, now);
      statement.executeUpdate();
      upsertPublication(dataset.id(), published, comment, user.username(), now, grade, runId, decision);
      return new QualityPublishDecisionResponse(
          decisionId,
          dataset.id(),
          runId,
          user.username(),
          decision,
          grade,
          published,
          comment,
          now
      );
    } catch (SQLException ex) {
      throw databaseError(ex);
    }
  }

  private void upsertPublication(
      String datasetId,
      boolean published,
      String note,
      String updatedBy,
      String updatedAt,
      String grade,
      String runId,
      String decision
  ) throws SQLException {
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement("""
             MERGE INTO DATASET_PUBLICATION
               (DATASET_ID, PUBLISHED, NOTE, UPDATED_BY, UPDATED_AT, GRADE, RUN_ID, DECISION)
             KEY (DATASET_ID)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?)
             """)) {
      statement.setString(1, datasetId);
      statement.setBoolean(2, published);
      statement.setString(3, note);
      statement.setString(4, updatedBy);
      statement.setString(5, updatedAt);
      statement.setString(6, grade);
      statement.setString(7, runId);
      statement.setString(8, decision);
      statement.executeUpdate();
    }
  }

  private DatasetPublicationResponse publicationFor(DatasetCardResponse dataset) {
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(
             "SELECT * FROM DATASET_PUBLICATION WHERE DATASET_ID = ?")) {
      statement.setString(1, dataset.id());
      try (ResultSet rs = statement.executeQuery()) {
        if (rs.next()) {
          return new DatasetPublicationResponse(
              dataset.id(),
              dataset.name(),
              rs.getBoolean("PUBLISHED"),
              rs.getString("NOTE"),
              rs.getString("UPDATED_BY"),
              rs.getString("UPDATED_AT"),
              rs.getString("GRADE"),
              rs.getString("RUN_ID"),
              rs.getString("DECISION")
          );
        }
      }
    } catch (SQLException ex) {
      throw databaseError(ex);
    }
    return new DatasetPublicationResponse(dataset.id(), dataset.name(), true, "默认发布", "system", "", "Silver", "", "PUBLISH");
  }

  private static String normalizeGrade(String value, boolean published) {
    String grade = value == null ? "" : value.trim();
    if (grade.isBlank()) {
      return published ? "Silver" : "Quarantine";
    }
    String normalized = grade.substring(0, 1).toUpperCase(Locale.ROOT) + grade.substring(1).toLowerCase(Locale.ROOT);
    if (!List.of("Gold", "Silver", "Bronze", "Quarantine").contains(normalized)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "发布等级仅支持 Gold、Silver、Bronze、Quarantine");
    }
    return normalized;
  }

  private static String normalizeDecision(String value, boolean published) {
    String decision = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    if (decision.isBlank()) {
      return published ? "PUBLISH" : "HIDE";
    }
    if (!List.of("PUBLISH", "HIDE", "HOLD", "ROLLBACK").contains(decision)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "发布决策仅支持 PUBLISH、HIDE、HOLD、ROLLBACK");
    }
    return decision;
  }

  private static String normalizeReviewStatus(String value) {
    String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    if (status.isBlank()) {
      return "OPEN";
    }
    if (!List.of("OPEN", "CONFIRMED", "FIXING", "WAIVED", "CLOSED").contains(status)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "问题状态仅支持 OPEN、CONFIRMED、FIXING、WAIVED、CLOSED");
    }
    return status;
  }

  private static String normalizeRunConclusion(String value) {
    String conclusion = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    if (conclusion.isBlank()) {
      return "REVIEWED";
    }
    if (!List.of("PASS", "REVIEWED", "REJECT", "NEEDS_FIX").contains(conclusion)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "审核结论仅支持 PASS、REVIEWED、REJECT、NEEDS_FIX");
    }
    return conclusion;
  }

  private static String safeRequired(String value, String field, int maxLength) {
    String text = safeText(value, maxLength);
    if (text.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, field + " 不能为空");
    }
    return text;
  }

  private static String safeText(String value, int maxLength) {
    String text = value == null ? "" : value.trim();
    if (text.length() > maxLength) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "文本长度不能超过 " + maxLength + " 字符");
    }
    return text;
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
        Path path = Paths.get(properties.getDbPath());
        Path cwd = Paths.get("").toAbsolutePath();
        Path candidate = path.isAbsolute() ? path : cwd.resolve(path).normalize();
        Path projectCandidate = path.isAbsolute() ? path : cwd.resolve("..").resolve(path).normalize();
        if (!path.isAbsolute() && Files.isDirectory(projectCandidate.getParent())) {
          candidate = projectCandidate;
        }
        try {
          Files.createDirectories(candidate.toAbsolutePath().getParent());
        } catch (Exception ex) {
          throw new IllegalStateException("创建数据治理数据库目录失败: " + ex.getMessage(), ex);
        }
        jdbcUrl = "jdbc:h2:" + candidate.toAbsolutePath().normalize().toString().replace('\\', '/');
      }
      return jdbcUrl;
    }
  }

  private static ApiException databaseError(SQLException ex) {
    return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "读取数据治理数据库失败: " + ex.getMessage());
  }
}
