package com.netflix.spinnaker.orca.clouddriver.model;

import lombok.Data;

@Data
public class Health {
  String type;
  HealthState state;
  String healthClass;
}
