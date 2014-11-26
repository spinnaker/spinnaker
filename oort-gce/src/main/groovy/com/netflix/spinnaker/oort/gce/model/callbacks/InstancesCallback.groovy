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
import com.google.api.services.compute.model.Instance
import com.netflix.spinnaker.oort.gce.model.GoogleInstance
import com.netflix.spinnaker.oort.gce.model.GoogleServerGroup
import org.apache.log4j.Logger

class InstancesCallback<Instance> extends JsonBatchCallback<Instance> {
  protected static final Logger log = Logger.getLogger(this)

  private static final String GOOGLE_INSTANCE_TYPE = "gce"

  private String localZoneName
  private GoogleServerGroup googleServerGroup

  public InstancesCallback(String localZoneName, GoogleServerGroup googleServerGroup) {
    this.localZoneName = localZoneName
    this.googleServerGroup = googleServerGroup
  }

  @Override
  void onSuccess(Instance instance, HttpHeaders responseHeaders) throws IOException {
    long instanceTimestamp = instance.creationTimestamp
                             ? Utils.getTimeFromTimestamp(instance.creationTimestamp)
                             : Long.MAX_VALUE
    boolean instanceIsHealthy = instance.status == "RUNNING"

    // Use earliest replica launchTime as createdTime of instance group for now.
    def launchConfig = googleServerGroup.getProperty("launchConfig")
    def earliestReplicaTimestamp = Math.min(launchConfig.createdTime, instanceTimestamp)
    launchConfig.createdTime = earliestReplicaTimestamp

    def googleInstance = new GoogleInstance(instance.name)
    googleInstance.setProperty("isHealthy", instanceIsHealthy)
    googleInstance.setProperty("instanceId", instance.name)
    googleInstance.setProperty("instanceType", Utils.getLocalName(instance.machineType))
    googleInstance.setProperty("providerType", GOOGLE_INSTANCE_TYPE)
    googleInstance.setProperty("launchTime", instanceTimestamp)
    googleInstance.setProperty("placement", [availabilityZone: localZoneName])
    googleInstance.setProperty("health", [[type : "GCE",
                                           state: instanceIsHealthy ? "Up" : "Down"]])
    googleServerGroup.instances << googleInstance
  }

  @Override
  void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
    log.error e.getMessage()
  }
}
