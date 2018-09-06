package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import lombok.Data;

import java.time.ZonedDateTime;
import java.util.Map;

@Data
public class Package {
  private String guid;
  private State state;
  private String type;
  private ZonedDateTime createdAt;
  private PackageData data;
  private Map<String, Link> links;

  public enum State {
    AWAITING_UPLOAD, PROCESSING_UPLOAD, READY, FAILED, COPYING, EXPIRED
  }
}
