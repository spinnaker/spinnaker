package com.netflix.spinnaker.clouddriver.google.provider.agent.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Helper class for locally resolving queued backend service group health requests.
 */
@Data
@EqualsAndHashCode
@AllArgsConstructor
@ToString
public class GroupHealthRequest {
  private String project;
  private String backendServiceName;
  private String resourceGroup;
}
