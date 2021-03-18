/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.titus.deploy.actions;

import static java.lang.String.format;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.event.EventMetadata;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.orchestration.sagas.LoadFront50App;
import com.netflix.spinnaker.clouddriver.orchestration.sagas.LoadFront50App.Front50AppAware;
import com.netflix.spinnaker.clouddriver.saga.SagaCommand;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.titus.JobType;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusException;
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil;
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient;
import com.netflix.spinnaker.clouddriver.titus.client.model.DisruptionBudget;
import com.netflix.spinnaker.clouddriver.titus.client.model.Job;
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.AvailabilityPercentageLimit;
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.ContainerHealthProvider;
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.HourlyTimeWindow;
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.RatePercentagePerInterval;
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.TimeWindow;
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.SubmitTitusJob.SubmitTitusJobCommand;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.exceptions.JobNotFoundException;
import com.netflix.spinnaker.clouddriver.titus.model.DockerImage;
import com.netflix.spinnaker.config.AwsConfiguration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PrepareTitusDeploy extends AbstractTitusDeployAction
    implements SagaAction<PrepareTitusDeploy.PrepareTitusDeployCommand> {
  private static final Logger log = LoggerFactory.getLogger(PrepareTitusDeploy.class);

  private static final TimeWindow DEFAULT_SYSTEM_TIME_WINDOW =
      new TimeWindow(
          Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday"),
          Collections.singletonList(new HourlyTimeWindow(10, 16)),
          "PST");
  private static final String USE_APPLICATION_DEFAULT_SG_LABEL =
      "spinnaker.useApplicationDefaultSecurityGroup";
  private static final String SKIP_SECURITY_GROUP_VALIDATION_LABEL =
      "spinnaker.skipSecurityGroupValidation";
  private static final String LABEL_TARGET_GROUPS = "spinnaker.targetGroups";
  private static final String SPINNAKER_ACCOUNT_ENV_VAR = "SPINNAKER_ACCOUNT";

  private final AwsLookupUtil awsLookupUtil;
  private final RegionScopedProviderFactory regionScopedProviderFactory;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final AwsConfiguration.DeployDefaults deployDefaults;
  private final TargetGroupLookupHelper targetGroupLookupHelper;

  @Autowired
  public PrepareTitusDeploy(
      AccountCredentialsRepository accountCredentialsRepository,
      TitusClientProvider titusClientProvider,
      AwsLookupUtil awsLookupUtil,
      RegionScopedProviderFactory regionScopedProviderFactory,
      AccountCredentialsProvider accountCredentialsProvider,
      AwsConfiguration.DeployDefaults deployDefaults,
      Optional<TargetGroupLookupHelper> targetGroupLookupHelper) {
    super(accountCredentialsRepository, titusClientProvider);
    this.awsLookupUtil = awsLookupUtil;
    this.regionScopedProviderFactory = regionScopedProviderFactory;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.deployDefaults = deployDefaults;
    this.targetGroupLookupHelper = targetGroupLookupHelper.orElse(new TargetGroupLookupHelper());
  }

  private static <T> T orDefault(T input, T defaultValue) {
    return (input == null) ? defaultValue : input;
  }

  private static int orDefault(int input, int defaultValue) {
    if (input == 0) {
      return defaultValue;
    }
    return input;
  }

  @Nonnull
  @Override
  public Result apply(@Nonnull PrepareTitusDeployCommand command, @Nonnull Saga saga) {
    final TitusDeployDescription description = command.description;

    prepareDeployDescription(description);

    final TitusClient titusClient =
        titusClientProvider.getTitusClient(
            description.getCredentials(), command.description.getRegion());

    final LoadFront50App.Front50App front50App = command.getFront50App();

    final String asgName = description.getSource().getAsgName();
    if (!isNullOrEmpty(asgName)) {
      log.trace("Source present, getting details: {}", asgName);
      mergeSourceDetailsIntoDescription(saga, description, front50App);
    } else {
      configureDisruptionBudget(description, null, front50App);
    }

    saga.log(
        "Preparing deployment to %s:%s:%s",
        description.getAccount(),
        description.getRegion(),
        isNullOrEmpty(description.getSubnet()) ? "" : ":" + description.getSubnet());

    DockerImage dockerImage = new DockerImage(description.getImageId());

    if (!isNullOrEmpty(description.getInterestingHealthProviderNames())) {
      description
          .getLabels()
          .put(
              "interestingHealthProviderNames",
              String.join(",", description.getInterestingHealthProviderNames()));
    }
    if (!isNullOrEmpty(description.getResources().getSignedAddressAllocations())) {
      description
          .getResources()
          .setSignedAddressAllocations(description.getResources().getSignedAddressAllocations());
    }

    resolveSecurityGroups(saga, description);

    setSpinnakerAccountEnvVar(description);

    TargetGroupLookupHelper.TargetGroupLookupResult targetGroupLookupResult = null;
    if (!description.getTargetGroups().isEmpty()) {
      targetGroupLookupResult = validateLoadBalancers(description);
      if (targetGroupLookupResult != null) {
        description
            .getLabels()
            .put(
                LABEL_TARGET_GROUPS,
                String.join(",", targetGroupLookupResult.getTargetGroupARNs()));
      }
    } else {
      description.getLabels().remove(LABEL_TARGET_GROUPS);
    }

    String nextServerGroupName = TitusJobNameResolver.resolveJobName(titusClient, description);
    saga.log("Resolved server group name to %s", nextServerGroupName);

    String user = resolveUser(front50App, description);

    return new Result(
        SubmitTitusJobCommand.builder()
            .description(description)
            .submitJobRequest(
                description.toSubmitJobRequest(dockerImage, nextServerGroupName, user))
            .nextServerGroupName(nextServerGroupName)
            .targetGroupLookupResult(targetGroupLookupResult)
            .build(),
        Collections.emptyList());
  }

  @Nullable
  private String resolveUser(
      LoadFront50App.Front50App front50App, TitusDeployDescription description) {
    if (front50App != null && !isNullOrEmpty(front50App.getEmail())) {
      return front50App.getEmail();
    } else if (!isNullOrEmpty(description.getUser())) {
      return description.getUser();
    }
    return null;
  }

  private void configureDisruptionBudget(
      TitusDeployDescription description, Job sourceJob, LoadFront50App.Front50App front50App) {
    if (description.getDisruptionBudget() == null) {
      // migrationPolicy should only be used when the disruptionBudget has not been specified
      description.setMigrationPolicy(
          orDefault(
              description.getMigrationPolicy(),
              (sourceJob == null) ? null : sourceJob.getMigrationPolicy()));

      // "systemDefault" should be treated as "no migrationPolicy"
      if (description.getMigrationPolicy() == null
          || "systemDefault".equals(description.getMigrationPolicy().getType())) {
        description.setDisruptionBudget(getDefaultDisruptionBudget(front50App));
      }
    }
  }

  private void mergeSourceDetailsIntoDescription(
      Saga saga, TitusDeployDescription description, LoadFront50App.Front50App front50App) {
    // If cluster name info was not provided, use the fields from the source asg.
    Names sourceName = Names.parseName(description.getSource().getAsgName());
    description.setApplication(
        description.getApplication() != null ? description.getApplication() : sourceName.getApp());
    description.setStack(
        description.getStack() != null ? description.getStack() : sourceName.getStack());
    description.setFreeFormDetails(
        description.getFreeFormDetails() != null
            ? description.getFreeFormDetails()
            : sourceName.getDetail());

    TitusDeployDescription.Source source = description.getSource();

    TitusClient sourceClient = buildSourceTitusClient(source);
    if (sourceClient == null) {
      throw new TitusException(
          "Unable to find a Titus client for deployment source: {}",
          description.getSource().getAsgName());
    }

    Job sourceJob = sourceClient.findJobByName(source.getAsgName());
    if (sourceJob == null) {
      throw new JobNotFoundException(
          format(
              "Unable to locate source (%s:%s:%s)",
              source.getAccount(), source.getRegion(), source.getAsgName()));
    }

    saga.log(
        format(
            "Copying deployment details from (%s:%s:%s)",
            source.getAccount(), source.getRegion(), source.getAsgName()));

    if (isNullOrEmpty(description.getSecurityGroups())) {
      description.setSecurityGroups(sourceJob.getSecurityGroups());
    }
    if (isNullOrEmpty(description.getImageId())) {
      String imageVersion =
          (sourceJob.getVersion() == null) ? sourceJob.getDigest() : sourceJob.getVersion();
      description.setImageId(format("%s:%s", sourceJob.getApplicationName(), imageVersion));
    }

    if (description.getSource() != null && description.getSource().isUseSourceCapacity()) {
      description.getCapacity().setMin(sourceJob.getInstancesMin());
      description.getCapacity().setMax(sourceJob.getInstancesMax());
      description.getCapacity().setDesired(sourceJob.getInstancesDesired());
    }

    description
        .getResources()
        .setAllocateIpAddress(
            orDefault(
                description.getResources().isAllocateIpAddress(), sourceJob.isAllocateIpAddress()));
    description
        .getResources()
        .setCpu(orDefault(description.getResources().getCpu(), sourceJob.getCpu()));
    description
        .getResources()
        .setDisk(orDefault(description.getResources().getDisk(), sourceJob.getDisk()));
    description
        .getResources()
        .setGpu(orDefault(description.getResources().getGpu(), sourceJob.getGpu()));
    description
        .getResources()
        .setMemory(orDefault(description.getResources().getMemory(), sourceJob.getMemory()));
    description
        .getResources()
        .setNetworkMbps(
            orDefault(description.getResources().getNetworkMbps(), sourceJob.getNetworkMbps()));

    // Fallback to source allocations if request does not include allocations
    description
        .getResources()
        .setSignedAddressAllocations(
            orDefault(
                description.getResources().getSignedAddressAllocations(),
                sourceJob.getSignedAddressAllocations()));

    description.setRetries(orDefault(description.getRetries(), sourceJob.getRetries()));
    description.setRuntimeLimitSecs(
        orDefault(description.getRuntimeLimitSecs(), sourceJob.getRuntimeLimitSecs()));
    description.setEfs(orDefault(description.getEfs(), sourceJob.getEfs()));
    description.setEntryPoint(orDefault(description.getEntryPoint(), sourceJob.getEntryPoint()));
    description.setCmd(orDefault(description.getCmd(), sourceJob.getCmd()));
    description.setIamProfile(orDefault(description.getIamProfile(), sourceJob.getIamProfile()));
    description.setCapacityGroup(
        orDefault(description.getCapacityGroup(), sourceJob.getCapacityGroup()));
    description.setInService(orDefault(description.getInService(), sourceJob.isInService()));
    description.setJobType(orDefault(description.getJobType(), JobType.SERVICE.value()));

    if (isNullOrEmpty(description.getLabels())) {
      description.getLabels().putAll(sourceJob.getLabels());
    }
    if (isNullOrEmpty(description.getEnv())) {
      description.getEnv().putAll(sourceJob.getEnvironment());
    }
    if (isNullOrEmpty(description.getContainerAttributes())) {
      description.getContainerAttributes().putAll(sourceJob.getContainerAttributes());
    }

    configureDisruptionBudget(description, sourceJob, front50App);

    if (isNullOrEmpty(description.getHardConstraints())) {
      description.setHardConstraints(new ArrayList<>());
    }
    if (isNullOrEmpty(description.getSoftConstraints())) {
      description.setSoftConstraints(new ArrayList<>());
    }
    if (description.getSoftConstraints().isEmpty() && !sourceJob.getSoftConstraints().isEmpty()) {
      sourceJob
          .getSoftConstraints()
          .forEach(
              softConstraint -> {
                if (!description.getHardConstraints().contains(softConstraint)) {
                  description.getSoftConstraints().add(softConstraint);
                }
              });
    }
    if (description.getHardConstraints().isEmpty() && !sourceJob.getHardConstraints().isEmpty()) {
      sourceJob
          .getHardConstraints()
          .forEach(
              hardConstraint -> {
                if (!description.getSoftConstraints().contains(hardConstraint)) {
                  description.getHardConstraints().add(hardConstraint);
                }
              });
    }
  }

  // Sets an env variable that can be accessed within the task (container) which maps to the
  // spinnaker account
  private void setSpinnakerAccountEnvVar(TitusDeployDescription description) {
    if (description.getEnv().get(SPINNAKER_ACCOUNT_ENV_VAR) == null) {
      Map existingEnvVars = description.getEnv();
      existingEnvVars.put(SPINNAKER_ACCOUNT_ENV_VAR, description.getAccount());
      description.setEnv(existingEnvVars);
    }
  }

  @Nonnull
  private DisruptionBudget getDefaultDisruptionBudget(LoadFront50App.Front50App front50App) {
    DisruptionBudget budget = new DisruptionBudget();
    budget.setAvailabilityPercentageLimit(new AvailabilityPercentageLimit(95));
    budget.setRatePercentagePerInterval(new RatePercentagePerInterval(600_000, 5));
    budget.setTimeWindows(Collections.singletonList(DEFAULT_SYSTEM_TIME_WINDOW));

    if (front50App != null && front50App.isPlatformHealthOnly()) {
      budget.setContainerHealthProviders(
          Collections.singletonList(new ContainerHealthProvider("eureka")));
    }

    return budget;
  }

  @Nullable
  private TargetGroupLookupHelper.TargetGroupLookupResult validateLoadBalancers(
      TitusDeployDescription description) {
    if (description.getTargetGroups().isEmpty()) {
      return null;
    }

    RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider =
        regionScopedProviderFactory.forRegion(
            (NetflixAmazonCredentials)
                accountCredentialsProvider.getCredentials(
                    description.getCredentials().getAwsAccount()),
            description.getRegion());

    TargetGroupLookupHelper.TargetGroupLookupResult targetGroups =
        targetGroupLookupHelper.getTargetGroupsByName(
            regionScopedProvider, description.getTargetGroups());
    if (!targetGroups.getUnknownTargetGroups().isEmpty()) {
      throw new TargetGroupsNotFoundException(
          format(
              "Unable to find Target Groups: %s",
              String.join(", ", targetGroups.getUnknownTargetGroups())));
    }

    return targetGroups;
  }

  private void resolveSecurityGroups(Saga saga, TitusDeployDescription description) {
    saga.log("Resolving security groups");

    // Determine if we should configure the app default security group...
    // First check for a label, falling back to the value (if any) passed via the description.
    boolean useApplicationDefaultSecurityGroup =
        Boolean.valueOf(
            description
                .getLabels()
                .getOrDefault(
                    USE_APPLICATION_DEFAULT_SG_LABEL,
                    String.valueOf(description.isUseApplicationDefaultSecurityGroup())));
    if (!useApplicationDefaultSecurityGroup) {
      description.getLabels().put(USE_APPLICATION_DEFAULT_SG_LABEL, "false");
    } else {
      description.getLabels().remove(USE_APPLICATION_DEFAULT_SG_LABEL);
    }
    description.setUseApplicationDefaultSecurityGroup(useApplicationDefaultSecurityGroup);

    // Resolve the provided security groups, asserting that they actually exist.
    // TODO(rz): Seems kinda odd that we'd do resolution & validation here and not in... a validator
    // or preprocessor?
    Set<String> securityGroups = new HashSet<>();
    // TODO(aravindd) Used to skip validation for cross account SG's
    // Remove this workaround when we have support for multi account setup
    boolean skipSecurityGroupValidation =
        Boolean.valueOf(
            description
                .getLabels()
                .getOrDefault(SKIP_SECURITY_GROUP_VALIDATION_LABEL, String.valueOf(false)));
    if (skipSecurityGroupValidation) {
      saga.log("Skipping Security Group Validation");
      description
          .getSecurityGroups()
          .forEach(providedSecurityGroup -> securityGroups.add(providedSecurityGroup));
    } else {
      description
          .getSecurityGroups()
          .forEach(
              providedSecurityGroup -> {
                saga.log("Resolving Security Group '%s'", providedSecurityGroup);

                if (awsLookupUtil.securityGroupIdExists(
                    description.getAccount(), description.getRegion(), providedSecurityGroup)) {
                  securityGroups.add(providedSecurityGroup);
                } else {
                  String convertedSecurityGroup =
                      awsLookupUtil.convertSecurityGroupNameToId(
                          description.getAccount(), description.getRegion(), providedSecurityGroup);
                  if (isNullOrEmpty(convertedSecurityGroup)) {
                    throw new SecurityGroupNotFoundException(
                        format("Security Group '%s' cannot be found", providedSecurityGroup));
                  }
                  securityGroups.add(convertedSecurityGroup);
                }
              });

      if (deployDefaults.getAddAppGroupToServerGroup()
          && securityGroups.size() < deployDefaults.getMaxSecurityGroups()
          && useApplicationDefaultSecurityGroup) {
        String applicationSecurityGroup =
            awsLookupUtil.convertSecurityGroupNameToId(
                description.getAccount(), description.getRegion(), description.getApplication());
        if (isNullOrEmpty(applicationSecurityGroup)) {
          applicationSecurityGroup =
              (String)
                  OperationPoller.retryWithBackoff(
                      op ->
                          awsLookupUtil.createSecurityGroupForApplication(
                              description.getAccount(),
                              description.getRegion(),
                              description.getApplication()),
                      1_000,
                      5);
        }
        securityGroups.add(applicationSecurityGroup);
      }
    }

    if (!securityGroups.isEmpty()) {
      description.setSecurityGroups(Lists.newArrayList(securityGroups));
    }

    saga.log(
        "Finished resolving security groups: {}",
        Joiner.on(",").join(description.getSecurityGroups()));
  }

  @Builder(builderClassName = "PrepareTitusDeployCommandBuilder", toBuilder = true)
  @JsonDeserialize(builder = PrepareTitusDeployCommand.PrepareTitusDeployCommandBuilder.class)
  @JsonTypeName("prepareTitusDeployCommand")
  @Value
  public static class PrepareTitusDeployCommand implements SagaCommand, Front50AppAware {
    private TitusDeployDescription description;
    @NonFinal private LoadFront50App.Front50App front50App;
    @NonFinal private EventMetadata metadata;

    @Override
    public void setFront50App(LoadFront50App.Front50App front50App) {
      this.front50App = front50App;
    }

    @Override
    public void setMetadata(EventMetadata metadata) {
      this.metadata = metadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class PrepareTitusDeployCommandBuilder {}
  }

  private static class SecurityGroupNotFoundException extends TitusException {
    SecurityGroupNotFoundException(String message) {
      super(message);
      setRetryable(true);
    }
  }

  private static class TargetGroupsNotFoundException extends TitusException {
    TargetGroupsNotFoundException(String message) {
      super(message);
      setRetryable(true);
    }
  }
}
