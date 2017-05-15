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

import com.netflix.titus.grpc.protogen.*;
import com.sun.net.httpserver.Authenticator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JobDescription {

    private String name;
    private String type;
    private String applicationName;
    private String version;
    private int instancesDesired;
    private int instancesMax;
    private int instancesMin;
    private int cpu;
    private int memory;
    private int disk;
    private int gpu;
    private int retries;
    private int runtimeLimitSecs;
    private int networkMbps;
    private int[] ports;
    private Map<String,String> env;
    private boolean allocateIpAddress;

    private String appName;
    private String jobGroupStack;
    private String jobGroupDetail;
    private String jobGroupSequence;
    private String user;
    private List<String> softConstraints;
    private List<String> hardConstraints;
    private List<String> securityGroups;
    private Map<String, String> labels;
    private Boolean inService;

    private String entryPoint;
    private String iamProfile;
    private String capacityGroup;
    private Efs efs;

    //Soft/Hard constraints

    JobDescription() {}

    JobDescription(SubmitJobRequest request) {
        type = request.getJobType();
        name = request.getJobName();
        applicationName = request.getDockerImageName();
        version = request.getDockerImageVersion();
        instancesDesired = request.getInstanceDesired();
        instancesMin = request.getInstanceMin();
        instancesMax = request.getInstanceMax();
        cpu = request.getCpu();
        memory = request.getMemory();
        disk = request.getDisk();
        ports = request.getPorts();
        networkMbps = request.getNetworkMbps();
        allocateIpAddress = request.getAllocateIpAddress();
        appName = request.getApplication();
        jobGroupStack = request.getStack();
        jobGroupDetail = request.getDetail();
        softConstraints = request.getConstraints().stream()
                .filter((c) -> c.getConstraintType() == SubmitJobRequest.Constraint.ConstraintType.SOFT)
                .map(SubmitJobRequest.Constraint::getConstraint)
                .collect(Collectors.toList());
        hardConstraints = request.getConstraints().stream()
                .filter((c) -> c.getConstraintType() == SubmitJobRequest.Constraint.ConstraintType.HARD)
                .map(SubmitJobRequest.Constraint::getConstraint)
                .collect(Collectors.toList());
        user = request.getUser();
        env = request.getEnv() != null ? request.getEnv() : new HashMap<>();
        labels = request.getLabels() != null ? request.getLabels() : new HashMap<>();
        entryPoint = request.getEntryPoint();
        iamProfile = request.getIamProfile();
        capacityGroup = request.getCapacityGroup();
        securityGroups = request.getSecurityGroups();
        inService = request.getInService();
        efs = request.getEfs();
        gpu = request.getGpu();
        retries = request.getRetries();
        runtimeLimitSecs = request.getRuntimeLimitSecs();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
      this.version = version;
    }

    public int getInstancesMin() {
      return instancesMin;
    }

    public void setInstancesMin(int instances) {
      this.instancesMin = instancesMin;
    }

    public int getInstancesMax() {
      return instancesMax;
    }

    public void setInstancesMax(int instances) {
      this.instancesMax = instancesMax;
    }

    public int getInstancesDesired() {
      return instancesDesired;
    }

    public void setInstancesDesired(int instances) {
    this.instancesDesired = instancesDesired;
  }

    public int getCpu() {
        return cpu;
    }

    public void setCpu(int cpu) {
        this.cpu = cpu;
    }

    public int getMemory() {
        return memory;
    }

    public void setMemory(int memory) {
        this.memory = memory;
    }

    public int getDisk() {
        return disk;
    }

    public void setDisk(int disk) {
        this.disk = disk;
    }

    public void setGpu(int gpu) {
      this.gpu = gpu;
    }

    public int getGpu() {
      return gpu;
    }

    public void setRetries() { this.retries = retries; }

    public int getRetries() { return retries; }

    public int getRuntimeLimitSecs() {
      return runtimeLimitSecs;
    }

    public void setRuntimeLimitSecs(int runtimeLimitSecs) {
      this.runtimeLimitSecs = runtimeLimitSecs;
    }

    public int getNetworkMbps() {
      return networkMbps;
    }

    public void setEfs(Efs efs) {
    this.efs = efs;
  }

    public Efs getEfs() {
      return efs;
    }

    public void setNetworkMbps(int networkMbps) {
      this.networkMbps = networkMbps;
    }

    public int[] getPorts() {
        return ports;
    }

    public void setPorts(int[] ports) {
        this.ports = ports;
    }

    public boolean getAllocateIpAddress() {
        return allocateIpAddress;
    }

    public void setAllocateIpAddress(boolean name) {
        this.allocateIpAddress = allocateIpAddress;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getJobGroupStack() {
        return jobGroupStack;
    }

    public void setJobGroupStack(String jobGroupStack) {
        this.jobGroupStack = jobGroupStack;
    }

    public String getJobGroupDetail() {
        return jobGroupDetail;
    }

    public void setJobGroupDetail(String jobGroupDetail) {
        this.jobGroupDetail = jobGroupDetail;
    }

    public String getJobGroupSequence() {
        return jobGroupSequence;
    }

    public void setJobGroupSequence(String jobGroupSequence) {
        this.jobGroupSequence = jobGroupSequence;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public List<String> getSoftConstraints() {
        return softConstraints;
    }

    public void setSoftConstraints(List<String> softConstraints) {
        this.softConstraints = softConstraints;
    }

    public List<String> getHardConstraints() {
        return hardConstraints;
    }

    public void setHardConstraints(List<String> hardConstraints) {
        this.hardConstraints = hardConstraints;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String,String> getEnv() {
        return env;
    }

    public void setEnv(Map<String,String> env) {
        this.env = env;
    }

    public void setEnvParam(String key, String value) {
        if (this.env == null) {
            this.env = new HashMap<>();
        }
        this.env.put(key, value);
    }

    public Map<String, String> getLabels() { return labels; }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public String getEntryPoint() { return entryPoint; }

    public void setEntryPoint(String entryPoint) { this.entryPoint = entryPoint; }

    public String getIamProfile() { return iamProfile; }

    public void setIamProfile(String iamProfile) { this.iamProfile = iamProfile; }

    public String getCapacityGroup() { return capacityGroup; }

    public void setCapacityGroup(String capacityGroup) { this.capacityGroup = capacityGroup; }

    public Boolean getInService() { return inService; }

    public void setInService(Boolean inService) { this.inService = inService; }

    public List<String> getSecurityGroups() { return securityGroups; }

    public void setSecurityGroups(List<String> securityGroups) { this.securityGroups = securityGroups; }

    public JobDescriptor getGrpcJobDescriptor() {

      // trying to keep the same order as in the proto definition https://stash.corp.netflix.com/projects/TN/repos/titus-api-definitions/browse/src/main/proto/netflix/titus/titus_job_api.proto

      JobDescriptor.Builder jobDescriptorBuilder = JobDescriptor.newBuilder();

      jobDescriptorBuilder.setOwner(Owner.newBuilder().setTeamEmail(user));
      jobDescriptorBuilder.setAppName(appName);

      JobGroupInfo.Builder jobGroupInfoBuilder = JobGroupInfo.newBuilder();
      if (jobGroupStack != null) {
        jobGroupInfoBuilder.setStack(jobGroupStack);
      }
      if (jobGroupDetail != null) {
        jobGroupInfoBuilder.setDetail(jobGroupDetail);
      }
      jobGroupInfoBuilder.setSequence(jobGroupSequence);

      jobDescriptorBuilder.setJobGroupInfo(jobGroupInfoBuilder);

      if(!labels.isEmpty()) {
        jobDescriptorBuilder.putAllLabels(labels);
      }

      Container.Builder containerBuilder = Container.newBuilder();

      containerBuilder.setName("main");

      Map<String, String> resources = new HashMap<String, String>();

      if (cpu != 0) {
        resources.put("cpu", String.valueOf(cpu));
      }

      if (gpu!=0){
        resources.put("gpu", String.valueOf(gpu));
      }

      if(networkMbps!=0){
        resources.put("network", String.valueOf(networkMbps) + "Mbps");
      }

      if(memory!=0){
        resources.put("memory", String.valueOf(memory) + "MB");
      }

      if(disk!=0){
        resources.put("disk", String.valueOf(disk) + "MB");
      }

      if(!resources.isEmpty()){
        containerBuilder.putAllResources(resources);
      }

      if(!securityGroups.isEmpty()){
        containerBuilder.putSecurityProfile("securityGroups", String.join(",", securityGroups));
      }

      if(iamProfile != null) {
        containerBuilder.putSecurityProfile("iamProfile", iamProfile);
      }

      containerBuilder.setImage(Image.newBuilder().setName(applicationName + ":" + version));

      if(entryPoint != null){
        containerBuilder.addEntryPoint(entryPoint);
      }

      if(!env.isEmpty()){
        containerBuilder.putAllEnv(env);
      }

      if(!softConstraints.isEmpty()){
        containerBuilder.setSoftConstraints(constraintTransformer(softConstraints));
      }

      if(!hardConstraints.isEmpty()){
        containerBuilder.setSoftConstraints(constraintTransformer(hardConstraints));
      }

      jobDescriptorBuilder.addContainers(containerBuilder);

      TaskInstances.Builder taskInstances = TaskInstances.newBuilder();
      taskInstances.setMin(instancesMin).setMax(instancesMax).setDesired(instancesDesired);

      if(type == "service"){
        jobDescriptorBuilder.setService(ServiceJobSpec.newBuilder().setContainerId("main").setEnabled(inService).setInstances(taskInstances));
      }

      if(type == "batch"){
        BatchJobSpec.Builder batchJobSpec = BatchJobSpec.newBuilder();
        batchJobSpec.setContainerId("main").setInstances(taskInstances);
        if(runtimeLimitSecs != 0){
          batchJobSpec.setRuntimeLimitSecs(runtimeLimitSecs);
        }
        if(retries!=0){
          batchJobSpec.setRetryPolicy(RetryPolicy.newBuilder().setImmediate(RetryPolicy.Immediate.newBuilder().setRetries(retries)));
        }
        jobDescriptorBuilder.setBatch(batchJobSpec);
      }

      return jobDescriptorBuilder.build();
    }

    private Constraints.Builder constraintTransformer(List<String> constraints){
      Constraints.Builder constraintsBuilder = Constraints.newBuilder();
      constraints.forEach( constraint ->
        { constraintsBuilder.putConstraints(constraint, "true"); }
      );
      return constraintsBuilder;
    }

}
