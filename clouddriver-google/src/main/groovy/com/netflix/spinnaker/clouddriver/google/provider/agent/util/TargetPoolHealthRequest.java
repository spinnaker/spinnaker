package com.netflix.spinnaker.clouddriver.google.provider.agent.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Helper class for locally resolving queued target pool health requests.
 */
@Data
@EqualsAndHashCode
@AllArgsConstructor
@ToString
public class TargetPoolHealthRequest {
  private String project;
  private String region;
  private String targetPoolName;
  private String instance;
}
