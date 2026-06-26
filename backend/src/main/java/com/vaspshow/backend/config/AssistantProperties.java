package com.vaspshow.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vasp.assistant")
public class AssistantProperties {

  private boolean enabled = true;
  private String baseUrl = "http://127.0.0.1:11434";
  private String model = "qwen3:8b";
  private String keepAlive = "30m";
  private int connectTimeoutSeconds = 5;
  private int requestTimeoutSeconds = 180;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getKeepAlive() {
    return keepAlive;
  }

  public void setKeepAlive(String keepAlive) {
    this.keepAlive = keepAlive;
  }

  public int getConnectTimeoutSeconds() {
    return connectTimeoutSeconds;
  }

  public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
    this.connectTimeoutSeconds = connectTimeoutSeconds;
  }

  public int getRequestTimeoutSeconds() {
    return requestTimeoutSeconds;
  }

  public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
    this.requestTimeoutSeconds = requestTimeoutSeconds;
  }
}
