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

import com.amazonaws.services.autoscaling.model.LaunchTemplateOverrides;
import com.amazonaws.services.ec2.model.LaunchTemplateVersion;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgConfigHelper;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.validators.ModifyServerGroupLaunchTemplateValidator;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidationErrors;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidationException;
import com.netflix.spinnaker.clouddriver.event.EventMetadata;
import com.netflix.spinnaker.clouddriver.saga.SagaCommand;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/** Action to prepare for AWS EC2 Auto Scaling Group update. */
@Component
public class PrepareUpdateAutoScalingGroup
    implements SagaAction<PrepareUpdateAutoScalingGroup.PrepareUpdateAutoScalingGroupCommand> {
  private final ModifyServerGroupLaunchTemplateValidator validator;

  public PrepareUpdateAutoScalingGroup(ModifyServerGroupLaunchTemplateValidator validator) {
    this.validator = validator;
  }

  @NotNull
  @Override
  public Result apply(@NotNull PrepareUpdateAutoScalingGroupCommand command, @NotNull Saga saga) {
    ModifyServerGroupLaunchTemplateDescription description = command.description;

    saga.log(
        "[SAGA_ACTION] Preparing to update EC2 Auto Scaling Group " + description.getAsgName());

    // validate description before proceeding with the update
    saga.log("[SAGA_ACTION] Validating configuration for modify");
    DescriptionValidationErrors validationErrors = new DescriptionValidationErrors(description);
    validator.validate(Collections.emptyList(), description, validationErrors);
    if (validationErrors.hasErrors()) {
      saga.log("[SAGA_ACTION] Validation failed with errors: " + validationErrors.toString());
      throw new DescriptionValidationException(validationErrors);
    }

    // transform overrides
    List<LaunchTemplateOverrides> ltOverrides =
        AsgConfigHelper.getLaunchTemplateOverrides(
            description.getLaunchTemplateOverridesForInstanceType());

    UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand updateCommand =
        UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand.builder()
            .description(description)
            .launchTemplateVersion(command.launchTemplateVersion)
            .launchTemplateOverrides(ltOverrides)
            .isReqToUpgradeAsgToMixedInstancesPolicy(
                command.isReqToUpgradeAsgToMixedInstancesPolicy)
            .newLaunchTemplateVersionNumber(command.newLaunchTemplateVersionNumber)
            .build();
    return new Result(updateCommand, Collections.emptyList());
  }

  @Builder(builderClassName = "PrepareUpdateAutoScalingGroupCommandBuilder", toBuilder = true)
  @JsonDeserialize(
      builder =
          PrepareUpdateAutoScalingGroupCommand.PrepareUpdateAutoScalingGroupCommandBuilder.class)
  @JsonTypeName("PrepareUpdateAutoScalingGroupCommand")
  @Value
  public static class PrepareUpdateAutoScalingGroupCommand implements SagaCommand {
    @Nonnull private ModifyServerGroupLaunchTemplateDescription description;
    @Nonnull private LaunchTemplateVersion launchTemplateVersion;
    @Nonnull private Boolean isReqToUpgradeAsgToMixedInstancesPolicy;
    @Nullable private Long newLaunchTemplateVersionNumber;
    @NonFinal private EventMetadata metadata;

    @Override
    public void setMetadata(@NotNull EventMetadata metadata) {
      this.metadata = metadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class PrepareUpdateAutoScalingGroupCommandBuilder {}
  }
}
