package com.netflix.spinnaker.clouddriver.titus.deploy.description;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.clouddriver.orchestration.SagaContextAware;
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEvent;
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable;
import com.netflix.spinnaker.clouddriver.titus.client.model.DisruptionBudget;
import com.netflix.spinnaker.clouddriver.titus.client.model.Efs;
import com.netflix.spinnaker.clouddriver.titus.client.model.MigrationPolicy;
import com.netflix.spinnaker.clouddriver.titus.client.model.ServiceJobProcesses;
import com.netflix.spinnaker.clouddriver.titus.client.model.SignedAddressAllocations;
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest;
import com.netflix.spinnaker.clouddriver.titus.model.DockerImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TitusDeployDescription extends AbstractTitusCredentialsDescription
    implements DeployDescription, ApplicationNameable, SagaContextAware {
  private String region;
  private String subnet;
  private List<String> zones = new ArrayList<>();
  private List<String> securityGroups = new ArrayList<>();
  private List<String> securityGroupNames = new ArrayList<>();
  private List<String> targetGroups = new ArrayList<>();
  private List<String> softConstraints;
  private List<String> hardConstraints;
  private String application;
  private String stack;
  private String freeFormDetails;
  private String imageId;
  private Capacity capacity = new Capacity();
  private Resources resources = new Resources();
  private Map<String, String> env = new LinkedHashMap<>();
  private Map<String, String> labels = new LinkedHashMap<>();
  private Map<String, String> containerAttributes = new LinkedHashMap<>();
  private String entryPoint;
  private String cmd;
  private String iamProfile;
  private String capacityGroup;
  private String user;
  private Boolean inService;
  private String jobType;
  private int retries;
  private int runtimeLimitSecs;
  private List<String> interestingHealthProviderNames = new ArrayList<>();
  private MigrationPolicy migrationPolicy;
  private Boolean copySourceScalingPoliciesAndActions = true;
  private Integer sequence;
  private DisruptionBudget disruptionBudget;
  private SubmitJobRequest.Constraints constraints = new SubmitJobRequest.Constraints();
  private ServiceJobProcesses serviceJobProcesses;
  @JsonIgnore private SagaContext sagaContext;

  /**
   * Will be overridden by any the label {@code PrepareTitusDeploy.USE_APPLICATION_DEFAULT_SG_LABEL}
   *
   * <p>TODO(rz): Redundant; migrate off this property or the label (pref to migrate off the label)
   */
  @Deprecated private boolean useApplicationDefaultSecurityGroup = true;

  /**
   * If false, the newly created server group will not pick up scaling policies and actions from an
   * ancestor group
   */
  private boolean copySourceScalingPolicies = true;

  private List<OperationEvent> events = new ArrayList<>();
  private Source source = new Source();
  private Efs efs;

  @Override
  public Collection<String> getApplications() {
    return Arrays.asList(application);
  }

  @Override
  public void setSagaContext(SagaContext sagaContext) {
    this.sagaContext = sagaContext;
  }

  @Nullable
  public SagaContext getSagaContext() {
    return sagaContext;
  }

  /** For Jackson deserialization. */
  public void setApplications(List<String> applications) {
    if (!applications.isEmpty()) {
      application = applications.get(0);
    }
  }

  @Nonnull
  public SubmitJobRequest toSubmitJobRequest(
      @Nonnull DockerImage dockerImage, @Nonnull String jobName, String user) {
    final SubmitJobRequest.SubmitJobRequestBuilder submitJobRequest =
        SubmitJobRequest.builder()
            .jobName(jobName)
            .user(user)
            .application(application)
            .dockerImageName(dockerImage.getImageName())
            .instancesMin(capacity.getMin())
            .instancesMax(capacity.getMax())
            .instancesDesired(capacity.getDesired())
            .cpu(resources.getCpu())
            .memory(resources.getMemory())
            .sharedMemory(resources.getSharedMemory())
            .disk(resources.getDisk())
            .retries(retries)
            .runtimeLimitSecs(runtimeLimitSecs)
            .gpu(resources.getGpu())
            .networkMbps(resources.getNetworkMbps())
            .efs(efs)
            .ports(resources.getPorts())
            .env(env)
            .allocateIpAddress(resources.isAllocateIpAddress())
            .stack(stack)
            .detail(freeFormDetails)
            .entryPoint(entryPoint)
            .iamProfile(iamProfile)
            .capacityGroup(capacityGroup)
            .labels(labels)
            .inService(inService)
            .migrationPolicy(migrationPolicy)
            .credentials(getCredentials().getName())
            .containerAttributes(containerAttributes)
            .disruptionBudget(disruptionBudget)
            .signedAddressAllocations(resources.getSignedAddressAllocations())
            .serviceJobProcesses(serviceJobProcesses);

    if (cmd != null && !cmd.isEmpty()) {
      submitJobRequest.cmd(cmd);
    }

    if (!securityGroups.isEmpty()) {
      submitJobRequest.securityGroups(securityGroups);
    }

    if (dockerImage.getImageDigest() != null) {
      submitJobRequest.dockerDigest(dockerImage.getImageDigest());
    } else {
      submitJobRequest.dockerImageVersion(dockerImage.getImageVersion());
    }

    /**
     * Titus api now supports the ability to set key/value for hard & soft constraints, but the
     * original interface we supported was just a list of keys, to make this change backwards
     * compatible we give preference to they\ constraints key/value map vs soft & hard constraints
     * list
     */
    if (constraints.getHard() != null || constraints.getSoft() != null) {
      submitJobRequest.containerConstraints(constraints);
    } else {
      log.warn("Use of deprecated constraints payload: {}-{}", application, stack);

      List<SubmitJobRequest.Constraint> constraints = new ArrayList<>();
      if (hardConstraints != null) {
        hardConstraints.forEach(c -> constraints.add(SubmitJobRequest.Constraint.hard(c)));
      }
      if (softConstraints != null) {
        softConstraints.forEach(c -> constraints.add(SubmitJobRequest.Constraint.soft(c)));
      }
      submitJobRequest.constraints(constraints);
    }

    if (jobType != null) {
      submitJobRequest.jobType(jobType);
    }

    return submitJobRequest.build();
  }

  @Data
  public static class Capacity {
    private int min;
    private int max;
    private int desired;
  }

  @Data
  public static class Resources {
    private int cpu;
    private int memory;
    private int sharedMemory;
    private int disk;
    private int gpu;
    private int networkMbps;
    private int[] ports;
    private boolean allocateIpAddress;
    private List<SignedAddressAllocations> signedAddressAllocations;
  }

  @Data
  public static class Source {
    private String account;
    private String region;
    private String asgName;
    private boolean useSourceCapacity;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getSubnet() {
    return subnet;
  }

  public void setSubnet(String subnet) {
    this.subnet = subnet;
  }

  public List<String> getZones() {
    return zones;
  }

  public void setZones(List<String> zones) {
    this.zones = zones;
  }

  public List<String> getSecurityGroups() {
    return securityGroups;
  }

  public void setSecurityGroups(List<String> securityGroups) {
    this.securityGroups = securityGroups;
  }

  public List<String> getSecurityGroupNames() {
    return securityGroupNames;
  }

  public void setSecurityGroupNames(List<String> securityGroupNames) {
    this.securityGroupNames = securityGroupNames;
  }

  public List<String> getTargetGroups() {
    return targetGroups;
  }

  public void setTargetGroups(List<String> targetGroups) {
    this.targetGroups = targetGroups;
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

  public String getApplication() {
    return application;
  }

  public void setApplication(String application) {
    this.application = application;
  }

  public String getStack() {
    return stack;
  }

  public void setStack(String stack) {
    this.stack = stack;
  }

  public String getFreeFormDetails() {
    return freeFormDetails;
  }

  public void setFreeFormDetails(String freeFormDetails) {
    this.freeFormDetails = freeFormDetails;
  }

  public String getImageId() {
    return imageId;
  }

  public void setImageId(String imageId) {
    this.imageId = imageId;
  }

  public Capacity getCapacity() {
    return capacity;
  }

  public void setCapacity(Capacity capacity) {
    this.capacity = capacity;
  }

  public Resources getResources() {
    return resources;
  }

  public void setResources(Resources resources) {
    this.resources = resources;
  }

  public Map<String, String> getEnv() {
    return env;
  }

  public void setEnv(Map<String, String> env) {
    this.env = env;
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

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public Boolean getInService() {
    return inService;
  }

  public void setInService(Boolean inService) {
    this.inService = inService;
  }

  public String getJobType() {
    return jobType;
  }

  public void setJobType(String jobType) {
    this.jobType = jobType;
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

  public List<String> getInterestingHealthProviderNames() {
    return interestingHealthProviderNames;
  }

  public void setInterestingHealthProviderNames(List<String> interestingHealthProviderNames) {
    this.interestingHealthProviderNames = interestingHealthProviderNames;
  }

  public MigrationPolicy getMigrationPolicy() {
    return migrationPolicy;
  }

  public void setMigrationPolicy(MigrationPolicy migrationPolicy) {
    this.migrationPolicy = migrationPolicy;
  }

  public Boolean getCopySourceScalingPoliciesAndActions() {
    return copySourceScalingPoliciesAndActions;
  }

  public void setCopySourceScalingPoliciesAndActions(Boolean copySourceScalingPoliciesAndActions) {
    this.copySourceScalingPoliciesAndActions = copySourceScalingPoliciesAndActions;
  }

  public Integer getSequence() {
    return sequence;
  }

  public void setSequence(Integer sequence) {
    this.sequence = sequence;
  }

  public DisruptionBudget getDisruptionBudget() {
    return disruptionBudget;
  }

  public void setDisruptionBudget(DisruptionBudget disruptionBudget) {
    this.disruptionBudget = disruptionBudget;
  }

  public SubmitJobRequest.Constraints getConstraints() {
    return constraints;
  }

  public void setConstraints(SubmitJobRequest.Constraints constraints) {
    this.constraints = constraints;
  }

  public ServiceJobProcesses getServiceJobProcesses() {
    return serviceJobProcesses;
  }

  public void setServiceJobProcesses(ServiceJobProcesses serviceJobProcesses) {
    this.serviceJobProcesses = serviceJobProcesses;
  }

  public boolean isUseApplicationDefaultSecurityGroup() {
    return useApplicationDefaultSecurityGroup;
  }

  public void setUseApplicationDefaultSecurityGroup(boolean useApplicationDefaultSecurityGroup) {
    this.useApplicationDefaultSecurityGroup = useApplicationDefaultSecurityGroup;
  }

  public boolean isCopySourceScalingPolicies() {
    return copySourceScalingPolicies;
  }

  public void setCopySourceScalingPolicies(boolean copySourceScalingPolicies) {
    this.copySourceScalingPolicies = copySourceScalingPolicies;
  }

  @Override
  public List<OperationEvent> getEvents() {
    return events;
  }

  public void setEvents(List<OperationEvent> events) {
    this.events = events;
  }

  public Source getSource() {
    return source;
  }

  public void setSource(Source source) {
    this.source = source;
  }

  public Efs getEfs() {
    return efs;
  }

  public void setEfs(Efs efs) {
    this.efs = efs;
  }
}
