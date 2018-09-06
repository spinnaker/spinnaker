package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2;

import lombok.Data;

import java.time.ZonedDateTime;

@Data
public class Resource<T> {
  private Metadata metadata;
  private T entity;

  @Data
  public static class Metadata {
    private String guid;
    private ZonedDateTime createdAt;
  }
}
