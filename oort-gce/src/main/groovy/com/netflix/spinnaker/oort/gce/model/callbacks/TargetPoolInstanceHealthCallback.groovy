/*
 * Copyright 2015 Google, Inc.
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

import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.model.HealthStatus
import com.google.api.services.compute.model.TargetPoolInstanceHealth
import org.apache.log4j.Logger

class TargetPoolInstanceHealthCallback<TargetPoolInstanceHealth> extends JsonBatchCallback<TargetPoolInstanceHealth> {
  protected static final Logger log = Logger.getLogger(this)

  private String forwardingRuleName
  private String instanceName
  private Map<String, Map<String, List<HealthStatus>>> instanceNameToLoadBalancerHealthStatusMap
  private boolean hasHttpHealthCheck

  public TargetPoolInstanceHealthCallback(String forwardingRuleName,
                                          String instanceName,
                                          Map<String, List<HealthStatus>> instanceNameToLoadBalancerHealthStatusMap,
                                          boolean hasHttpHealthCheck) {
    this.forwardingRuleName = forwardingRuleName
    this.instanceName = instanceName
    this.instanceNameToLoadBalancerHealthStatusMap = instanceNameToLoadBalancerHealthStatusMap
    this.hasHttpHealthCheck = hasHttpHealthCheck
  }

  @Override
  void onSuccess(TargetPoolInstanceHealth targetPoolInstanceHealth, HttpHeaders responseHeaders) throws IOException {
    if (!instanceNameToLoadBalancerHealthStatusMap[instanceName]) {
      instanceNameToLoadBalancerHealthStatusMap[instanceName] = new HashMap<String, List<HealthStatus>>()
    }

    if (!instanceNameToLoadBalancerHealthStatusMap[instanceName][forwardingRuleName]) {
      instanceNameToLoadBalancerHealthStatusMap[instanceName][forwardingRuleName] = new ArrayList<HealthStatus>()
    }

    List<HealthStatus> healthStatusList = targetPoolInstanceHealth.healthStatus

    healthStatusList.each { HealthStatus healthStatus ->
      healthStatus.hasHttpHealthCheck = hasHttpHealthCheck
    }

    instanceNameToLoadBalancerHealthStatusMap[instanceName][forwardingRuleName].addAll(healthStatusList)
  }

  @Override
  void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
    log.error e.getMessage()
  }
}