/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.titus.model

import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.titanclient.model.Task
import com.netflix.titanclient.model.TaskState

class TitanInstance implements Instance {

  String application
  String id
  String jobId
  String jobName
  Map image = [:]
  TaskState state
  Map env
  Long submittedAt
  Long finishedAt
  List<Map<String, String>> health
  TitanInstanceResources resources = new TitanInstanceResources()
  TitanInstancePlacement placement = new TitanInstancePlacement()

  TitanInstance(Task task) {
    id = task.id
    jobId = task.jobId
    jobName = task.jobName
    application = task.application
    image << [dockerImageName: task.dockerImageName]
    image << [dockerImageVersion: task.dockerImageVersion]
    state = task.state

    placement.account = task.account
    placement.region = task.region
    placement.subnetId = task.subnetId
    placement.zone = task.zone
    placement.host = task.host

    resources.cpu = task.cpu
    resources.memory = task.memory
    resources.disk = task.disk
    resources.ports = task.ports

    env = task.env
    submittedAt = task.submittedAt ? task.submittedAt.time : null
    finishedAt = task.finishedAt ? task.finishedAt.time : null
  }

  @Override
  String getName() {
    id
  }

  @Override
  HealthState getHealthState() {
    List<Map<String, String>> healthList = getHealth()
    someUpRemainingUnknown(healthList) ? HealthState.Up :
      anyStarting(healthList) ? HealthState.Starting :
        anyDown(healthList) ? HealthState.Down :
          anyOutOfService(healthList) ? HealthState.OutOfService : HealthState.Unknown
  }

  @Override
  Long getLaunchTime() {
    submittedAt
  }

  @Override
  String getZone() {
    placement.zone
  }

  @Override
  List<Map<String, String>> getHealth() {
    health
  }

  boolean getIsHealthy() {
    health ? health.any { it.state == 'Up' } && health.every { it.state == 'Up' || it.state == 'Unknown' } : false
  }

  private static boolean anyDown(List<Map<String, String>> healthList) {
    healthList.any { it.state == HealthState.Down.toString()}
  }

  private static boolean someUpRemainingUnknown(List<Map<String, String>> healthList) {
    List<Map<String, String>> knownHealthList = healthList.findAll{ it.state != HealthState.Unknown.toString() }
    knownHealthList ? knownHealthList.every { it.state == HealthState.Up.toString() } : false
  }

  private static boolean anyStarting(List<Map<String, String>> healthList) {
    healthList.any { it.state == HealthState.Starting.toString()}
  }

  private static boolean anyOutOfService(List<Map<String, String>> healthList) {
    healthList.any { it.state == HealthState.OutOfService.toString()}
  }

  @Override
  boolean equals(Object o) {
    o instanceof TitanInstance ? o.id.equals(id) : false
  }

  @Override
  int hashCode() {
    return id.hashCode()
  }
}
