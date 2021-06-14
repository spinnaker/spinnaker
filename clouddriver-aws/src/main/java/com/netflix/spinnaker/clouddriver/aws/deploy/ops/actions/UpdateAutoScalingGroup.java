/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.actions;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.InstancesDistribution;
import com.amazonaws.services.autoscaling.model.LaunchTemplate;
import com.amazonaws.services.autoscaling.model.LaunchTemplateOverrides;
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification;
import com.amazonaws.services.autoscaling.model.MixedInstancesPolicy;
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest;
import com.amazonaws.services.ec2.model.LaunchTemplateVersion;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.ModifyServerGroupLaunchTemplateAtomicOperation.LaunchTemplateException;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.services.LaunchTemplateService;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.event.EventMetadata;
import com.netflix.spinnaker.clouddriver.saga.SagaCommand;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/** Action to update an AWS EC2 Auto Scaling Group. */
@Slf4j
@Component
public class UpdateAutoScalingGroup
    implements SagaAction<UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand> {
  private final RegionScopedProviderFactory regionScopedProviderFactory;
  private final CredentialsRepository<NetflixAmazonCredentials> credentialsRepository;

  public UpdateAutoScalingGroup(
      RegionScopedProviderFactory regionScopedProviderFactory,
      CredentialsRepository<NetflixAmazonCredentials> credentialsRepository) {
    this.regionScopedProviderFactory = regionScopedProviderFactory;
    this.credentialsRepository = credentialsRepository;
  }

  @NotNull
  @Override
  public Result apply(
      @NotNull UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand command, @NotNull Saga saga) {
    ModifyServerGroupLaunchTemplateDescription description = command.description;
    NetflixAmazonCredentials credentials =
        (NetflixAmazonCredentials) credentialsRepository.getOne(description.getAccount());
    RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider =
        regionScopedProviderFactory.forRegion(credentials, description.getRegion());

    saga.log("[SAGA_ACTION] Updating EC2 Auto Scaling Group " + description.getAsgName());

    // build update request
    UpdateAutoScalingGroupRequest updateReq =
        new UpdateAutoScalingGroupRequest().withAutoScalingGroupName(description.getAsgName());

    AutoScalingGroup autoScalingGroup =
        getAutoScalingGroup(description.getAsgName(), regionScopedProvider);
    boolean isAsgBackedByMip = autoScalingGroup.getMixedInstancesPolicy() != null;

    String ltId = command.launchTemplateVersion.getLaunchTemplateId();
    String ltVersion = String.valueOf(command.launchTemplateVersion.getVersionNumber());

    if (isAsgBackedByMip || command.isReqToUpgradeAsgToMixedInstancesPolicy) {
      final MixedInstancesPolicy mip =
          new MixedInstancesPolicy()
              .withLaunchTemplate(
                  new LaunchTemplate()
                      .withLaunchTemplateSpecification(
                          new LaunchTemplateSpecification()
                              .withLaunchTemplateId(ltId)
                              .withVersion(ltVersion))
                      .withOverrides(command.launchTemplateOverrides))
              .withInstancesDistribution(
                  new InstancesDistribution()
                      .withOnDemandAllocationStrategy(description.getOnDemandAllocationStrategy())
                      .withOnDemandBaseCapacity(description.getOnDemandBaseCapacity())
                      .withOnDemandPercentageAboveBaseCapacity(
                          description.getOnDemandPercentageAboveBaseCapacity())
                      .withSpotAllocationStrategy(description.getSpotAllocationStrategy())
                      .withSpotInstancePools(description.getSpotInstancePools())
                      .withSpotMaxPrice(description.getSpotPrice()));

      updateReq.withMixedInstancesPolicy(mip);
    } else {
      updateReq.withLaunchTemplate(
          new LaunchTemplateSpecification().withLaunchTemplateId(ltId).withVersion(ltVersion));
    }

    try {
      regionScopedProvider.getAutoScaling().updateAutoScalingGroup(updateReq);
    } catch (Exception e) {
      StringBuilder exceptionMsg =
          new StringBuilder(
              String.format("Failed to update server group %s.", description.getAsgName()));
      if (StringUtils.isNotBlank(e.getMessage())) {
        exceptionMsg.append(String.format("Error: %s\n", e.getMessage()));
      }

      try {
        // Clean up newly created launch template version by the Saga Flow, in order to keep the
        // latest version unaltered.
        // This step is required because only the default and latest launch template versions for a
        // launch template are cached.
        // Not cleaning up will result in Internal Server Error for Clouddriver API requests and
        // subsequent Deck errors.
        if (command.getNewLaunchTemplateVersionNumber() != null) {
          saga.log("[SAGA_ACTION] Cleaning up to keep the operation atomic.");
          cleanUpOnFailure(
              regionScopedProvider.getLaunchTemplateService(),
              command.getLaunchTemplateVersion().getLaunchTemplateId(),
              command.getNewLaunchTemplateVersionNumber());
        }
      } catch (Exception ex) {
        exceptionMsg.append(
            "Failed to clean up launch template version! Error: " + ex.getMessage());
      }
      throw new LaunchTemplateException(exceptionMsg.toString(), e);
    }
    return new Result();
  }

  private AutoScalingGroup getAutoScalingGroup(
      String autoScalingGroupName,
      RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider) {
    try {
      return regionScopedProvider.getAsgService().getAutoScalingGroup(autoScalingGroupName);
    } catch (Exception e) {
      throw new LaunchTemplateException(
          String.format("Failed to get server group %s.", autoScalingGroupName), e);
    }
  }

  private void cleanUpOnFailure(
      LaunchTemplateService ltService, String launchTemplateId, Long ltVersionToDelete) {
    ltService.deleteLaunchTemplateVersion(launchTemplateId, ltVersionToDelete);
  }

  @Builder(builderClassName = "UpdateAutoScalingGroupCommandBuilder", toBuilder = true)
  @JsonDeserialize(
      builder = UpdateAutoScalingGroupCommand.UpdateAutoScalingGroupCommandBuilder.class)
  @JsonTypeName("updateAutoScalingGroupCommand")
  @Value
  public static class UpdateAutoScalingGroupCommand implements SagaCommand {
    @Nonnull private ModifyServerGroupLaunchTemplateDescription description;
    @Nonnull private LaunchTemplateVersion launchTemplateVersion;
    @Nullable private Long newLaunchTemplateVersionNumber;
    @Nullable private List<LaunchTemplateOverrides> launchTemplateOverrides;
    @Nonnull private Boolean isReqToUpgradeAsgToMixedInstancesPolicy;
    @NonFinal private EventMetadata metadata;

    @Override
    public void setMetadata(@NotNull EventMetadata metadata) {
      this.metadata = metadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class UpdateAutoScalingGroupCommandBuilder {}
  }
}
