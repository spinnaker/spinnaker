package com.netflix.spinnaker.clouddriver.titus.client.model;

public class MigrationPolicy {
  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  private String type;
}
