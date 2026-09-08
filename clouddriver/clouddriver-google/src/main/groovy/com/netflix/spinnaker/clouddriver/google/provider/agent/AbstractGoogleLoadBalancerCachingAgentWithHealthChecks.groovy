/*
 * Copyright 2025 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancer
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.GroupHealthRequest
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.LoadBalancerHealthResolution
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.transform.CompileStatic

/**
 * Base class for Google load balancer caching agents that use backend service group health checks.
 * Provides common health check caching infrastructure and instance key determination for global LBs.
 */
@CompileStatic
abstract class AbstractGoogleLoadBalancerCachingAgentWithHealthChecks extends AbstractGoogleLoadBalancerCachingAgent {

  /**
   * Local cache of BackendServiceGroupHealth or TargetPoolHealth keyed by backend service/target pool name.
   *
   * It turns out that the types in the GCE Batch callbacks aren't the actual Compute
   * types for some reason, which is why this map is String -> Object.
   */
  protected Map<String, Object> healthCache = [:]

  protected Set<GroupHealthRequest> queuedHealthRequests = new HashSet<>()
  protected Set<LoadBalancerHealthResolution> resolutions = new HashSet<>()

  AbstractGoogleLoadBalancerCachingAgentWithHealthChecks(String clouddriverUserAgentApplicationName,
                                                          GoogleNamedAccountCredentials credentials,
                                                          ObjectMapper objectMapper,
                                                          Registry registry,
                                                          String region) {
    super(clouddriverUserAgentApplicationName, credentials, objectMapper, registry, region)
  }

  /**
   * Reset health check caches at the beginning of each caching cycle.
   */
  protected void resetHealthCaches() {
    healthCache = [:]
    queuedHealthRequests = new HashSet<>()
    resolutions = new HashSet<>()
  }

  /**
   * Determine instance key for global load balancers.
   * Since global LBs don't have a region, we derive it from the instance zone.
   */
  @Override
  String determineInstanceKey(GoogleLoadBalancer loadBalancer, GoogleLoadBalancerHealth health) {
    def instanceZone = health.instanceZone
    def instanceRegion = credentials.regionFromZone(instanceZone)
    return Keys.getInstanceKey(accountName, instanceRegion, health.instanceName)
  }
}
