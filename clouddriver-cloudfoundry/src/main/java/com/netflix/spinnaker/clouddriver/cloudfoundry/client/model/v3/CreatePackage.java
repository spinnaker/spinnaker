package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class CreatePackage {
  private final String type = "bits";
  private final Map<String, ToOneRelationship> relationships = new HashMap<>();

  public CreatePackage(String appId) {
    relationships.put("app", new ToOneRelationship(new Relationship(appId)));
  }
}
