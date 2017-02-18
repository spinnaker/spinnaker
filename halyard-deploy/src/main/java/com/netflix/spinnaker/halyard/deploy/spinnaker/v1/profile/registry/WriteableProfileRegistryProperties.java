package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.registry;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("spinnaker.config.input.gcs")
public class WriteableProfileRegistryProperties {
  private String jsonPath = "";
  private String project = "spinnaker-marketplace";
}
