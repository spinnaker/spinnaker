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
import com.google.api.services.compute.model.HealthStatus
import com.google.api.services.compute.model.InstanceReference
import com.google.api.services.compute.model.TargetPool
import com.netflix.spinnaker.oort.gce.model.GoogleLoadBalancer
import org.apache.log4j.Logger

class TargetPoolCallback<TargetPool> extends JsonBatchCallback<TargetPool> {
  protected static final Logger log = Logger.getLogger(this)

  private GoogleLoadBalancer googleLoadBalancer
  private String forwardingRuleName
  private Map<String, Map<String, List<HealthStatus>>> instanceNameToLoadBalancerHealthStatusMap
  private String project
  private Compute compute
  private BatchRequest httpHealthCheckBatch

  public TargetPoolCallback(GoogleLoadBalancer googleLoadBalancer,
                            String forwardingRuleName,
                            Map<String, Map<String, List<HealthStatus>>> instanceNameToLoadBalancerHealthStatusMap,
                            String project,
                            Compute compute,
                            BatchRequest httpHealthCheckBatch) {
    this.googleLoadBalancer = googleLoadBalancer
    this.forwardingRuleName = forwardingRuleName
    this.instanceNameToLoadBalancerHealthStatusMap = instanceNameToLoadBalancerHealthStatusMap
    this.project = project
    this.compute = compute
    this.httpHealthCheckBatch = httpHealthCheckBatch
  }

  @Override
  void onSuccess(TargetPool targetPool, HttpHeaders responseHeaders) throws IOException {
    def region = Utils.getLocalName(targetPool.region)
    def targetPoolName = targetPool.name

    targetPool?.instances?.each { instanceUrl ->
      def instanceReference = new InstanceReference(instance: instanceUrl)
      def targetPoolInstanceHealthCallback =
        new TargetPoolInstanceHealthCallback(forwardingRuleName,
                                             Utils.getLocalName(instanceUrl),
                                             instanceNameToLoadBalancerHealthStatusMap,
                                             (boolean)targetPool?.healthChecks)

      compute.targetPools().getHealth(project,
                                      region,
                                      targetPoolName,
                                      instanceReference).queue(httpHealthCheckBatch, targetPoolInstanceHealthCallback)
    }

    // TODO(duftler): Figure out how to return multiple health checks associated with 1 load balancer.
    targetPool?.healthChecks?.each { def healthCheckUrl ->
      def localHealthCheckName = Utils.getLocalName(healthCheckUrl)
      def httpHealthCheckCallback = new HttpHealthCheckCallback(googleLoadBalancer, project, compute)

      compute.httpHealthChecks().get(project, localHealthCheckName).queue(httpHealthCheckBatch, httpHealthCheckCallback)
    }
  }

  @Override
  void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
    log.error e.getMessage()
  }
}