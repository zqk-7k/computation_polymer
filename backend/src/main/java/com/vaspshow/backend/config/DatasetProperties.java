package com.vaspshow.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vasp.datasets")
public class DatasetProperties {

  private String aniPath = "documents/data/ani_gdb_s03.h5";
  private String displayDbPath = "documents/data/frontend_template_data";
  private boolean displayWritable = false;

  public String getAniPath() {
    return aniPath;
  }

  public void setAniPath(String aniPath) {
    this.aniPath = aniPath;
  }

  public String getDisplayDbPath() {
    return displayDbPath;
  }

  public void setDisplayDbPath(String displayDbPath) {
    this.displayDbPath = displayDbPath;
  }

  public boolean isDisplayWritable() {
    return displayWritable;
  }

  public void setDisplayWritable(boolean displayWritable) {
    this.displayWritable = displayWritable;
  }
}
