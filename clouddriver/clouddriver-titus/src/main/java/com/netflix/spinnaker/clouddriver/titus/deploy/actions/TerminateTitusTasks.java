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
import com.netflix.spinnaker.clouddriver.titus.client.model.TerminateTasksAndShrinkJobRequest;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TerminateTitusInstancesDescription;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TerminateTitusTasks
    implements SagaAction<TerminateTitusTasks.TerminateTitusTasksCommand> {
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final TitusClientProvider titusClientProvider;

  @Autowired
  public TerminateTitusTasks(
      AccountCredentialsProvider accountCredentialsProvider,
      TitusClientProvider titusClientProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.titusClientProvider = titusClientProvider;
  }

  @NotNull
  @Override
  public Result apply(
      @NotNull TerminateTitusTasks.TerminateTitusTasksCommand command, @NotNull Saga saga) {
    saga.log(
        "Terminating Titus Tasks %s:%s:%s",
        command.description.getAccount(),
        command.description.getRegion(),
        command.description.getInstanceIds());

    AccountCredentials accountCredentials =
        accountCredentialsProvider.getCredentials(command.description.getAccount());

    TitusClient titusClient =
        titusClientProvider.getTitusClient(
            (NetflixTitusCredentials) accountCredentials, command.description.getRegion());

    titusClient.terminateTasksAndShrink(
        new TerminateTasksAndShrinkJobRequest()
            .withTaskIds(command.description.getInstanceIds())
            .withShrink(false)
            .withUser(command.description.getUser()));

    saga.log(
        "Terminated Titus Instances %s:%s:%s",
        command.description.getAccount(),
        command.description.getRegion(),
        command.description.getInstanceIds());

    return new Result();
  }

  @Builder(builderClassName = "TerminateTitusTasksCommandBuilder", toBuilder = true)
  @JsonDeserialize(
      builder =
          TerminateTitusTasks.TerminateTitusTasksCommand.TerminateTitusTasksCommandBuilder.class)
  @JsonTypeName("terminateTitusTasksCommand")
  @Value
  public static class TerminateTitusTasksCommand implements SagaCommand {
    @Nonnull TerminateTitusInstancesDescription description;

    @NonFinal EventMetadata metadata;

    @Override
    public void setMetadata(EventMetadata metadata) {
      this.metadata = metadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class TerminateTitusTasksCommandBuilder {}
  }
}
