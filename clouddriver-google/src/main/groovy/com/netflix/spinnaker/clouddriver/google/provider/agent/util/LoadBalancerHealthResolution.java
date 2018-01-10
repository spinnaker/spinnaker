package com.netflix.spinnaker.clouddriver.google.provider.agent.util;

import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancer;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Helper class to resolve locally cached backend service get health call results.
 */
@Data
@AllArgsConstructor
public class LoadBalancerHealthResolution {
  private GoogleLoadBalancer googleLoadBalancer;
  private String target;
}
