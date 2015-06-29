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

import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.model.HealthStatus
import com.google.api.services.compute.model.InstanceAggregatedList
import com.netflix.spinnaker.oort.gce.model.GoogleInstance
import com.netflix.spinnaker.oort.gce.model.GoogleServerGroup
import com.netflix.spinnaker.oort.model.HealthState
import org.apache.log4j.Logger

class InstanceAggregatedListCallback<InstanceAggregatedList> extends JsonBatchCallback<InstanceAggregatedList> {
  protected static final Logger log = Logger.getLogger(this)

  private static final String GOOGLE_INSTANCE_TYPE = "gce"

  private Map<String, GoogleServerGroup> instanceNameToGoogleServerGroupMap
  private List<GoogleInstance> standaloneInstanceList
  private Map<String, Map<String, List<HealthStatus>>> instanceNameToLoadBalancerHealthStatusMap

  public InstanceAggregatedListCallback(Map<String, GoogleServerGroup> instanceNameToGoogleServerGroupMap,
                                        List<GoogleInstance> standaloneInstanceList,
                                        Map<String, Map<String, List<HealthStatus>>> instanceNameToLoadBalancerHealthStatusMap) {
    this.instanceNameToGoogleServerGroupMap = instanceNameToGoogleServerGroupMap
    this.standaloneInstanceList = standaloneInstanceList
    this.instanceNameToLoadBalancerHealthStatusMap = instanceNameToLoadBalancerHealthStatusMap
  }

  @Override
  void onSuccess(InstanceAggregatedList instanceAggregatedList, HttpHeaders responseHeaders) throws IOException {
    instanceAggregatedList.items.each { zone, instancesScopedList ->
      if (instancesScopedList.instances) {
        def localZoneName = Utils.getLocalName(zone)

        instancesScopedList.instances.each { instance ->
          long instanceTimestamp = instance.creationTimestamp
                                   ? Utils.getTimeFromTimestamp(instance.creationTimestamp)
                                   : Long.MAX_VALUE
          def googleInstance = new GoogleInstance(instance.name)

          // Set attributes that deck requires to render instance.
          googleInstance.setProperty("instanceId", instance.name)
          googleInstance.setProperty("instanceType", Utils.getLocalName(instance.machineType))
          googleInstance.setProperty("providerType", GOOGLE_INSTANCE_TYPE)
          googleInstance.setProperty("launchTime", instanceTimestamp)
          googleInstance.setProperty("placement", [availabilityZone: localZoneName])

          def healthStates = [buildGCEHealthState(instance.status)];

          buildAndAddLoadBalancerStateIfNecessary(
            instance.name, healthStates, instanceNameToLoadBalancerHealthStatusMap)

          googleInstance.setProperty("health", healthStates)
          googleInstance.setProperty("isHealthy", calculateIsHealthy(googleInstance))

          // Set all google-provided attributes for use by non-deck callers.
          googleInstance.putAll(instance)

          def googleServerGroup = instanceNameToGoogleServerGroupMap[instance.name]

          if (googleServerGroup) {
            // Set serverGroup so we can easily determine in deck if an instance is contained within a server group.
            googleInstance.setProperty("serverGroup", googleServerGroup.name)

            googleServerGroup.instances << googleInstance
          } else if (standaloneInstanceList != null) {
            standaloneInstanceList << googleInstance
          }
        }
      }
    }
  }

  static Map buildGCEHealthState(String instanceStatus) {
    [
      type : "GCE",
      state: deriveInstanceGCEHealthState(instanceStatus)
    ]
  }

  // Map GCE-returned instance status to spinnaker health state.
  static HealthState deriveInstanceGCEHealthState(String instanceStatus) {
    instanceStatus == "PROVISIONING" ? HealthState.Starting :
      instanceStatus == "STAGING" ? HealthState.Starting :
        instanceStatus == "RUNNING" ? HealthState.Unknown :
          HealthState.Down
  }

  static void buildAndAddLoadBalancerStateIfNecessary(
    String instanceName,
    List<Map> healthStates,
    Map<String, Map<String, List<HealthStatus>>> instanceNameToLoadBalancerHealthStatusMap) {

    if (instanceNameToLoadBalancerHealthStatusMap) {
      def individualInstanceLoadBalancerStates =
        deriveIndividualInstanceLoadBalancerStates(instanceName,
          instanceNameToLoadBalancerHealthStatusMap[instanceName])

      if (individualInstanceLoadBalancerStates) {
        healthStates << [
          type         : "LoadBalancer",
          state        : deriveInstanceLoadBalancerHealthState(individualInstanceLoadBalancerStates),
          loadBalancers: individualInstanceLoadBalancerStates,
          instanceId   : instanceName
        ];
      }
    }
  }

  // Roll up individual instance load balancer states into one health state.
  static HealthState deriveInstanceLoadBalancerHealthState(List<Map> instanceLoadBalancerStates) {
    instanceLoadBalancerStates.any { it.state == "OutOfService" } ? HealthState.Down : HealthState.Up
  }

  // Map GCE-returned load balancer health status to spinnaker health state and description.
  static List<Map> deriveIndividualInstanceLoadBalancerStates(
    String instanceName,
    Map<String, List<HealthStatus>> loadBalancerNameToHealthStatusMap) {
    def loadBalancerStates = []

    loadBalancerNameToHealthStatusMap?.each { loadBalancerName, healthStatusList ->
      if (healthStatusList) {
        def healthStatus = healthStatusList[0]

        // TODO(duftler): Reconsider whether we should artificially mark instances lacking a health check as
        // 'InService'. This may be a better alternative than having them listed as 'OutOfService' but still
        // receiving traffic (even though the health state returned from GCE would still be 'UNHEALTHY').
        def individualInstanceLoadBalancerState = [
          loadBalancerName: loadBalancerName,
          instanceId      : instanceName,
          state           : healthStatus.healthState == "UNHEALTHY" ? "OutOfService" : "InService"
        ]

        if (individualInstanceLoadBalancerState.state == "OutOfService") {
          individualInstanceLoadBalancerState.description =
            healthStatus.hasHttpHealthCheck
              ? "Instance has failed at least the Unhealthy Threshold number of health checks consecutively."
              : "No http health check defined. Traffic will still be sent to this instance.";

        }

        loadBalancerStates << individualInstanceLoadBalancerState
      }
    }

    return loadBalancerStates
  }

  // To be considered healthy, there must be at least one Up vote and all other votes must be Up or Unknown.
  public static boolean calculateIsHealthy(GoogleInstance googleInstance) {
    googleInstance.health.any {
      it.state == HealthState.Up
    } && googleInstance.health.every {
      it.state == HealthState.Up || it.state == HealthState.Unknown
    }
  }

  @Override
  void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
    log.error e.getMessage()
  }
}
