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

import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.*;
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.ContainerHealthProvider;
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.TimeWindow;
import com.netflix.titus.grpc.protogen.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.netflix.titus.grpc.protogen.JobDisruptionBudget.PolicyCase.*;
import static com.netflix.titus.grpc.protogen.JobDisruptionBudget.RateCase.*;

public class Job {

  private String id;
  private String name;
  private String type;
  private List<String> tags;
  private String applicationName;
  private String digest;
  private String appName;
  private String user;
  private String version;
  private String entryPoint;
  private String iamProfile;
  private String capacityGroup;
  private Boolean inService;
  private int instances;
  private int instancesMin;
  private int instancesMax;
  private int instancesDesired;
  private int cpu;
  private int memory;
  private int disk;
  private int gpu;
  private int networkMbps;
  private int[] ports;
  private Map<String, String> environment;
  private Map<String, String> containerAttributes;
  private int retries;
  private int runtimeLimitSecs;
  private boolean allocateIpAddress;
  private Date submittedAt;
  private List<Task> tasks;
  private Map<String, String> labels;
  private List<String> securityGroups;
  private String jobGroupStack;
  private String jobGroupDetail;
  private String jobGroupSequence;
  private List<String> hardConstraints;
  private List<String> softConstraints;
  private Efs efs;
  private MigrationPolicy migrationPolicy;
  private DisruptionBudget disruptionBudget;
  private String jobState;

  public Job() {
  }

  public Job(com.netflix.titus.grpc.protogen.Job grpcJob, List<com.netflix.titus.grpc.protogen.Task> grpcTasks) {
    id = grpcJob.getId();

    if (grpcJob.getJobDescriptor().getJobSpecCase().getNumber() == JobDescriptor.BATCH_FIELD_NUMBER) {
      type = "batch";
      BatchJobSpec batchJobSpec = grpcJob.getJobDescriptor().getBatch();
      instancesMin = batchJobSpec.getSize();
      instancesMax = batchJobSpec.getSize();
      instancesDesired = batchJobSpec.getSize();
      instances = batchJobSpec.getSize();
      runtimeLimitSecs = (int) batchJobSpec.getRuntimeLimitSec();
      retries = batchJobSpec.getRetryPolicy().getImmediate().getRetries();
    }

    if (grpcJob.getJobDescriptor().getJobSpecCase().getNumber() == JobDescriptor.SERVICE_FIELD_NUMBER) {
      type = "service";
      ServiceJobSpec serviceSpec = grpcJob.getJobDescriptor().getService();
      inService = serviceSpec.getEnabled();
      instances = serviceSpec.getCapacity().getDesired();
      instancesMin = serviceSpec.getCapacity().getMin();
      instancesMax = serviceSpec.getCapacity().getMax();
      instancesDesired = serviceSpec.getCapacity().getDesired();
      migrationPolicy = new MigrationPolicy();
      com.netflix.titus.grpc.protogen.MigrationPolicy policy = serviceSpec.getMigrationPolicy();
      if (policy.getPolicyCase().equals(com.netflix.titus.grpc.protogen.MigrationPolicy.PolicyCase.SELFMANAGED)) {
        migrationPolicy.setType("selfManaged");
      } else {
        migrationPolicy.setType("systemDefault");
      }
    }

    addDisruptionBudget(grpcJob);
    labels = grpcJob.getJobDescriptor().getAttributesMap();
    containerAttributes = grpcJob.getJobDescriptor().getContainer().getAttributesMap();
    user = grpcJob.getJobDescriptor().getOwner().getTeamEmail();

    if (grpcTasks != null) {
      tasks = grpcTasks.stream().map(grpcTask -> new Task(grpcTask)).collect(Collectors.toList());
    } else {
      tasks = new ArrayList<>();
    }

    appName = grpcJob.getJobDescriptor().getApplicationName();
    name = grpcJob.getJobDescriptor().getAttributesOrDefault("name", appName);
    applicationName = grpcJob.getJobDescriptor().getContainer().getImage().getName();
    version = grpcJob.getJobDescriptor().getContainer().getImage().getTag();
    digest = grpcJob.getJobDescriptor().getContainer().getImage().getDigest();
    entryPoint = grpcJob.getJobDescriptor().getContainer().getEntryPointList().stream().collect(Collectors.joining(" "));
    capacityGroup = grpcJob.getJobDescriptor().getCapacityGroup();
    cpu = (int) grpcJob.getJobDescriptor().getContainer().getResources().getCpu();
    memory = grpcJob.getJobDescriptor().getContainer().getResources().getMemoryMB();
    gpu = grpcJob.getJobDescriptor().getContainer().getResources().getGpu();
    networkMbps = grpcJob.getJobDescriptor().getContainer().getResources().getNetworkMbps();
    disk = grpcJob.getJobDescriptor().getContainer().getResources().getDiskMB();
    jobGroupSequence = grpcJob.getJobDescriptor().getJobGroupInfo().getSequence();
    jobGroupStack = grpcJob.getJobDescriptor().getJobGroupInfo().getStack();
    jobGroupDetail = grpcJob.getJobDescriptor().getJobGroupInfo().getDetail();
    environment = grpcJob.getJobDescriptor().getContainer().getEnvMap();
    securityGroups = grpcJob.getJobDescriptor().getContainer().getSecurityProfile().getSecurityGroupsList().stream().collect(Collectors.toList());
    iamProfile = grpcJob.getJobDescriptor().getContainer().getSecurityProfile().getIamRole();
    allocateIpAddress = true;
    submittedAt = new Date(grpcJob.getStatus().getTimestamp());
    softConstraints = new ArrayList<String>();
    softConstraints.addAll(grpcJob.getJobDescriptor().getContainer().getSoftConstraints().getConstraintsMap().keySet());
    hardConstraints = new ArrayList<String>();
    hardConstraints.addAll(grpcJob.getJobDescriptor().getContainer().getHardConstraints().getConstraintsMap().keySet());

    jobState = grpcJob.getStatus().getState().toString();

    if (grpcJob.getJobDescriptor().getContainer().getResources().getEfsMountsCount() > 0) {
      efs = new Efs();
      ContainerResources.EfsMount firstMount = grpcJob.getJobDescriptor().getContainer().getResources().getEfsMounts(0);
      efs.setEfsId(firstMount.getEfsId());
      efs.setMountPerm(firstMount.getMountPerm().toString());
      efs.setMountPoint(firstMount.getMountPoint());
      if (firstMount.getEfsRelativeMountPoint() != null) {
        efs.setEfsRelativeMountPoint(firstMount.getEfsRelativeMountPoint());
      }
    }

  }

  private void addDisruptionBudget(com.netflix.titus.grpc.protogen.Job grpcJob) {
    JobDisruptionBudget budget = grpcJob.getJobDescriptor().getDisruptionBudget();
    disruptionBudget = new DisruptionBudget();
    if (budget.getContainerHealthProvidersList() != null) {
      disruptionBudget.setContainerHealthProviders(
        budget.getContainerHealthProvidersList().stream()
          .map(c -> new ContainerHealthProvider(c.getName()))
          .collect(Collectors.toList())
      );
    }
    if (RATEUNLIMITED.equals(budget.getRateCase())) {
      disruptionBudget.setRateUnlimited(new RateUnlimited());
    }
    if (RATEPERCENTAGEPERHOUR.equals(budget.getRateCase())) {
      disruptionBudget.setRatePercentagePerHour(
        new RatePercentagePerHour(budget.getRatePercentagePerHour().getMaxPercentageOfContainersRelocatedInHour())
      );
    }
    if (RATEPERINTERVAL.equals(budget.getRateCase())) {
      disruptionBudget.setRatePerInterval(
        new RatePerInterval(budget.getRatePerInterval().getIntervalMs(), budget.getRatePerInterval().getLimitPerInterval())
      );
    }
    if (RATEPERCENTAGEPERINTERVAL.equals(budget.getRateCase())) {
      disruptionBudget.setRatePercentagePerInterval(
        new RatePercentagePerInterval(budget.getRatePercentagePerInterval().getIntervalMs(), budget.getRatePercentagePerInterval().getPercentageLimitPerInterval())
      );
    }

    if (SELFMANAGED.equals(budget.getPolicyCase())) {
      disruptionBudget.setSelfManaged(
        new SelfManaged(budget.getSelfManaged().getRelocationTimeMs())
      );
    }
    if (AVAILABILITYPERCENTAGELIMIT.equals(budget.getPolicyCase())) {
      disruptionBudget.setAvailabilityPercentageLimit(
        new AvailabilityPercentageLimit(budget.getAvailabilityPercentageLimit().getPercentageOfHealthyContainers())
      );
    }
    if (UNHEALTHYTASKSLIMIT.equals(budget.getPolicyCase())) {
      disruptionBudget.setUnhealthyTasksLimit(
        new UnhealthyTasksLimit(budget.getUnhealthyTasksLimit().getLimitOfUnhealthyContainers())
      );
    }
    if (RELOCATIONLIMIT.equals(budget.getPolicyCase())) {
      disruptionBudget.setRelocationLimit(
        new RelocationLimit(budget.getRelocationLimit().getLimit())
      );
    }
    if (budget.getTimeWindowsList() != null) {
      disruptionBudget.setTimeWindows(
        budget.getTimeWindowsList().stream()
          .map(w -> new TimeWindow(
            w.getDaysList().stream().map(Enum::name).collect(Collectors.toList()),
            w.getHourlyTimeWindowsList().stream().map(t -> new HourlyTimeWindow(t.getStartHour(), t.getEndHour())).collect(Collectors.toList()),
            w.getTimeZone()
          ))
          .collect(Collectors.toList())
      );
    }
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getEntryPoint() {
    return entryPoint;
  }

  public void setEntryPoint(String entryPoint) {
    this.entryPoint = entryPoint;
  }

  public int getInstances() {
    return instances;
  }

  public void setInstances(int instances) {
    this.instances = instances;
  }

  public int getInstancesMin() {
    return instancesMin;
  }

  public void setInstancesMin(int instancesMin) {
    this.instancesMin = instancesMin;
  }

  public int getInstancesMax() {
    return instancesMax;
  }

  public void setInstancesMax(int instancesMax) {
    this.instancesMax = instancesMax;
  }

  public int getInstancesDesired() {
    return instancesDesired;
  }

  public void setInstancesDesired(int instancesDesired) {
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

  public int[] getPorts() {
    return ports;
  }

  public void setPorts(int[] ports) {
    this.ports = ports;
  }

  public Map<String, String> getEnvironment() {
    return environment;
  }

  public void setEnvironment(Map<String, String> environment) {
    this.environment = environment;
  }

  public int getRetries() {
    return retries;
  }

  public void setRetries(int retries) {
    this.retries = retries;
  }

  public int getRuntimeLimitSecs() {
    return runtimeLimitSecs;
  }

  public void setRuntimeLimitSecs(int runtimeLimitSecs) {
    this.runtimeLimitSecs = runtimeLimitSecs;
  }

  public boolean isAllocateIpAddress() {
    return allocateIpAddress;
  }

  public void setAllocateIpAddress(boolean allocateIpAddress) {
    this.allocateIpAddress = allocateIpAddress;
  }

  public Date getSubmittedAt() {
    return submittedAt;
  }

  public void setSubmittedAt(Date submittedAt) {
    this.submittedAt = submittedAt;
  }

  public List<Task> getTasks() {
    return tasks;
  }

  public void setTasks(List<Task> tasks) {
    this.tasks = tasks;
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

  public Boolean isInService() {
    return inService;
  }

  public void setInService(Boolean inService) {
    this.inService = inService;
  }

  public List<String> getSecurityGroups() {
    return securityGroups;
  }

  public void setSecurityGroups(List<String> securityGroups) {
    this.securityGroups = securityGroups;
  }

  public Map<String, String> getLabels() {
    return labels;
  }

  public void setLabels(Map<String, String> labels) {
    this.labels = labels;
  }

  public Map<String, String> getContainerAttributes() {
    return containerAttributes;
  }

  public void setContainerAttributes(Map<String, String> containerAttributes) {
    this.containerAttributes = containerAttributes;
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

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public List<String> getHardConstraints() {
    return hardConstraints;
  }

  public void setHardConstraints(List<String> hardConstraints) {
    this.hardConstraints = hardConstraints;
  }

  public List<String> getSoftConstraints() {
    return softConstraints;
  }

  public void setSoftConstraints(List<String> softConstraints) {
    this.softConstraints = softConstraints;
  }

  public int getNetworkMbps() {
    return networkMbps;
  }

  public void setNetworkMbps(int networkMbps) {
    this.networkMbps = networkMbps;
  }

  public Efs getEfs() {
    return efs;
  }

  public void setEfs(Efs efs) {
    this.efs = efs;
  }

  public MigrationPolicy getMigrationPolicy() {
    return migrationPolicy;
  }

  public void setMigrationPolicy(MigrationPolicy migrationPolicy) {
    this.migrationPolicy = migrationPolicy;
  }

  public String getJobState() {
    return jobState;
  }

  public void setDigest(String digest) { this.digest = digest; }

  public String getDigest() { return digest; }

  public DisruptionBudget getDisruptionBudget() {
    return disruptionBudget;
  }

  public void setDisruptionBudget(DisruptionBudget disruptionBudget) {
    this.disruptionBudget = disruptionBudget;
  }

}
