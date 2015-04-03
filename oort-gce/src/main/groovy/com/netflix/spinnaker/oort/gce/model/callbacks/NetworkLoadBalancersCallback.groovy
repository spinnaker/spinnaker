/*
 * Copyright 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.oort.gce.model.callbacks

import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.ForwardingRuleAggregatedList
import com.google.api.services.compute.model.HealthStatus
import com.netflix.spinnaker.oort.gce.model.GoogleLoadBalancer
import org.apache.log4j.Logger

class NetworkLoadBalancersCallback<ForwardingRuleAggregatedList> extends JsonBatchCallback<ForwardingRuleAggregatedList> {
  protected static final Logger log = Logger.getLogger(this)

  private Map<String, List<GoogleLoadBalancer>> networkLoadBalancerMap
  private Map<String, Map<String, List<HealthStatus>>> instanceNameToLoadBalancerHealthStatusMap
  private String project
  private Compute compute
  private BatchRequest targetPoolBatch
  private BatchRequest httpHealthCheckBatch

  public NetworkLoadBalancersCallback(Map<String, List<GoogleLoadBalancer>> networkLoadBalancerMap,
                                      Map<String, Map<String, List<HealthStatus>>> instanceNameToLoadBalancerHealthStatusMap,
                                      String project,
                                      Compute compute,
                                      BatchRequest targetPoolBatch,
                                      BatchRequest httpHealthCheckBatch) {
    this.networkLoadBalancerMap = networkLoadBalancerMap
    this.instanceNameToLoadBalancerHealthStatusMap = instanceNameToLoadBalancerHealthStatusMap
    this.project = project
    this.compute = compute
    this.targetPoolBatch = targetPoolBatch
    this.httpHealthCheckBatch = httpHealthCheckBatch
  }

  @Override
  void onSuccess(ForwardingRuleAggregatedList forwardingRuleAggregatedList, HttpHeaders responseHeaders) throws IOException {
    forwardingRuleAggregatedList?.items?.each { scope, forwardingRulesScopedList ->
      // This can return a scope 'global' as well.
      if (scope.startsWith("regions/")) {
        def forwardingRules = forwardingRulesScopedList?.forwardingRules

        if (forwardingRules) {
          // Strip off 'regions/' prefix.
          def region = scope.substring(8)

          // Network load balancer lists are keyed (at this level) by region.
          if (!networkLoadBalancerMap[region]) {
            networkLoadBalancerMap[region] = new ArrayList<String>()
          }

          forwardingRules?.each { forwardingRule ->
            def googleLoadBalancer = new GoogleLoadBalancer(forwardingRule.name, region)

            networkLoadBalancerMap[region] << googleLoadBalancer

            googleLoadBalancer.setProperty("createdTime", Utils.getTimeFromTimestamp(forwardingRule.creationTimestamp))

            if (forwardingRule.target) {
              def localTargetPoolName = Utils.getLocalName(forwardingRule.target)
              def targetPoolCallback = new TargetPoolCallback(googleLoadBalancer,
                                                              forwardingRule.name,
                                                              instanceNameToLoadBalancerHealthStatusMap,
                                                              project,
                                                              compute,
                                                              httpHealthCheckBatch)

              compute.targetPools().get(project, region, localTargetPoolName).queue(targetPoolBatch, targetPoolCallback)
            }

            googleLoadBalancer.setProperty("ipAddress", forwardingRule.IPAddress)
            googleLoadBalancer.setProperty("ipProtocol", forwardingRule.IPProtocol)
            googleLoadBalancer.setProperty("portRange", forwardingRule.portRange)
          }
        }
      }
    }
  }

  @Override
  void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
    log.error e.getMessage()
  }
}