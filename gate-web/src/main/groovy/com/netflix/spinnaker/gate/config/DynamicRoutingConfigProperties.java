package com.netflix.spinnaker.gate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@ConfigurationProperties(prefix = "dynamic-routing")
public class DynamicRoutingConfigProperties {
  public static final String ENABLED_PROPERTY = "dynamic-routing.enabled";
  public Boolean enabled;

  @NestedConfigurationProperty public ClouddriverConfigProperties clouddriver;

  @Data
  public static class ClouddriverConfigProperties {
    public static final String ENABLED_PROPERTY = "dynamic-routing.clouddriver.enabled";
    public boolean enabled;
  }
}
