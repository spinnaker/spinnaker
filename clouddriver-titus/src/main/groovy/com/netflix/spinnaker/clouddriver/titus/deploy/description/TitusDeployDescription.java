package com.netflix.spinnaker.clouddriver.titus.deploy.description;

import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class TitusDeployDescription extends AbstractTitusCredentialsDescription
    implements DeployDescription, ApplicationNameable {
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

  @Nonnull
  public SubmitJobRequest toSubmitJobRequest(@Nonnull DockerImage dockerImage) {
    final SubmitJobRequest submitJobRequest =
        new SubmitJobRequest()
            .withApplication(application)
            .withDockerImageName(dockerImage.getImageName())
            .withInstancesMin(capacity.getMin())
            .withInstancesMax(capacity.getMax())
            .withInstancesDesired(capacity.getDesired())
            .withCpu(resources.getCpu())
            .withMemory(resources.getMemory())
            .withSharedMemory(resources.getSharedMemory())
            .withDisk(resources.getDisk())
            .withRetries(retries)
            .withRuntimeLimitSecs(runtimeLimitSecs)
            .withGpu(resources.getGpu())
            .withNetworkMbps(resources.getNetworkMbps())
            .withEfs(efs)
            .withPorts(resources.getPorts())
            .withEnv(env)
            .withAllocateIpAddress(resources.isAllocateIpAddress())
            .withStack(stack)
            .withDetail(freeFormDetails)
            .withEntryPoint(entryPoint)
            .withIamProfile(iamProfile)
            .withCapacityGroup(capacityGroup)
            .withLabels(labels)
            .withInService(inService)
            .withMigrationPolicy(migrationPolicy)
            .withCredentials(getCredentials().getName())
            .withContainerAttributes(containerAttributes)
            .withDisruptionBudget(disruptionBudget)
            .withServiceJobProcesses(serviceJobProcesses);

    if (!securityGroups.isEmpty()) {
      submitJobRequest.withSecurityGroups(securityGroups);
    }

    if (dockerImage.getImageDigest() != null) {
      submitJobRequest.withDockerDigest(dockerImage.getImageDigest());
    } else {
      submitJobRequest.withDockerImageVersion(dockerImage.getImageVersion());
    }

    /**
     * Titus api now supports the ability to set key/value for hard & soft constraints, but the
     * original interface we supported was just a list of keys, to make this change backwards
     * compatible we give preference to they\ constraints key/value map vs soft & hard constraints
     * list
     */
    if (constraints.getHard() != null || constraints.getSoft() != null) {
      submitJobRequest.withConstraints(constraints);
    } else {
      log.warn("Use of deprecated constraints payload: {}-{}", application, stack);
      if (hardConstraints != null) {
        hardConstraints.forEach(
            c -> submitJobRequest.withConstraint(SubmitJobRequest.Constraint.hard(c)));
      }
      if (softConstraints != null) {
        softConstraints.forEach(
            c -> submitJobRequest.withConstraint(SubmitJobRequest.Constraint.soft(c)));
      }
    }

    if (jobType != null) {
      submitJobRequest.withJobType(jobType);
    }

    return submitJobRequest;
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
