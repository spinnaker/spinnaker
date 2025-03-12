package com.netflix.spinnaker.orca.clouddriver.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Data
public class Instance {
  public String instanceId;
  public Long launchTime;
  public String name;
  public List<Health> health;
  public HealthState healthState;
  public String zone;
  public String cloudProvider;
  public String privateIpAddress;
  public String publicDnsName;

  /**
   * Method to cut down on the instance data that is serialized to the context
   *
   * @return
   */
  public MinimalInstance minimalInstance() {
    return MinimalInstance.of(instanceId, launchTime, health, healthState, zone);
  }

  public InstanceInfo instanceInfo() {
    String hostName = publicDnsName;
    if (hostName == null
        || hostName
            .isEmpty()) { // some instances dont have a public address, fall back to the private ip
      hostName = privateIpAddress;
    }

    String healthCheckUrl =
        health.stream()
            .map(Health::getHealthCheckUrl)
            .filter(url -> url != null && !url.isEmpty())
            .findFirst()
            .orElse(null);

    return InstanceInfo.builder()
        .hostName(hostName)
        .healthCheckUrl(healthCheckUrl)
        .privateIpAddress(privateIpAddress)
        .build();
  }

  @Value(staticConstructor = "of")
  public static class MinimalInstance {
    String name;
    Long launchTime;
    List<Health> health;
    HealthState healthState;
    String zone;
  }

  @Data
  @Builder
  @JsonDeserialize(builder = InstanceInfo.InstanceInfoBuilder.class)
  public static class InstanceInfo {
    public String hostName;
    public String healthCheckUrl;
    public String privateIpAddress;

    @JsonPOJOBuilder(withPrefix = "")
    public static class InstanceInfoBuilder {}
  }
}
