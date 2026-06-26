package com.vaspshow.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vasp.auth")
public class AuthProperties {

  private String dbPath = "documents/data/vasp_auth";
  private String superAdminPassword = "ChangeMe-SuperAdmin-2026!";
  private String adminPassword = "ChangeMe-Admin-2026!";

  public String getDbPath() {
    return dbPath;
  }

  public void setDbPath(String dbPath) {
    this.dbPath = dbPath;
  }

  public String getSuperAdminPassword() {
    return superAdminPassword;
  }

  public void setSuperAdminPassword(String superAdminPassword) {
    this.superAdminPassword = superAdminPassword;
  }

  public String getAdminPassword() {
    return adminPassword;
  }

  public void setAdminPassword(String adminPassword) {
    this.adminPassword = adminPassword;
  }
}
