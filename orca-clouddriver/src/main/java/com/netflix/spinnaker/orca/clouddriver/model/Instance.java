package com.netflix.spinnaker.orca.clouddriver.model;

import java.util.List;
import lombok.Data;
import lombok.Value;

@Data
public class Instance {
  String instanceId;
  Long launchTime;
  String name;
  List<Health> health;
  HealthState healthState;
  String zone;
  String cloudProvider;
  String privateIpAddress;
  String publicDnsName;

  /**
   * Method to cut down on the instance data that is serialized to the context
   *
   * @return
   */
  public MinimalInstance minimalInstance() {
    return MinimalInstance.of(instanceId, launchTime, health, healthState, zone);
  }

  @Value(staticConstructor = "of")
  public static class MinimalInstance {
    String name;
    Long launchTime;
    List<Health> health;
    HealthState healthState;
    String zone;
  }
}
