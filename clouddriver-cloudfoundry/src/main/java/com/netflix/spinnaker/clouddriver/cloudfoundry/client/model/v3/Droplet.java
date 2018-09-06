package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import lombok.Data;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;

@Data
public class Droplet {
  private String guid;
  private ZonedDateTime createdAt;
  private String stack;
  private String state;
  private Map<String, Link> links;
  private Collection<Buildpack> buildpacks;
}
