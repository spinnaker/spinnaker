/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.client.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubmitJobRequest {
  public static class Constraint {
    enum ConstraintType {
      SOFT,
      HARD
    }

    public static final String UNIQUE_HOST = "UniqueHost";
    public static final String ZONE_BALANCE = "ZoneBalance";

    public static Constraint hard(String constraint) {
      return new Constraint(ConstraintType.HARD, constraint);
    }

    public static Constraint soft(String constraint) {
      return new Constraint(ConstraintType.SOFT, constraint);
    }

    private final ConstraintType constraintType;
    private final String constraint;

    public Constraint(ConstraintType constraintType, String constraint) {
      this.constraintType = constraintType;
      this.constraint = constraint;
    }

    public ConstraintType getConstraintType() {
      return constraintType;
    }

    public String getConstraint() {
      return constraint;
    }
  }

  private String credentials;
  private String jobType;
  private String application;
  private String jobName;
  private String dockerImageName;
  private String dockerImageVersion;
  private String dockerDigest;
  private String stack;
  private String detail;
  private String user;
  private String entryPoint;
  private String iamProfile;
  private String capacityGroup;
  private Boolean inService = true;
  private int instancesMin;
  private int instancesMax;
  private int instancesDesired;
  private int cpu;
  private int gpu;
  private int memory;
  private int disk;
  private int retries;
  private int runtimeLimitSecs;
  private int networkMbps;
  private Efs efs;
  private int[] ports;
  private Map<String, String> env;
  private boolean allocateIpAddress;
  private List<Constraint> constraints = new ArrayList<>();
  private Map<String, String> labels = new HashMap<String, String>();
  private Map<String, String> containerAttributes = new HashMap<String, String>();
  private List<String> securityGroups = null;
  private MigrationPolicy migrationPolicy = null;
  private DisruptionBudget disruptionBudget = null;

  public DisruptionBudget getDisruptionBudget() {
    return disruptionBudget;
  }

  public SubmitJobRequest withJobType(String jobType) {
    this.jobType = jobType;
    return this;
  }

  public SubmitJobRequest withJobName(String jobName) {
    this.jobName = jobName;
    return this;
  }

  public SubmitJobRequest withApplication(String application) {
    this.application = application;
    return this;
  }

  public SubmitJobRequest withDockerImageName(String dockerImageName) {
    this.dockerImageName = dockerImageName;
    return this;
  }

  public SubmitJobRequest withDockerImageVersion(String dockerImageVersion) {
    this.dockerImageVersion = dockerImageVersion;
    return this;
  }

  public SubmitJobRequest withDockerDigest(String dockerDigest) {
    this.dockerDigest = dockerDigest;
    return this;
  }

  public SubmitJobRequest withInstancesMin(int instancesMin) {
    this.instancesMin = instancesMin;
    return this;
  }

  public SubmitJobRequest withInstancesMax(int instancesMax) {
    this.instancesMax = instancesMax;
    return this;
  }

  public SubmitJobRequest withInstancesDesired(int instancesDesired) {
    this.instancesDesired = instancesDesired;
    return this;
  }

  public SubmitJobRequest withCpu(int cpu) {
    this.cpu = cpu;
    return this;
  }

  public SubmitJobRequest withMemory(int memory) {
    this.memory = memory;
    return this;
  }

  public SubmitJobRequest withDisk(int disk) {
    this.disk = disk;
    return this;
  }

  public SubmitJobRequest withRetries(int retries) {
    this.retries = retries;
    return this;
  }

  public SubmitJobRequest withRuntimeLimitSecs(int runtimeLimitSecs) {
    this.runtimeLimitSecs = runtimeLimitSecs;
    return this;
  }

  public SubmitJobRequest withGpu(int gpu) {
    this.gpu = gpu;
    return this;
  }

  public SubmitJobRequest withPorts(int[] ports) {
    this.ports = ports;
    return this;
  }

  public SubmitJobRequest withNetworkMbps(int networkMbps) {
    this.networkMbps = networkMbps;
    return this;
  }

  public SubmitJobRequest withEnv(Map<String, String> env) {
    this.env = env;
    return this;
  }

  public SubmitJobRequest withAllocateIpAddress(boolean allocateIpAddress) {
    this.allocateIpAddress = allocateIpAddress;
    return this;
  }

  public SubmitJobRequest withStack(String stack) {
    this.stack = stack;
    return this;
  }

  public SubmitJobRequest withDetail(String detail) {
    this.detail = detail;
    return this;
  }

  public SubmitJobRequest withUser(String user) {
    this.user = user;
    return this;
  }

  public SubmitJobRequest withEntryPoint(String entryPoint) {
    this.entryPoint = entryPoint;
    return this;
  }

  public SubmitJobRequest withIamProfile(String iamProfile) {
    this.iamProfile = iamProfile;
    return this;
  }

  public SubmitJobRequest withSecurityGroups(List securityGroups) {
    this.securityGroups = securityGroups;
    return this;
  }

  public SubmitJobRequest withCapacityGroup(String capacityGroup) {
    this.capacityGroup = capacityGroup;
    return this;
  }

  public SubmitJobRequest withConstraint(Constraint constraint) {
    this.constraints.add(constraint);
    return this;
  }

  public SubmitJobRequest withLabels(Map labels) {
    this.labels = labels;
    return this;
  }

  public SubmitJobRequest withContainerAttributes(Map containerAttributes) {
    this.containerAttributes = containerAttributes;
    return this;
  }

  public SubmitJobRequest withLabel(String key, String value) {
    this.labels.put(key, value);
    return this;
  }

  public SubmitJobRequest withInService(Boolean inService) {
    this.inService = inService;
    return this;
  }

  public SubmitJobRequest withMigrationPolicy(MigrationPolicy migrationPolicy) {
    this.migrationPolicy = migrationPolicy;
    return this;
  }

  public SubmitJobRequest withEfs(Efs efs) {
    this.efs = efs;
    return this;
  }

  public SubmitJobRequest withCredentials(String credentials) {
    this.credentials = credentials;
    return this;
  }

  public SubmitJobRequest withDisruptionBudget(DisruptionBudget disruptionBudget) {
    this.disruptionBudget = disruptionBudget;
    return this;
  }

  // Getters

  public String getJobType() {
    return jobType;
  }

  public int getInstanceMin() {
    return instancesMin;
  }

  public int getInstanceMax() {
    return instancesMax;
  }

  public int getInstanceDesired() {
    return instancesDesired;
  }

  public int getCpu() {
    return cpu;
  }

  public int getGpu() {
    return gpu;
  }

  public int getRetries() {
    return retries;
  }

  public int getRuntimeLimitSecs() {
    return runtimeLimitSecs;
  }

  public int getMemory() {
    return memory;
  }

  public int getDisk() {
    return disk;
  }

  public int getNetworkMbps() {
    return networkMbps;
  }

  public int[] getPorts() {
    return ports;
  }

  public Map<String, String> getEnv() {
    return env;
  }

  public String getApplication() {
    return application;
  }

  public String getJobName() {
    return jobName;
  }

  public String getDockerImageName() {
    return dockerImageName;
  }

  public String getDockerImageVersion() {
    return dockerImageVersion;
  }

  public String getDockerDigest() {
    return dockerDigest;
  }

  public boolean getAllocateIpAddress() {
    return allocateIpAddress;
  }

  public String getStack() {
    return stack;
  }

  public String getDetail() {
    return detail;
  }

  public String getUser() {
    return user;
  }

  public List<Constraint> getConstraints() {
    return constraints;
  }

  public List<String> getSecurityGroups() {
    return securityGroups;
  }

  public String getEntryPoint() {
    return entryPoint;
  }

  public String getIamProfile() {
    return iamProfile;
  }

  public Boolean getInService() {
    return inService;
  }

  public String getCapacityGroup() {
    return capacityGroup;
  }

  public Map<String, String> getLabels() {
    return labels;
  }

  public Map<String, String> getContainerAttributes() {
    return containerAttributes;
  }

  public JobDescription getJobDescription() {
    return new JobDescription(this);
  }

  public Efs getEfs() {
    return efs;
  }

  public String getCredentials() {
    return credentials;
  }

  public MigrationPolicy getMigrationPolicy() {
    return migrationPolicy;
  }
}
