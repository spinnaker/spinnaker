package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import lombok.Data;

import java.time.ZonedDateTime;
import java.util.Map;

@Data
public class Application {
  private String name;
  private String guid;
  private String state;
  private ZonedDateTime createdAt;
  private Map<String, ToOneRelationship> relationships;
}
