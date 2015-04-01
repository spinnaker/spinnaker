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
import com.google.api.services.compute.model.InstanceAggregatedList
import com.netflix.spinnaker.oort.gce.model.GoogleInstance
import com.netflix.spinnaker.oort.gce.model.GoogleServerGroup
import org.apache.log4j.Logger

class InstanceAggregatedListCallback<InstanceAggregatedList> extends JsonBatchCallback<InstanceAggregatedList> {
  protected static final Logger log = Logger.getLogger(this)

  private static final String GOOGLE_INSTANCE_TYPE = "gce"

  private Map<String, GoogleServerGroup> instanceNameToGoogleServerGroupMap
  private List<GoogleInstance> standaloneInstanceList

  public InstanceAggregatedListCallback(Map<String, GoogleServerGroup> instanceNameToGoogleServerGroupMap,
                                        List<GoogleInstance> standaloneInstanceList) {
    this.instanceNameToGoogleServerGroupMap = instanceNameToGoogleServerGroupMap
    this.standaloneInstanceList = standaloneInstanceList
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
          boolean instanceIsHealthy = instance.status == "RUNNING"

          def googleInstance = new GoogleInstance(instance.name)

          // Set attributes that deck requires to render instance.
          googleInstance.setProperty("isHealthy", instanceIsHealthy)
          googleInstance.setProperty("instanceId", instance.name)
          googleInstance.setProperty("instanceType", Utils.getLocalName(instance.machineType))
          googleInstance.setProperty("providerType", GOOGLE_INSTANCE_TYPE)
          googleInstance.setProperty("launchTime", instanceTimestamp)
          googleInstance.setProperty("placement", [availabilityZone: localZoneName])
          googleInstance.setProperty("health", [[type : "GCE",
                                                 state: instanceIsHealthy ? "Up" : "Down"]])

          // Set all google-provided attributes for use by non-deck callers.
          googleInstance.putAll(instance)

          def googleServerGroup = instanceNameToGoogleServerGroupMap[instance.name]

          if (googleServerGroup) {
            // Set serverGroup so we can easily determine in deck if an instance is contained within a server group.
            googleInstance.setProperty("serverGroup", googleServerGroup.name)

            googleServerGroup.instances << googleInstance
          } else {
            standaloneInstanceList << googleInstance
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
