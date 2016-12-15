/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.model

import com.google.api.services.appengine.v1.model.Instance as AppEngineApiInstance
import com.google.api.services.appengine.v1.model.Service
import com.google.api.services.appengine.v1.model.Version
import com.netflix.spinnaker.clouddriver.appengine.AppEngineCloudProvider
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance

class AppEngineInstance implements Instance, Serializable {
  String name
  String id
  Long launchTime
  AppEngineInstanceStatus instanceStatus
  String zone
  String serverGroup
  List<String> loadBalancers
  HealthState healthState
  final String providerType = AppEngineCloudProvider.ID
  final String cloudProvider = AppEngineCloudProvider.ID
  String vmName
  String vmZoneName
  Integer requests
  Integer errors
  Float qps
  Integer averageLatency
  String memoryUsage
  String vmStatus
  String vmDebugEnabled

  AppEngineInstance() {}

  AppEngineInstance(AppEngineApiInstance instance, Version version, Service service) {
    this.healthState = AppEngineModelUtil.getInstanceHealthState(version, service)
    this.instanceStatus = instance.getAvailability() ?
      AppEngineInstanceStatus.valueOf(instance.getAvailability()) :
      null

    /*
    * The instance controller takes three coordinates to locate an instance: account, region, and instance name.
    * App Engine Flexible instances do not have unique ids, but do have unique vmNames, so we'll use vmName as instance name.
    * App Engine Standard instances have unique ids, but do not have vmNames, so we'll use id as instance name.
    * We'll keep a separate "id" property, which is the identifier the API needs for an instance delete operation.
    * */
    this.name = instance.getVmName() ?: instance.getId()
    this.id = instance.getId()

    this.launchTime = AppEngineModelUtil.translateTime(instance.getStartTime())
    this.vmName = instance.getVmName()
    this.vmZoneName = instance.getVmZoneName()
    this.requests = instance.getRequests()
    this.errors = instance.getErrors()
    this.qps = instance.getQps()
    this.averageLatency = instance.getAverageLatency()
    this.memoryUsage = instance.getMemoryUsage()
    this.vmStatus = instance.getVmStatus()
    this.vmDebugEnabled = instance.getVmDebugEnabled()
  }

  List<Map<String, String>> getHealth() {
    [['appengine': healthState.toString()]]
  }

  enum AppEngineInstanceStatus {
    /*
    * See https://cloud.google.com/appengine/docs/java/how-instances-are-managed
    * */
    DYNAMIC,
    RESIDENT,
    UNKNOWN
  }
}
