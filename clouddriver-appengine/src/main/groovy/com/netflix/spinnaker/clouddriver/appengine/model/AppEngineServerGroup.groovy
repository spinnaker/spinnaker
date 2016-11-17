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

import com.google.api.services.appengine.v1.model.Version
import com.netflix.spinnaker.clouddriver.appengine.AppEngineCloudProvider
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

@CompileStatic
@EqualsAndHashCode(includes = ["name", "account"])
class AppEngineServerGroup implements ServerGroup, Serializable {
  String name
  String type = AppEngineCloudProvider.ID
  String account
  String region
  Set<String> zones = []
  Set<AppEngineInstance> instances
  Set<String> loadBalancers = []
  Long createdTime
  Map<String, Object> launchConfig = [:]
  Set<String> securityGroups = []
  Boolean disabled = true

  AppEngineServerGroup() {}

  AppEngineServerGroup(Version version, String account, String region, String loadBalancerName, Boolean isDisabled) {
    this.account = account
    this.region = region
    this.name = version.getId()
    this.loadBalancers = [loadBalancerName] as Set
    this.createdTime = AppEngineModelUtil.translateTime(version.getCreateTime())
    this.disabled = isDisabled
  }

  @Override
  ServerGroup.InstanceCounts getInstanceCounts() {
    new ServerGroup.InstanceCounts(
      down: 0,
      outOfService: 0,
      up: (Integer) instances?.count { it.healthState == HealthState.Up } ?: 0,
      starting: 0,
      unknown: 0,
      total: (Integer) instances?.size(),
    )
  }

  @Override
  ServerGroup.Capacity getCapacity() {
    def instanceCount = instances?.size()
    new ServerGroup.Capacity(min: instanceCount, max: instanceCount, desired: instanceCount)
  }

  @Override
  ServerGroup.ImageSummary getImageSummary() {
    null
  }

  @Override
  ServerGroup.ImagesSummary getImagesSummary() {
    null
  }

  @Override
  Boolean isDisabled() {
    disabled
  }
}
