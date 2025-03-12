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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.protobuf.ByteString;
import com.netflix.titus.grpc.protogen.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JobDescription {

  private String name;
  private String type;
  private String applicationName;
  private String version;
  private String digest;
  private int instancesDesired;
  private int instancesMax;
  private int instancesMin;
  private int cpu;
  private int memory;
  private int sharedMemory;
  private int disk;
  private int gpu;
  private int retries;
  private int runtimeLimitSecs;
  private int networkMbps;
  private int[] ports;
  private Map<String, String> env;
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
  private Map<String, String> containerAttributes;
  private Boolean inService;

  private String entryPoint;
  private String cmd;
  private String iamProfile;
  private String capacityGroup;
  private Efs efs;
  private MigrationPolicy migrationPolicy;
  private Map<String, String> securityAttributes;

  private DisruptionBudget disruptionBudget;

  private SubmitJobRequest.Constraints constraints;
  private ServiceJobProcesses serviceJobProcesses;
  private List<SignedAddressAllocations> signedAddressAllocations;

  // Soft/Hard constraints

  JobDescription() {}

  JobDescription(SubmitJobRequest request) {
    type = request.getJobType();
    name = request.getJobName();
    applicationName = request.getDockerImageName();
    version = request.getDockerImageVersion();
    digest = request.getDockerDigest();
    instancesDesired = request.getInstancesDesired();
    instancesMin = request.getInstancesMin();
    instancesMax = request.getInstancesMax();
    cpu = request.getCpu();
    memory = request.getMemory();
    sharedMemory = request.getSharedMemory();
    disk = request.getDisk();
    ports = request.getPorts();
    networkMbps = request.getNetworkMbps();
    allocateIpAddress = request.isAllocateIpAddress();
    appName = request.getApplication();
    jobGroupStack = request.getStack();
    jobGroupDetail = request.getDetail();
    softConstraints =
        request.getConstraints().stream()
            .filter((c) -> c.getConstraintType() == SubmitJobRequest.Constraint.ConstraintType.SOFT)
            .map(SubmitJobRequest.Constraint::getConstraint)
            .collect(Collectors.toList());
    hardConstraints =
        request.getConstraints().stream()
            .filter((c) -> c.getConstraintType() == SubmitJobRequest.Constraint.ConstraintType.HARD)
            .map(SubmitJobRequest.Constraint::getConstraint)
            .collect(Collectors.toList());
    user = request.getUser();
    env = request.getEnv() != null ? request.getEnv() : new HashMap<>();
    labels = request.getLabels() != null ? request.getLabels() : new HashMap<>();
    containerAttributes =
        request.getContainerAttributes() != null
            ? request.getContainerAttributes()
            : new HashMap<>();
    entryPoint = request.getEntryPoint();
    cmd = request.getCmd();
    iamProfile = request.getIamProfile();
    capacityGroup = request.getCapacityGroup();
    securityGroups = request.getSecurityGroups();
    inService = request.getInService();
    migrationPolicy = request.getMigrationPolicy();
    efs = request.getEfs();
    gpu = request.getGpu();
    retries = request.getRetries();
    runtimeLimitSecs = request.getRuntimeLimitSecs();
    securityAttributes = new HashMap<String, String>();

    disruptionBudget = request.getDisruptionBudget();
    constraints = request.getContainerConstraints();
    serviceJobProcesses = request.getServiceJobProcesses();
    signedAddressAllocations = request.getSignedAddressAllocations();
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

  public void setRetries() {
    this.retries = retries;
  }

  public int getRetries() {
    return retries;
  }

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

  public Map<String, String> getEnv() {
    return env;
  }

  public void setEnv(Map<String, String> env) {
    this.env = env;
  }

  public void setContainerAttributes(Map<String, String> containerAttributes) {
    this.containerAttributes = containerAttributes;
  }

  public void setEnvParam(String key, String value) {
    if (this.env == null) {
      this.env = new HashMap<>();
    }
    this.env.put(key, value);
  }

  public Map<String, String> getLabels() {
    return labels;
  }

  public void setLabels(Map<String, String> labels) {
    this.labels = labels;
  }

  public String getEntryPoint() {
    return entryPoint;
  }

  public void setEntryPoint(String entryPoint) {
    this.entryPoint = entryPoint;
  }

  public String getCmd() {
    return cmd;
  }

  public void setCmd(String cmd) {
    this.cmd = cmd;
  }

  public String getIamProfile() {
    return iamProfile;
  }

  public void setIamProfile(String iamProfile) {
    this.iamProfile = iamProfile;
  }

  public String getCapacityGroup() {
    return capacityGroup;
  }

  public void setCapacityGroup(String capacityGroup) {
    this.capacityGroup = capacityGroup;
  }

  public Boolean getInService() {
    return inService;
  }

  public void setInService(Boolean inService) {
    this.inService = inService;
  }

  public MigrationPolicy getMigrationPolicy() {
    return migrationPolicy;
  }

  public void setMigrationPolicy(MigrationPolicy migrationPolicy) {
    this.migrationPolicy = migrationPolicy;
  }

  public List<String> getSecurityGroups() {
    return securityGroups;
  }

  public void setSecurityGroups(List<String> securityGroups) {
    this.securityGroups = securityGroups;
  }

  public void setDigest(String digest) {
    this.digest = digest;
  }

  public String getDigest() {
    return digest;
  }

  public DisruptionBudget getDisruptionBudget() {
    return disruptionBudget;
  }

  public void setDisruptionBudget(DisruptionBudget disruptionBudget) {
    this.disruptionBudget = disruptionBudget;
  }

  public ServiceJobProcesses getServiceJobProcesses() {
    return serviceJobProcesses;
  }

  public void setServiceJobProcesses(ServiceJobProcesses serviceJobProcesses) {
    this.serviceJobProcesses = serviceJobProcesses;
  }

  public List<SignedAddressAllocations> getSignedAddressAllocations() {
    return signedAddressAllocations;
  }

  public void setSignedAddressAllocations(List<SignedAddressAllocations> signedAddressAllocations) {
    this.signedAddressAllocations = signedAddressAllocations;
  }

  @JsonIgnore
  public SubmitJobRequest.Constraints getConstraints() {
    return constraints;
  }

  public void setConstraints(SubmitJobRequest.Constraints constraints) {
    this.constraints = constraints;
  }

  @JsonIgnore
  public Map<String, String> getSecurityAttributes() {
    return securityAttributes;
  }

  @JsonIgnore
  public JobDescriptor getGrpcJobDescriptor() {

    // trying to keep the same order as in the proto definition
    // https://stash.corp.netflix.com/projects/TN/repos/titus-api-definitions/browse/src/main/proto/netflix/titus/titus_job_api.proto

    JobDescriptor.Builder jobDescriptorBuilder = JobDescriptor.newBuilder();

    jobDescriptorBuilder.setOwner(Owner.newBuilder().setTeamEmail(user));
    jobDescriptorBuilder.setApplicationName(appName);

    if (!labels.isEmpty()) {
      jobDescriptorBuilder.putAllAttributes(labels);
    }

    Container.Builder containerBuilder = Container.newBuilder();
    ContainerResources.Builder containerResources =
        ContainerResources.newBuilder().setAllocateIP(true);

    if (cpu != 0) {
      containerResources.setCpu(cpu);
    }

    if (gpu != 0) {
      containerResources.setGpu(gpu);
    }

    if (networkMbps != 0) {
      containerResources.setNetworkMbps(networkMbps);
    }

    if (memory != 0) {
      containerResources.setMemoryMB(memory);
    }

    if (sharedMemory != 0) {
      containerResources.setShmSizeMB(sharedMemory);
    }

    if (disk != 0) {
      containerResources.setDiskMB(disk);
    }

    if (signedAddressAllocations != null && !signedAddressAllocations.isEmpty()) {
      signedAddressAllocations.forEach(
          signedAddressAllocation -> {
            SignedAddressAllocation.Builder builder =
                convertSignedAddressAllocations(signedAddressAllocation);
            containerResources.addSignedAddressAllocations(builder);
          });
    }

    if (efs != null && efs.getEfsId() != null) {
      ContainerResources.EfsMount.Builder efsBuilder = ContainerResources.EfsMount.newBuilder();
      efsBuilder.setEfsId(efs.getEfsId());
      efsBuilder.setMountPoint(efs.getMountPoint());
      efsBuilder.setMountPerm(convertMountPerm(efs.getMountPerm()));
      if (efs.getEfsRelativeMountPoint() != null) {
        efsBuilder.setEfsRelativeMountPoint(efs.getEfsRelativeMountPoint());
      }
      containerResources.addEfsMounts(efsBuilder);
    }

    containerBuilder.setResources(containerResources);

    SecurityProfile.Builder securityProfile = SecurityProfile.newBuilder();

    if (securityGroups != null && !securityGroups.isEmpty()) {
      securityGroups.forEach(
          sg -> {
            securityProfile.addSecurityGroups(sg);
          });
    }

    if (iamProfile != null) {
      securityProfile.setIamRole(iamProfile);
    }

    if (!securityAttributes.isEmpty()) {
      securityProfile.putAllAttributes(securityAttributes);
    }

    containerBuilder.setSecurityProfile(securityProfile);

    Image.Builder imageBuilder = Image.newBuilder();
    imageBuilder.setName(applicationName);
    if (digest != null) {
      imageBuilder.setDigest(digest);
    } else {
      imageBuilder.setTag(version);
    }

    containerBuilder.setImage(imageBuilder);

    if (entryPoint != null) {
      containerBuilder.addEntryPoint(entryPoint);
    }

    if (cmd != null && !cmd.isEmpty()) {
      containerBuilder.addCommand(cmd);
    }

    if (!containerAttributes.isEmpty()) {
      containerBuilder.putAllAttributes(containerAttributes);
    }

    if (!env.isEmpty()) {
      containerBuilder.putAllEnv(env);
    }

    if (constraints != null) {
      Constraints.Builder constraintsBuilder = Constraints.newBuilder();
      containerBuilder.setHardConstraints(
          constraintsBuilder.putAllConstraints(constraints.getHard()));
      constraintsBuilder = Constraints.newBuilder();
      containerBuilder.setSoftConstraints(
          constraintsBuilder.putAllConstraints(constraints.getSoft()));
    } else {
      if (!softConstraints.isEmpty()) {
        containerBuilder.setSoftConstraints(constraintTransformer(softConstraints));
      }

      if (!hardConstraints.isEmpty()) {
        containerBuilder.setHardConstraints(constraintTransformer(hardConstraints));
      }
    }

    jobDescriptorBuilder.setContainer(containerBuilder);

    Capacity.Builder jobCapacity = Capacity.newBuilder();
    jobCapacity.setMin(instancesMin).setMax(instancesMax).setDesired(instancesDesired);

    JobGroupInfo.Builder jobGroupInfoBuilder = JobGroupInfo.newBuilder();
    if (jobGroupStack != null) {
      jobGroupInfoBuilder.setStack(jobGroupStack);
    }
    if (jobGroupDetail != null) {
      jobGroupInfoBuilder.setDetail(jobGroupDetail);
    }
    if (jobGroupSequence != null) {
      jobGroupInfoBuilder.setSequence(jobGroupSequence);
    }
    jobDescriptorBuilder.setJobGroupInfo(jobGroupInfoBuilder);

    if (type.equals("service")) {
      if (inService == null) {
        inService = true;
      }

      com.netflix.titus.grpc.protogen.MigrationPolicy serviceMigrationPolicy;

      if (migrationPolicy != null && migrationPolicy.getType().equals("selfManaged")) {
        serviceMigrationPolicy =
            com.netflix.titus.grpc.protogen.MigrationPolicy.newBuilder()
                .setSelfManaged(
                    com.netflix.titus.grpc.protogen.MigrationPolicy.SelfManaged.newBuilder()
                        .build())
                .build();
      } else {
        serviceMigrationPolicy =
            com.netflix.titus.grpc.protogen.MigrationPolicy.newBuilder()
                .setSystemDefault(
                    com.netflix.titus.grpc.protogen.MigrationPolicy.SystemDefault.newBuilder()
                        .build())
                .build();
      }
      com.netflix.titus.grpc.protogen.ServiceJobSpec.ServiceJobProcesses.Builder
          titusServiceJobProcesses = ServiceJobSpec.ServiceJobProcesses.newBuilder();
      if (serviceJobProcesses != null) {
        titusServiceJobProcesses
            .setDisableDecreaseDesired(serviceJobProcesses.isDisableDecreaseDesired())
            .setDisableIncreaseDesired(serviceJobProcesses.isDisableIncreaseDesired())
            .build();
      }
      jobDescriptorBuilder.setService(
          ServiceJobSpec.newBuilder()
              .setEnabled(inService)
              .setCapacity(jobCapacity)
              .setMigrationPolicy(serviceMigrationPolicy)
              .setServiceJobProcesses(titusServiceJobProcesses)
              .setRetryPolicy(
                  RetryPolicy.newBuilder()
                      .setExponentialBackOff(
                          RetryPolicy.ExponentialBackOff.newBuilder()
                              .setInitialDelayMs(5000)
                              .setMaxDelayIntervalMs(300000))));
    }

    if (type.equals("batch")) {
      BatchJobSpec.Builder batchJobSpec = BatchJobSpec.newBuilder();
      batchJobSpec.setSize(instancesDesired);
      if (runtimeLimitSecs != 0) {
        batchJobSpec.setRuntimeLimitSec(runtimeLimitSecs);
      }
      batchJobSpec.setRetryPolicy(
          RetryPolicy.newBuilder()
              .setImmediate(RetryPolicy.Immediate.newBuilder().setRetries(retries)));
      jobDescriptorBuilder.setBatch(batchJobSpec);
    }

    if (capacityGroup == null || capacityGroup.isEmpty()) {
      jobDescriptorBuilder.setCapacityGroup(jobDescriptorBuilder.getApplicationName());
    } else {
      jobDescriptorBuilder.setCapacityGroup(capacityGroup);
    }

    if (disruptionBudget != null) {
      JobDisruptionBudget budget = convertJobDisruptionBudget(disruptionBudget);
      if (budget != null) {
        jobDescriptorBuilder.setDisruptionBudget(budget);
      }
    }

    return jobDescriptorBuilder.build();
  }

  // Returns builder for Protobuf type com.netflix.titus.SignedAddressAllocation
  private SignedAddressAllocation.Builder convertSignedAddressAllocations(
      SignedAddressAllocations signedAddressAllocation) {

    SignedAddressAllocations.AddressLocation addressLocation =
        signedAddressAllocation.getAddressAllocation().getAddressLocation();

    AddressLocation.Builder addressLocationBuilder = AddressLocation.newBuilder();
    addressLocationBuilder.setAvailabilityZone(addressLocation.getAvailabilityZone());
    addressLocationBuilder.setRegion(addressLocation.getRegion());
    addressLocationBuilder.setSubnetId(addressLocation.getSubnetId());
    addressLocationBuilder.build();

    AddressAllocation.Builder addressAllocationBuilder =
        AddressAllocation.newBuilder().setAddressLocation(addressLocationBuilder);
    addressAllocationBuilder.setUuid(signedAddressAllocation.getAddressAllocation().getUuid());
    addressAllocationBuilder.setAddress(
        signedAddressAllocation.getAddressAllocation().getAddress());
    addressAllocationBuilder.build();

    SignedAddressAllocation.Builder signedAddressAllocationBuilder =
        SignedAddressAllocation.newBuilder().setAddressAllocation(addressAllocationBuilder);
    signedAddressAllocationBuilder.setAuthoritativePublicKey(
        ByteString.copyFromUtf8(signedAddressAllocation.getAuthoritativePublicKey()));
    signedAddressAllocationBuilder.setHostPublicKey(
        ByteString.copyFromUtf8(signedAddressAllocation.getHostPublicKey()));
    signedAddressAllocationBuilder.setHostPublicKeySignature(
        ByteString.copyFromUtf8(signedAddressAllocation.getHostPublicKeySignature()));
    signedAddressAllocationBuilder.setMessage(
        ByteString.copyFromUtf8(signedAddressAllocation.getMessage()));
    signedAddressAllocationBuilder.setMessageSignature(
        ByteString.copyFromUtf8(signedAddressAllocation.getMessageSignature()));
    signedAddressAllocationBuilder.build();

    return signedAddressAllocationBuilder;
  }

  private JobDisruptionBudget convertJobDisruptionBudget(DisruptionBudget budget) {
    JobDisruptionBudget.Builder builder = JobDisruptionBudget.newBuilder();
    if (budget.getAvailabilityPercentageLimit() != null) {
      builder.setAvailabilityPercentageLimit(
          JobDisruptionBudget.AvailabilityPercentageLimit.newBuilder()
              .setPercentageOfHealthyContainers(
                  budget.availabilityPercentageLimit.getPercentageOfHealthyContainers())
              .build());
    }
    if (budget.getContainerHealthProviders() != null
        && !budget.getContainerHealthProviders().isEmpty()) {
      budget
          .getContainerHealthProviders()
          .forEach(
              chp ->
                  builder.addContainerHealthProviders(
                      ContainerHealthProvider.newBuilder().setName(chp.getName()).build()));
    }

    if (budget.getSelfManaged() != null) {
      builder.setSelfManaged(
          JobDisruptionBudget.SelfManaged.newBuilder()
              .setRelocationTimeMs(budget.getSelfManaged().getRelocationTimeMs())
              .build());
    }

    if (budget.getRatePercentagePerHour() != null) {
      builder.setRatePercentagePerHour(
          JobDisruptionBudget.RatePercentagePerHour.newBuilder()
              .setMaxPercentageOfContainersRelocatedInHour(
                  budget.getRatePercentagePerHour().getMaxPercentageOfContainersRelocatedInHour())
              .build());
    }

    if (budget.getRatePerInterval() != null) {
      builder.setRatePerInterval(
          JobDisruptionBudget.RatePerInterval.newBuilder()
              .setIntervalMs(budget.getRatePerInterval().getIntervalMs())
              .setLimitPerInterval(budget.getRatePerInterval().getLimitPerInterval())
              .build());
    }

    if (budget.getRatePercentagePerInterval() != null) {
      builder.setRatePercentagePerInterval(
          JobDisruptionBudget.RatePercentagePerInterval.newBuilder()
              .setIntervalMs(budget.getRatePercentagePerInterval().getIntervalMs())
              .setPercentageLimitPerInterval(
                  budget.getRatePercentagePerInterval().getPercentageLimitPerInterval())
              .build());
    }

    if (budget.getRelocationLimit() != null) {
      builder.setRelocationLimit(
          JobDisruptionBudget.RelocationLimit.newBuilder()
              .setLimit(budget.getRelocationLimit().getLimit()));
    }

    if (budget.getTimeWindows() != null && !budget.getTimeWindows().isEmpty()) {
      budget
          .getTimeWindows()
          .forEach(
              tw -> {
                TimeWindow.Builder timeWindowBuilder = TimeWindow.newBuilder();
                tw.getDays().forEach(day -> timeWindowBuilder.addDays(convertDay(day)));
                tw.getHourlyTimeWindows()
                    .forEach(
                        htw -> {
                          timeWindowBuilder.addHourlyTimeWindows(
                              TimeWindow.HourlyTimeWindow.newBuilder()
                                  .setEndHour(htw.getEndHour())
                                  .setStartHour(htw.getStartHour())
                                  .build());
                        });
                timeWindowBuilder.setTimeZone(tw.getTimeZone());
                builder.addTimeWindows(timeWindowBuilder.build());
              });
    }

    if (budget.getUnhealthyTasksLimit() != null) {
      builder.setUnhealthyTasksLimit(
          JobDisruptionBudget.UnhealthyTasksLimit.newBuilder()
              .setLimitOfUnhealthyContainers(
                  budget.getUnhealthyTasksLimit().getLimitOfUnhealthyContainers())
              .build());
    }

    return builder.build();
  }

  private Day convertDay(String day) {
    switch (day) {
      case "Monday":
        return Day.Monday;
      case "Tuesday":
        return Day.Tuesday;
      case "Wednesday":
        return Day.Wednesday;
      case "Thursday":
        return Day.Thursday;
      case "Friday":
        return Day.Friday;
      case "Saturday":
        return Day.Saturday;
      default:
        return Day.Sunday;
    }
  }

  private MountPerm convertMountPerm(String mountPerm) {
    switch (mountPerm) {
      case "RO":
        return MountPerm.RO;
      case "WO":
        return MountPerm.WO;
      default:
        return MountPerm.RW;
    }
  }

  private Constraints.Builder constraintTransformer(List<String> constraints) {
    Constraints.Builder constraintsBuilder = Constraints.newBuilder();
    constraints.forEach(
        constraint -> {
          constraintsBuilder.putConstraints(constraint, "true");
        });
    return constraintsBuilder;
  }
}
