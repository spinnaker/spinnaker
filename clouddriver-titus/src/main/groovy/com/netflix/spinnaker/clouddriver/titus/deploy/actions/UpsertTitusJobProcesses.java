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
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.ServiceJobProcessesRequest;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

public class UpsertTitusJobProcesses
    implements SagaAction<UpsertTitusJobProcesses.UpsertTitusJobProcessesCommand> {
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final TitusClientProvider titusClientProvider;

  @Autowired
  public UpsertTitusJobProcesses(
      AccountCredentialsProvider accountCredentialsProvider,
      TitusClientProvider titusClientProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.titusClientProvider = titusClientProvider;
  }

  @NotNull
  @Override
  public Result apply(
      @NotNull UpsertTitusJobProcesses.UpsertTitusJobProcessesCommand command, @NotNull Saga saga) {
    saga.log(
        "Updating Titus Job Processes %s:%s:%s",
        command.description.getAccount(),
        command.description.getRegion(),
        command.description.getJobId());

    AccountCredentials accountCredentials =
        accountCredentialsProvider.getCredentials(command.description.getAccount());

    TitusClient titusClient =
        titusClientProvider.getTitusClient(
            (NetflixTitusCredentials) accountCredentials, command.description.getRegion());

    titusClient.updateScalingProcesses(command.description);

    saga.log(
        "Updated Titus Job Processes %s:%s:%s",
        command.description.getAccount(),
        command.description.getRegion(),
        command.description.getJobId());

    return new Result();
  }

  @Builder(builderClassName = "UpsertTitusJobProcessesCommandBuilder", toBuilder = true)
  @JsonDeserialize(
      builder =
          UpsertTitusJobProcesses.UpsertTitusJobProcessesCommand
              .UpsertTitusJobProcessesCommandBuilder.class)
  @JsonTypeName("upsertTitusJobProcessesCommand")
  @Value
  public static class UpsertTitusJobProcessesCommand implements SagaCommand {
    @Nonnull ServiceJobProcessesRequest description;

    @NonFinal EventMetadata metadata;

    @Override
    public void setMetadata(EventMetadata metadata) {
      this.metadata = metadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class UpsertTitusJobProcessesCommandBuilder {}
  }
}
