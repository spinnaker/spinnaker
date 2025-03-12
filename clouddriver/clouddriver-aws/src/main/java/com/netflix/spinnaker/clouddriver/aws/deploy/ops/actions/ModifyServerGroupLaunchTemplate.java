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
import java.util.Collections;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * Action to modify a EC2 launch template i.e. create a new version of the launch template with
 * requested changes. This action may be skipped if no launch template changes are requested.
 */
@Component
public class ModifyServerGroupLaunchTemplate
    implements SagaAction<ModifyServerGroupLaunchTemplate.ModifyServerGroupLaunchTemplateCommand> {
  private final CredentialsRepository<NetflixAmazonCredentials> credentialsRepository;
  private final RegionScopedProviderFactory regionScopedProviderFactory;

  public ModifyServerGroupLaunchTemplate(
      CredentialsRepository<NetflixAmazonCredentials> credentialsRepository,
      RegionScopedProviderFactory regionScopedProviderFactory) {
    this.credentialsRepository = credentialsRepository;
    this.regionScopedProviderFactory = regionScopedProviderFactory;
  }

  @NotNull
  @Override
  public Result apply(@NotNull ModifyServerGroupLaunchTemplateCommand command, @NotNull Saga saga) {
    ModifyServerGroupLaunchTemplateDescription description = command.description;

    if (!command.isReqToModifyLaunchTemplate) {
      saga.log(
          "[SAGA_ACTION] Skipping ModifyServerGroupLaunchTemplate as only mixed instances policy will be updated.");

      return new Result(
          PrepareUpdateAutoScalingGroup.PrepareUpdateAutoScalingGroupCommand.builder()
              .description(description)
              .launchTemplateVersion(command.sourceVersion)
              .newLaunchTemplateVersionNumber(null)
              .isReqToUpgradeAsgToMixedInstancesPolicy(
                  command.isReqToUpgradeAsgToMixedInstancesPolicy)
              .build(),
          Collections.emptyList());
    }

    NetflixAmazonCredentials credentials =
        (NetflixAmazonCredentials) credentialsRepository.getOne(description.getAccount());
    RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider =
        regionScopedProviderFactory.forRegion(credentials, description.getRegion());

    saga.log(
        "[SAGA_ACTION] Modifying launch template (i.e. creating a new version) for EC2 Auto Scaling Group "
            + description.getAsgName());

    LaunchTemplateService launchTemplateService = regionScopedProvider.getLaunchTemplateService();
    LaunchTemplateVersion newVersion;
    try {
      boolean shouldUseMixedInstancesPolicy =
          command.isAsgBackedByMixedInstancesPolicy
              || command.isReqToUpgradeAsgToMixedInstancesPolicy;
      newVersion =
          launchTemplateService.modifyLaunchTemplate(
              credentials, description, command.sourceVersion, shouldUseMixedInstancesPolicy);
    } catch (Exception e) {
      throw new LaunchTemplateException("Failed to modify launch template", e);
    }

    return new Result(
        PrepareUpdateAutoScalingGroup.PrepareUpdateAutoScalingGroupCommand.builder()
            .description(description)
            .launchTemplateVersion(newVersion)
            .newLaunchTemplateVersionNumber(newVersion.getVersionNumber())
            .isReqToUpgradeAsgToMixedInstancesPolicy(
                command.isReqToUpgradeAsgToMixedInstancesPolicy)
            .build(),
        Collections.emptyList());
  }

  @Builder(builderClassName = "ModifyServerGroupLaunchTemplateCommandBuilder", toBuilder = true)
  @JsonDeserialize(
      builder =
          ModifyServerGroupLaunchTemplateCommand.ModifyServerGroupLaunchTemplateCommandBuilder
              .class)
  @JsonTypeName("modifyServerGroupLaunchTemplateCommand")
  @Value
  public static class ModifyServerGroupLaunchTemplateCommand implements SagaCommand {
    @Nonnull private LaunchTemplateVersion sourceVersion;
    @Nonnull private ModifyServerGroupLaunchTemplateDescription description;
    @Nonnull private Boolean isReqToModifyLaunchTemplate;
    @Nonnull private Boolean isReqToUpgradeAsgToMixedInstancesPolicy;
    @Nonnull private Boolean isAsgBackedByMixedInstancesPolicy;
    @NonFinal private EventMetadata metadata;

    @Override
    public void setMetadata(@NotNull EventMetadata metadata) {
      this.metadata = metadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class ModifyServerGroupLaunchTemplateCommandBuilder {}
  }
}
