package com.netflix.spinnaker.igor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "helm-oci-docker-registry")
public class HelmOciDockerRegistryProperties {
  private boolean enabled;
  private Integer itemUpperThreshold;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Integer getItemUpperThreshold() {
    return itemUpperThreshold;
  }

  public void setItemUpperThreshold(Integer itemUpperThreshold) {
    this.itemUpperThreshold = itemUpperThreshold;
  }
}
