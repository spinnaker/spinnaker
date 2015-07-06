/*
 * Copyright 2015 Netflix, Inc.
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
import com.netflix.spinnaker.oort.model.ServerGroup
import com.netflix.titanclient.model.Job
/**
 * Equivalent of a Titan {@link com.netflix.titanclient.model.Job}
 * @author sthadeshwar
 */
class TitanServerGroup implements ServerGroup, Serializable {

  public static final String TYPE = "titan"

  private String id
  private String name
  private String imageName
  private String imageVersion
  private String entryPoint
  private int cpu
  private int memory
  private int disk
  private int[] ports
  private Map env
  private int retries
  private boolean restartOnSuccess
  private Date submittedAt

  private String account
  private String region
  private String subnetId
  private Map<String, Object> image = Collections.emptyMap()
  private Set<Instance> instances = Collections.emptySet()

  TitanServerGroup(Job job) {
    id = job.id
    name = job.name
    imageName = job.imageName
    imageVersion = job.imageVersion
    entryPoint = job.entryPoint
    cpu = job.cpu
    memory = job.memory
    disk = job.disk
    ports = job.ports
    env = job.env
    retries = job.retries
    submittedAt = job.submittedAt
    account = job.account      // TODO
    region = job.region      // TODO
    subnetId = job.subnetId      // TODO
    instances = job.tasks.findAll { it != null }.collect { new TitanInstance(it) } as Set
  }

  @Override
  String getName() {
    name
  }

  @Override
  String getType() {
    TYPE
  }

  @Override
  String getRegion() {
    region // TODO
  }

  @Override
  Boolean isDisabled() {
    return false  // TODO
  }

  @Override
  Long getCreatedTime() {
    return submittedAt ? submittedAt.time : null
  }

  @Override
  Set<String> getLoadBalancers() {
    Collections.emptySet()
  }

  @Override
  Set<String> getSecurityGroups() {
    Collections.emptySet()
  }

  @Override
  Set<String> getZones() {
    Collections.emptySet()  // TODO
  }

  @Override
  Set<Instance> getInstances() {
    instances
  }

  @Override
  Map<String, Object> getLaunchConfig() {
    [:]
  }

  @Override
  ServerGroup.InstanceCounts getInstanceCounts() {
    Set<Instance> instances = getInstances()
    new ServerGroup.InstanceCounts(
      total: instances.size(),
      up: filterInstancesByHealthState(instances, HealthState.Up)?.size() ?: 0,
      down: filterInstancesByHealthState(instances, HealthState.Down)?.size() ?: 0,
      unknown: filterInstancesByHealthState(instances, HealthState.Unknown)?.size() ?: 0,
      starting: filterInstancesByHealthState(instances, HealthState.Starting)?.size() ?: 0,
      outOfService: filterInstancesByHealthState(instances, HealthState.OutOfService)?.size() ?: 0)
  }

  static Set filterInstancesByHealthState(Set instances, HealthState healthState) {
    instances.findAll { Instance it -> it.getHealthState() == healthState }
  }

  String getId() {
    return id
  }

  void setId(String id) {
    this.id = id
  }

  void setName(String name) {
    this.name = name
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

  int[] getPorts() {
    return ports
  }

  void setPorts(int[] ports) {
    this.ports = ports
  }

  Map getEnv() {
    return env
  }

  void setEnv(Map env) {
    this.env = env
  }

  int getRetries() {
    return retries
  }

  void setRetries(int retries) {
    this.retries = retries
  }

  boolean getRestartOnSuccess() {
    return restartOnSuccess
  }

  void setRestartOnSuccess(boolean restartOnSuccess) {
    this.restartOnSuccess = restartOnSuccess
  }

  Date getSubmittedAt() {
    return submittedAt
  }

  void setSubmittedAt(Date submittedAt) {
    this.submittedAt = submittedAt
  }

  void setRegion(String region) {
    this.region = region
  }

  Map<String, Object> getImage() {
    return image
  }

  void setImage(Map<String, Object> image) {
    this.image = image
  }

  void setInstances(Set<Instance> instances) {
    this.instances = instances
  }

  String getAccount() {
    return account
  }

  void setAccount(String account) {
    this.account = account
  }
}
