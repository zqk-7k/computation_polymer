package com.vaspshow.backend.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vasp.intake")
public class IntakeProperties {

  private String dbPath = "documents/data/vasp_intake";

  private final Discovery discovery = new Discovery();

  public String getDbPath() {
    return dbPath;
  }

  public void setDbPath(String dbPath) {
    this.dbPath = dbPath;
  }

  public Discovery getDiscovery() {
    return discovery;
  }

  /** Tunable behaviour for the automated dataset discovery pipeline. */
  public static class Discovery {

    /** Master switch for the scheduled discovery run. */
    private boolean enabled = true;

    /** Spring cron expression for the scheduled run. Default: weekly Monday 03:15. */
    private String cron = "0 15 3 ? * MON";

    /** DataCite search queries; each is paged up to maxResultsPerQuery. */
    private List<String> queries = new ArrayList<>(List.of(
        "VASP DFT dataset energy force materials",
        "density functional theory dataset polymer energy",
        "DFT structures forces HDF5 dataset",
        "materials phonon dielectric bandgap dataset",
        "computational materials dataset CIF POSCAR"
    ));

    /** Page size requested from DataCite per query. */
    private int maxResultsPerQuery = 8;

    /** Contact email appended to the discovery User-Agent (polite crawling). */
    private String contactEmail = "";

    /** Enable the DataCite source (indexes Zenodo / Figshare / Materials Cloud DOIs). */
    private boolean datacite = true;

    /** Enable the direct Zenodo records API source. */
    private boolean zenodo = true;

    /** Enable the NOMAD datasets API source. */
    private boolean nomad = true;

    /** Enable the direct Figshare articles search source. */
    private boolean figshare = true;

    /** Enable the Dryad datasets search source (broad; relevance-gated). */
    private boolean dryad = true;

    /** Enable the OpenAIRE datasets search source (broad; relevance-gated). */
    private boolean openaire = true;

    private final AutoPromote autoPromote = new AutoPromote();
    private final AutoArchive autoArchive = new AutoArchive();
    private final AutoAdapt autoAdapt = new AutoAdapt();
    private final Validate validate = new Validate();

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getCron() {
      return cron;
    }

    public void setCron(String cron) {
      this.cron = cron;
    }

    public List<String> getQueries() {
      return queries;
    }

    public void setQueries(List<String> queries) {
      this.queries = queries;
    }

    public int getMaxResultsPerQuery() {
      return maxResultsPerQuery;
    }

    public void setMaxResultsPerQuery(int maxResultsPerQuery) {
      this.maxResultsPerQuery = maxResultsPerQuery;
    }

    public String getContactEmail() {
      return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
      this.contactEmail = contactEmail;
    }

    public boolean isDatacite() {
      return datacite;
    }

    public void setDatacite(boolean datacite) {
      this.datacite = datacite;
    }

    public boolean isZenodo() {
      return zenodo;
    }

    public void setZenodo(boolean zenodo) {
      this.zenodo = zenodo;
    }

    public boolean isNomad() {
      return nomad;
    }

    public void setNomad(boolean nomad) {
      this.nomad = nomad;
    }

    public boolean isFigshare() {
      return figshare;
    }

    public void setFigshare(boolean figshare) {
      this.figshare = figshare;
    }

    public boolean isDryad() {
      return dryad;
    }

    public void setDryad(boolean dryad) {
      this.dryad = dryad;
    }

    public boolean isOpenaire() {
      return openaire;
    }

    public void setOpenaire(boolean openaire) {
      this.openaire = openaire;
    }

    public AutoPromote getAutoPromote() {
      return autoPromote;
    }

    public AutoArchive getAutoArchive() {
      return autoArchive;
    }

    public AutoAdapt getAutoAdapt() {
      return autoAdapt;
    }

    public Validate getValidate() {
      return validate;
    }
  }

  /**
   * Auto-validation ("自动校对"): runs an offline rule set over every discovered
   * candidate and stores a PASS/WARN/FAIL report. It never publishes or writes the
   * display DB; a FAIL only blocks auto-promotion into the human review queue.
   */
  public static class Validate {

    private boolean enabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  /**
   * Auto-promotion only moves high-confidence candidates into the human review
   * queue (SUBMITTED). It never publishes data; the super-admin gate stays manual.
   */
  public static class AutoPromote {

    private boolean enabled = true;
    private int minScore = 82;
    /** Minimum materials-relevance (0-100) required before a candidate may auto-promote. */
    private int minRelevance = 44;
    private boolean requireLicense = true;
    /** When true, only candidates whose validation status is PASS may auto-promote (WARN is then blocked too). */
    private boolean requireValidationPass = false;
    private int maxPerRun = 10;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getMinScore() {
      return minScore;
    }

    public void setMinScore(int minScore) {
      this.minScore = minScore;
    }

    public int getMinRelevance() {
      return minRelevance;
    }

    public void setMinRelevance(int minRelevance) {
      this.minRelevance = minRelevance;
    }

    public boolean isRequireLicense() {
      return requireLicense;
    }

    public void setRequireLicense(boolean requireLicense) {
      this.requireLicense = requireLicense;
    }

    public boolean isRequireValidationPass() {
      return requireValidationPass;
    }

    public void setRequireValidationPass(boolean requireValidationPass) {
      this.requireValidationPass = requireValidationPass;
    }

    public int getMaxPerRun() {
      return maxPerRun;
    }

    public void setMaxPerRun(int maxPerRun) {
      this.maxPerRun = maxPerRun;
    }
  }

  /** Auto-archiving hides obvious low-signal leads so reviewers see less noise. */
  public static class AutoArchive {

    private boolean enabled = false;
    private int maxScore = 30;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getMaxScore() {
      return maxScore;
    }

    public void setMaxScore(int maxScore) {
      this.maxScore = maxScore;
    }
  }

  /**
   * Auto-adapter: download a bounded sample of an approved source and parse its
   * fields/structure for review. It never writes to the display DB or publishes.
   */
  public static class AutoAdapt {

    private boolean enabled = false;
    private int maxDownloadMb = 50;
    private int sampleMb = 4;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getMaxDownloadMb() {
      return maxDownloadMb;
    }

    public void setMaxDownloadMb(int maxDownloadMb) {
      this.maxDownloadMb = maxDownloadMb;
    }

    public int getSampleMb() {
      return sampleMb;
    }

    public void setSampleMb(int sampleMb) {
      this.sampleMb = sampleMb;
    }
  }
}
