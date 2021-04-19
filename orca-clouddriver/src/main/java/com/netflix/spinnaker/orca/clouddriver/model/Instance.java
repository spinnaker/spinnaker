package com.netflix.spinnaker.orca.clouddriver.model;

import java.util.List;
import lombok.Data;

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
}
