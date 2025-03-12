/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.deploy.actions;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.clouddriver.event.EventMetadata;
import com.netflix.spinnaker.clouddriver.saga.SagaCommand;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient;
import com.netflix.spinnaker.clouddriver.titus.client.model.Job;
import com.netflix.spinnaker.clouddriver.titus.client.model.ResizeJobRequest;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.ResizeTitusServerGroupDescription;
import com.netflix.spinnaker.kork.exceptions.UserException;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ResizeTitusJob implements SagaAction<ResizeTitusJob.ResizeTitusJobCommand> {
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final TitusClientProvider titusClientProvider;

  @Autowired
  public ResizeTitusJob(
      AccountCredentialsProvider accountCredentialsProvider,
      TitusClientProvider titusClientProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.titusClientProvider = titusClientProvider;
  }

  @NotNull
  @Override
  public Result apply(@NotNull ResizeTitusJob.ResizeTitusJobCommand command, @NotNull Saga saga) {
    saga.log(
        "Resizing Titus Job %s:%s:%s",
        command.description.getAccount(),
        command.description.getRegion(),
        command.description.getServerGroupName());

    AccountCredentials accountCredentials =
        accountCredentialsProvider.getCredentials(command.description.getAccount());

    TitusClient titusClient =
        titusClientProvider.getTitusClient(
            (NetflixTitusCredentials) accountCredentials, command.description.getRegion());

    Job job = titusClient.findJobByName(command.description.getServerGroupName());
    if (job == null) {
      throw new UserException(
          "No titus server group named '" + command.description.getServerGroupName() + "' found");
    }

    boolean shouldToggleScalingFlags = !job.isInService();
    if (shouldToggleScalingFlags) {
      titusClient.setAutoscaleEnabled(job.getId(), true);
    }

    titusClient.resizeJob(
        (ResizeJobRequest)
            new ResizeJobRequest()
                .withInstancesDesired(command.description.getCapacity().getDesired())
                .withInstancesMin(command.description.getCapacity().getMin())
                .withInstancesMax(command.description.getCapacity().getMax())
                .withUser(command.description.getUser())
                .withJobId(job.getId()));

    if (shouldToggleScalingFlags) {
      titusClient.setAutoscaleEnabled(job.getId(), false);
    }

    saga.log(
        "Resized Titus Job %s:%s:%s",
        command.description.getAccount(),
        command.description.getRegion(),
        command.description.getServerGroupName());

    return new Result();
  }

  @Builder(builderClassName = "ResizeTitusJobCommandBuilder", toBuilder = true)
  @JsonDeserialize(
      builder = ResizeTitusJob.ResizeTitusJobCommand.ResizeTitusJobCommandBuilder.class)
  @JsonTypeName("resizeTitusJobCommand")
  @Value
  public static class ResizeTitusJobCommand implements SagaCommand {
    @Nonnull ResizeTitusServerGroupDescription description;

    @NonFinal EventMetadata metadata;

    @Override
    public void setMetadata(EventMetadata metadata) {
      this.metadata = metadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class ResizeTitusJobCommandBuilder {}
  }
}
