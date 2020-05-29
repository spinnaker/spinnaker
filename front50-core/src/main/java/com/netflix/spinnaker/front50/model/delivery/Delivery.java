package com.netflix.spinnaker.front50.model.delivery;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.netflix.spinnaker.front50.model.Timestamped;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Delivery implements Timestamped {
  private String id;
  private String application;
  private Long updateTs;
  private Long createTs;
  private String lastModifiedBy;
  private List<Map<String, Object>> deliveryArtifacts;
  private List<Map<String, Object>> deliveryEnvironments;
  private Map<String, Object> details = new HashMap<>();

  public Delivery() {}

  @Override
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getApplication() {
    return application;
  }

  public void setApplication(String application) {
    this.application = application;
  }

  @Override
  public Long getLastModified() {
    return updateTs;
  }

  @Override
  public void setLastModified(Long lastModified) {
    updateTs = lastModified;
  }

  @Override
  public String getLastModifiedBy() {
    return lastModifiedBy;
  }

  @Override
  public void setLastModifiedBy(String lastModifiedBy) {
    this.lastModifiedBy = lastModifiedBy;
  }

  public List<Map<String, Object>> getDeliveryArtifacts() {
    return deliveryArtifacts;
  }

  public void setArtifacts(List<Map<String, Object>> deliveryEnvironments) {
    this.deliveryArtifacts = deliveryEnvironments;
  }

  public List<Map<String, Object>> getDeliveryEnvironments() {
    return deliveryEnvironments;
  }

  public void setEnvironments(List<Map<String, Object>> deliveryEnvironments) {
    this.deliveryEnvironments = deliveryEnvironments;
  }

  public Long getCreateTs() {
    return createTs;
  }

  public void setCreateTs(Long createTs) {
    this.createTs = createTs;
  }

  @JsonAnyGetter
  Map<String, Object> details() {
    return details;
  }

  @JsonAnySetter
  void set(String name, Object value) {
    details.put(name, value);
  }
}
