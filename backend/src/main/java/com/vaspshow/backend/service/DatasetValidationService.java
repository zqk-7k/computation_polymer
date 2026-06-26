package com.vaspshow.backend.service;

import com.vaspshow.backend.dto.DiscoveryCandidateResponse;
import com.vaspshow.backend.dto.ValidationCheck;
import com.vaspshow.backend.dto.ValidationReport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Automated proof-reading ("自动校对") for discovered candidates. Every rule is
 * offline — it reasons over metadata already collected during discovery plus a
 * read-only duplicate lookup against the display DB — so it never adds HTTP cost
 * to a discovery run and never writes or publishes anything. The aggregate
 * PASS/WARN/FAIL is advisory; downstream a FAIL only blocks auto-promotion.
 */
@Service
public class DatasetValidationService {

  private static final String PASS = "PASS";
  private static final String WARN = "WARN";
  private static final String FAIL = "FAIL";

  /** Only these checks can drive the overall verdict to FAIL. */
  private static final Set<String> CRITICAL = Set.of("link", "relevance", "duplicate");

  private static final Set<String> LICENSE_TOKENS = Set.of(
      "cc-by", "cc by", "cc0", "cc-zero", "creative commons", "creativecommons",
      "publicdomain", "public domain", "mit", "apache", "bsd", "gpl", "lgpl",
      "mpl", "odc", "odbl", "pddl", "opensource.org");

  private static final Set<String> LICENSE_PLACEHOLDERS = Set.of(
      "", "待确认", "未说明", "待管理员确认");

  private final DisplayDatasetService displayService;

  public DatasetValidationService(DisplayDatasetService displayService) {
    this.displayService = displayService;
  }

  public ValidationReport validate(DiscoveryCandidateResponse candidate) {
    List<ValidationCheck> checks = new ArrayList<>();
    checks.add(linkCheck(candidate));
    checks.add(licenseCheck(candidate));
    checks.add(formatCheck(candidate));
    checks.add(doiCheck(candidate));
    checks.add(fieldsCheck(candidate));
    checks.add(relevanceCheck(candidate));
    checks.add(duplicateCheck(candidate));
    checks.add(metadataCheck(candidate));
    String status = aggregate(checks);
    return new ValidationReport(status, summarize(status, checks), Instant.now().toString(), checks);
  }

  private static ValidationCheck linkCheck(DiscoveryCandidateResponse c) {
    String scale = c.dataScale() == null ? "" : c.dataScale();
    if (c.dataUrl() == null || c.dataUrl().isBlank()) {
      return new ValidationCheck("link", "数据链接", FAIL, "缺少数据下载入口");
    }
    if (!c.dataUrl().startsWith("http")) {
      return new ValidationCheck("link", "数据链接", WARN, "下载入口不是 HTTP 链接，需人工确认");
    }
    if (scale.contains("预检失败")) {
      return new ValidationCheck("link", "数据链接", FAIL, "链接预检失败，疑似失效或不可访问");
    }
    if (scale.contains("bytes")) {
      return new ValidationCheck("link", "数据链接", PASS, "链接可达，规模 " + scale);
    }
    return new ValidationCheck("link", "数据链接", WARN, "链接存在但未验证规模（" + (scale.isBlank() ? "未知" : scale) + "）");
  }

  private static ValidationCheck licenseCheck(DiscoveryCandidateResponse c) {
    String raw = c.license() == null ? "" : c.license().trim();
    String lic = raw.toLowerCase(Locale.ROOT);
    if (containsToken(lic)) {
      return new ValidationCheck("license", "许可证", PASS, "已识别为标准开放许可：" + raw);
    }
    if (!LICENSE_PLACEHOLDERS.contains(lic)) {
      return new ValidationCheck("license", "许可证", WARN, "存在许可证但未归一化识别：" + raw);
    }
    return new ValidationCheck("license", "许可证", WARN, "许可证缺失/待确认，不能直接公开下载");
  }

  private static ValidationCheck formatCheck(DiscoveryCandidateResponse c) {
    String format = c.dataFormat() == null ? "" : c.dataFormat();
    if (format.isBlank() || "待识别".equals(format)) {
      return new ValidationCheck("format", "数据格式", WARN, "格式待识别，接入前需抽样确认");
    }
    return new ValidationCheck("format", "数据格式", PASS, "已识别格式：" + format);
  }

  private static ValidationCheck doiCheck(DiscoveryCandidateResponse c) {
    String doi = stripDoi(c.doi());
    if (doi.isBlank()) {
      return new ValidationCheck("doi", "DOI", WARN, "缺少 DOI，来源可追溯性弱");
    }
    if (doi.startsWith("10.")) {
      return new ValidationCheck("doi", "DOI", PASS, "DOI 规范：" + doi);
    }
    return new ValidationCheck("doi", "DOI", WARN, "DOI 格式异常：" + doi);
  }

  private static ValidationCheck fieldsCheck(DiscoveryCandidateResponse c) {
    String fields = c.detectedFields() == null ? "" : c.detectedFields();
    if (fields.isBlank() || "metadata only".equals(fields)) {
      return new ValidationCheck("fields", "物理量字段", WARN, "仅检出元数据，未识别能量/力/结构等物理量");
    }
    return new ValidationCheck("fields", "物理量字段", PASS, "检出字段：" + fields);
  }

  private static ValidationCheck relevanceCheck(DiscoveryCandidateResponse c) {
    int relevance = c.relevance();
    if (relevance >= 44) {
      return new ValidationCheck("relevance", "材料相关度", PASS, "相关度 " + relevance + "，属计算材料领域");
    }
    if (relevance >= 22) {
      return new ValidationCheck("relevance", "材料相关度", WARN, "相关度 " + relevance + " 偏低，需人工确认是否对口");
    }
    return new ValidationCheck("relevance", "材料相关度", FAIL, "相关度 " + relevance + " 过低，疑似跨领域噪声");
  }

  private ValidationCheck duplicateCheck(DiscoveryCandidateResponse c) {
    String doi = stripDoi(c.doi());
    if (doi.isBlank()) {
      return new ValidationCheck("duplicate", "展示库查重", WARN, "无 DOI，无法精确查重");
    }
    int hits = displayService.countByDoi(doi);
    if (hits > 0) {
      return new ValidationCheck("duplicate", "展示库查重", FAIL, "展示库已存在该 DOI（命中 " + hits + " 条），无需重复接入");
    }
    return new ValidationCheck("duplicate", "展示库查重", PASS, "展示库未发现重复 DOI");
  }

  private static ValidationCheck metadataCheck(DiscoveryCandidateResponse c) {
    boolean hasTitle = c.title() != null && !c.title().isBlank();
    String method = c.method() == null ? "" : c.method();
    boolean hasMethod = !method.isBlank() && !method.contains("待从");
    if (hasTitle && hasMethod) {
      return new ValidationCheck("metadata", "元数据完整度", PASS, "标题与计算方法齐备");
    }
    if (hasTitle) {
      return new ValidationCheck("metadata", "元数据完整度", WARN, "缺计算方法/泛函信息，需从论文补全");
    }
    return new ValidationCheck("metadata", "元数据完整度", WARN, "标题缺失，元数据不完整");
  }

  private static String aggregate(List<ValidationCheck> checks) {
    boolean anyWarn = false;
    for (ValidationCheck check : checks) {
      if (FAIL.equals(check.status()) && CRITICAL.contains(check.key())) {
        return FAIL;
      }
      if (WARN.equals(check.status()) || FAIL.equals(check.status())) {
        anyWarn = true;
      }
    }
    return anyWarn ? WARN : PASS;
  }

  private static String summarize(String status, List<ValidationCheck> checks) {
    long pass = checks.stream().filter(c -> PASS.equals(c.status())).count();
    long warn = checks.stream().filter(c -> WARN.equals(c.status())).count();
    long fail = checks.stream().filter(c -> FAIL.equals(c.status())).count();
    String head = switch (status) {
      case PASS -> "校对通过";
      case WARN -> "校对存在提醒";
      default -> "校对未通过";
    };
    return String.format("%s：通过 %d / 提醒 %d / 不通过 %d。", head, pass, warn, fail);
  }

  private static boolean containsToken(String license) {
    for (String token : LICENSE_TOKENS) {
      if (license.contains(token)) {
        return true;
      }
    }
    return false;
  }

  private static String stripDoi(String value) {
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
    return v;
  }
}
