package com.netflix.spinnaker.clouddriver.titus.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ResizeJobRequest {

  private String user;
  private String jobId;
  private int instancesDesired;
  private int instancesMax;
  private int instancesMin;

  public ResizeJobRequest() {
  }

  public String getUser() {
    return user;
  }

  public ResizeJobRequest withUser(String user) {
    this.user = user;
    return this;
  }

  public String getJobId() {
    return jobId;
  }

  public ResizeJobRequest withJobId(String jobId) {
    this.jobId = jobId;
    return this;
  }

  public int getInstancesDesired() {
    return instancesDesired;
  }

  public ResizeJobRequest withInstancesDesired(int instancesDesired) {
    this.instancesDesired = instancesDesired;
    return this;
  }

  public int getInstancesMax() {
    return instancesMax;
  }

  public ResizeJobRequest withInstancesMax(int instancesMax) {
    this.instancesMax = instancesMax;
    return this;
  }

  public int getInstancesMin() {
    return instancesMin;
  }

  public ResizeJobRequest withInstancesMin(int instancesMin) {
    this.instancesMin = instancesMin;
    return this;
  }

}
