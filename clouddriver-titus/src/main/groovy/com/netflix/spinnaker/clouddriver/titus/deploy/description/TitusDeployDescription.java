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
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class TitusDeployDescription extends AbstractTitusCredentialsDescription
    implements DeployDescription, ApplicationNameable, SagaContextAware {
  private String region;
  private String subnet;
  private List<String> zones = new ArrayList<>();
  private List<String> securityGroups = new ArrayList<>();
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
  private SagaContext sagaContext;

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

  @JsonIgnore
  @Override
  public void setSagaContext(SagaContext sagaContext) {
    this.sagaContext = sagaContext;
  }

  @JsonIgnore
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
            .serviceJobProcesses(serviceJobProcesses);

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
  }

  @Data
  public static class Source {
    private String account;
    private String region;
    private String asgName;
    private boolean useSourceCapacity;
  }
}
