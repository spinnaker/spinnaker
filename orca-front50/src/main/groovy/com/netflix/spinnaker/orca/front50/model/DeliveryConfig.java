package com.netflix.spinnaker.orca.front50.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class DeliveryConfig {
  private String id;
  private String application;
  private Long lastModified;
  private Long createTs;
  private String lastModifiedBy;
  private List<Map<String,Object>> deliveryArtifacts;
  private List<Map<String,Object>> deliveryEnvironments;

  private Map<String,Object> details = new HashMap<>();

  @JsonAnyGetter
  Map<String,Object> details() {
    return details;
  }

  @JsonAnySetter
  void set(String name, Object value) {
    details.put(name, value);
  }
}
