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
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DestroyTitusJobDescription;
import java.util.Collections;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ResolveTitusJobId implements SagaAction<ResolveTitusJobId.ResolveTitusJobIdCommand> {
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final TitusClientProvider titusClientProvider;

  @Autowired
  public ResolveTitusJobId(
      AccountCredentialsProvider accountCredentialsProvider,
      TitusClientProvider titusClientProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.titusClientProvider = titusClientProvider;
  }

  @NotNull
  @Override
  public Result apply(
      @NotNull ResolveTitusJobId.ResolveTitusJobIdCommand command, @NotNull Saga saga) {
    AccountCredentials accountCredentials =
        accountCredentialsProvider.getCredentials(command.getAccount());

    TitusClient titusClient =
        titusClientProvider.getTitusClient(
            (NetflixTitusCredentials) accountCredentials, command.getRegion());

    Job job = titusClient.findJobByName(command.getServerGroupName());
    if (job != null) {
      DestroyTitusJobDescription destroyTitusJobDescription = new DestroyTitusJobDescription();
      destroyTitusJobDescription.setAccount(command.getAccount());
      destroyTitusJobDescription.setRegion(command.getRegion());
      destroyTitusJobDescription.setJobId(job.getId());
      destroyTitusJobDescription.setServerGroupName(job.getName());
      destroyTitusJobDescription.setUser(command.getUser());

      return new Result(
          DestroyTitusJob.DestroyTitusJobCommand.builder()
              .description(destroyTitusJobDescription)
              .build(),
          Collections.emptyList());
    }

    return new Result();
  }

  @Builder(builderClassName = "ResolveTitusJobIdCommandBuilder", toBuilder = true)
  @JsonDeserialize(builder = ResolveTitusJobIdCommand.ResolveTitusJobIdCommandBuilder.class)
  @JsonTypeName("resolveTitusJobIdCommand")
  @Value
  public static class ResolveTitusJobIdCommand implements SagaCommand {
    @Nonnull String account;
    @Nonnull String region;
    @Nonnull String serverGroupName;
    String user;

    @NonFinal EventMetadata metadata;

    @Override
    public void setMetadata(EventMetadata metadata) {
      this.metadata = metadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class ResolveTitusJobIdCommandBuilder {}
  }
}
