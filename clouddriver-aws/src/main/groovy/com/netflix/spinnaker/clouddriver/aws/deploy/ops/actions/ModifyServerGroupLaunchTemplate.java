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

import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification;
import com.amazonaws.services.ec2.model.LaunchTemplateVersion;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.clouddriver.aws.deploy.BlockDeviceConfig;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.ModifyServerGroupLaunchTemplateAtomicOperation.LaunchTemplateException;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.actions.UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.services.LaunchTemplateService;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.event.EventMetadata;
import com.netflix.spinnaker.clouddriver.saga.SagaCommand;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import java.util.Collections;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class ModifyServerGroupLaunchTemplate
    implements SagaAction<ModifyServerGroupLaunchTemplate.ModifyServerGroupLaunchTemplateCommand> {
  private final BlockDeviceConfig blockDeviceConfig;
  private final AccountCredentialsRepository credentialsRepository;
  private final RegionScopedProviderFactory regionScopedProviderFactory;

  public ModifyServerGroupLaunchTemplate(
      BlockDeviceConfig blockDeviceConfig,
      AccountCredentialsRepository credentialsRepository,
      RegionScopedProviderFactory regionScopedProviderFactory) {
    this.blockDeviceConfig = blockDeviceConfig;
    this.credentialsRepository = credentialsRepository;
    this.regionScopedProviderFactory = regionScopedProviderFactory;
  }

  @NotNull
  @Override
  public Result apply(@NotNull ModifyServerGroupLaunchTemplateCommand command, @NotNull Saga saga) {
    ModifyServerGroupLaunchTemplateDescription description = command.description;
    NetflixAmazonCredentials credentials =
        (NetflixAmazonCredentials) credentialsRepository.getOne(description.getAccount());
    RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider =
        regionScopedProviderFactory.forRegion(credentials, description.getRegion());

    LaunchTemplateService launchTemplateService = regionScopedProvider.getLaunchTemplateService();
    LaunchTemplateSpecification spec;
    try {
      LaunchTemplateVersion newVersion =
          launchTemplateService.modifyLaunchTemplate(
              credentials, description, command.sourceVersion);
      spec =
          new LaunchTemplateSpecification()
              .withLaunchTemplateId(newVersion.getLaunchTemplateId())
              .withVersion(String.valueOf(newVersion.getVersionNumber()));
    } catch (Exception e) {
      throw new LaunchTemplateException("Failed while preparing for launch template update", e);
    }

    UpdateAutoScalingGroupCommand updateCommand =
        UpdateAutoScalingGroupCommand.builder()
            .description(description)
            .launchTemplateSpecification(spec)
            .build();
    return new Result(updateCommand, Collections.emptyList());
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

    @NonFinal private EventMetadata metadata;

    @Override
    public void setMetadata(@NotNull EventMetadata eventMetadata) {
      this.metadata = eventMetadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class ModifyServerGroupLaunchTemplateCommandBuilder {}
  }
}
