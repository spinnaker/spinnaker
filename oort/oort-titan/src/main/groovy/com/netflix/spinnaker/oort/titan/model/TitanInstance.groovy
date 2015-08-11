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

package com.netflix.spinnaker.oort.titan.model

import com.netflix.spinnaker.oort.model.HealthState
import com.netflix.spinnaker.oort.model.Instance
import com.netflix.titanclient.model.Task
import com.netflix.titanclient.model.TaskState

class TitanInstance implements Instance {

  private List<Map<String, String>> health  // TODO
  private boolean isHealthy

  private String id
  private String jobId
  private TaskState state
  private String applicationName
  private int cpu
  private int memory
  private int disk
  private Map<Integer, Integer> ports
  private Map env
  private String version
  private String entryPoint
  private Long submittedAt
  private Long finishedAt
  private String host

  // Not in Titan API response
  private String imageName
  private String imageVersion
  private String zone

  // Not in Titan API response, but used by clouddriver
  private String account
  private String region
  private String subnetId
  private String jobName
  private String application

  TitanInstance(Task task) {
    id = task.id
    jobId = task.jobId
    state = task.state
    applicationName = task.applicationName
    cpu = task.cpu
    memory = task.memory
    disk = task.disk
    ports = task.ports
    env = task.env
    version = task.version
    entryPoint = task.entryPoint
    submittedAt = task.submittedAt ? task.submittedAt.time : null
    finishedAt = task.finishedAt ? task.finishedAt.time : null
    host = task.host

    imageName = task.imageName
    imageVersion = task.imageVersion

    account = task.account
    region = task.region
    zone = task.zone
    subnetId = task.subnetId
    jobName = task.jobName
    application = task.application
  }

  @Override
  String getName() {
    id
  }

  boolean isHealthy() {
    isHealthy
  }

  @Override
  List<Map<String, String>> getHealth() {
    health
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
    zone  // TODO
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

  void setHealth(List<Map<String, String>> health) {
    this.health = health
  }

  String getId() {
    return id
  }

  void setId(String id) {
    this.id = id
  }

  boolean getIsHealthy() {
    return isHealthy
  }

  void setIsHealthy(boolean isHealthy) {
    this.isHealthy = isHealthy
  }

  String getJobId() {
    return jobId
  }

  void setJobId(String jobId) {
    this.jobId = jobId
  }

  String getState() {
    return state
  }

  void setState(String state) {
    this.state = state
  }

  String getImageName() {
    return imageName
  }

  void setImageName(String imageName) {
    this.imageName = imageName
  }

  String getImageVersion() {
    return imageVersion
  }

  void setImageVersion(String imageVersion) {
    this.imageVersion = imageVersion
  }

  String getEntryPoint() {
    return entryPoint
  }

  void setEntryPoint(String entryPoint) {
    this.entryPoint = entryPoint
  }

  int getCpu() {
    return cpu
  }

  void setCpu(int cpu) {
    this.cpu = cpu
  }

  int getMemory() {
    return memory
  }

  void setMemory(int memory) {
    this.memory = memory
  }

  int getDisk() {
    return disk
  }

  void setDisk(int disk) {
    this.disk = disk
  }

  Map<Integer, Integer> getPorts() {
    return ports
  }

  void setPorts(Map<Integer, Integer> ports) {
    this.ports = ports
  }

  Map getEnv() {
    return env
  }

  void setEnv(Map env) {
    this.env = env
  }

  String getHost() {
    return host
  }

  void setHost(String host) {
    this.host = host
  }

  String getRegion() {
    return region
  }

  void setRegion(String region) {
    this.region = region
  }

  void setZone(String zone) {
    this.zone = zone
  }

  Long getSubmittedAt() {
    return submittedAt
  }

  void setSubmittedAt(Long submittedAt) {
    this.submittedAt = submittedAt
  }

  Long getFinishedAt() {
    return finishedAt
  }

  void setFinishedAt(Long finishedAt) {
    this.finishedAt = finishedAt
  }

  @Override
  boolean equals(Object o) {
    o instanceof TitanInstance ? o.name.equals(name) : false
  }

  @Override
  int hashCode() {
    return name.hashCode()
  }
}
