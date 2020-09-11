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
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.ModifyServerGroupLaunchTemplateAtomicOperation.LaunchTemplateException;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.event.EventMetadata;
import com.netflix.spinnaker.clouddriver.saga.SagaCommand;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class UpdateAutoScalingGroup
    implements SagaAction<UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand> {
  private final RegionScopedProviderFactory regionScopedProviderFactory;
  private final AccountCredentialsRepository credentialsRepository;

  public UpdateAutoScalingGroup(
      RegionScopedProviderFactory regionScopedProviderFactory,
      AccountCredentialsRepository credentialsRepository) {
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
    try {
      regionScopedProvider
          .getAutoScaling()
          .updateAutoScalingGroup(
              new UpdateAutoScalingGroupRequest()
                  .withAutoScalingGroupName(description.getAsgName())
                  .withLaunchTemplate(command.launchTemplateSpecification));
    } catch (Exception e) {
      throw new LaunchTemplateException(
          String.format("Failed to update server group %s", description.getAsgName()), e);
    }

    return new Result();
  }

  @Builder(builderClassName = "UpdateAutoScalingGroupCommandBuilder", toBuilder = true)
  @JsonDeserialize(
      builder = UpdateAutoScalingGroupCommand.UpdateAutoScalingGroupCommandBuilder.class)
  @JsonTypeName("updateAutoScalingGroupCommand")
  @Value
  public static class UpdateAutoScalingGroupCommand implements SagaCommand {
    @Nonnull private LaunchTemplateSpecification launchTemplateSpecification;
    @Nonnull private ModifyServerGroupLaunchTemplateDescription description;

    @NonFinal private EventMetadata metadata;

    @Override
    public void setMetadata(@NotNull EventMetadata eventMetadata) {
      this.metadata = eventMetadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class UpdateAutoScalingGroupCommandBuilder {}
  }
}
