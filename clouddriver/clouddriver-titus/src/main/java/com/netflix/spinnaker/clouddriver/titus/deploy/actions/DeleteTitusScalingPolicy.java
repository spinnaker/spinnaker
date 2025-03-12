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
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.client.TitusAutoscalingClient;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DeleteTitusScalingPolicyDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusScalingPolicyDeleted;
import com.netflix.spinnaker.kork.exceptions.UserException;
import com.netflix.titus.grpc.protogen.DeletePolicyRequest;
import com.netflix.titus.grpc.protogen.ScalingPolicyID;
import java.util.Collections;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class DeleteTitusScalingPolicy
    implements SagaAction<DeleteTitusScalingPolicy.DeleteTitusScalingPolicyCommand> {

  private final TitusClientProvider titusClientProvider;
  final AccountCredentialsRepository accountCredentialsRepository;

  public DeleteTitusScalingPolicy(
      TitusClientProvider titusClientProvider,
      AccountCredentialsRepository accountCredentialsRepository) {
    this.titusClientProvider = titusClientProvider;
    this.accountCredentialsRepository = accountCredentialsRepository;
  }

  @NotNull
  @Override
  public Result apply(
      @NotNull DeleteTitusScalingPolicy.DeleteTitusScalingPolicyCommand command,
      @NotNull Saga saga) {
    saga.log("Initializing Delete Scaling Policy " + command.description.getScalingPolicyID());
    AccountCredentials accountCredentials =
        accountCredentialsRepository.getOne(command.description.getAccount());

    TitusAutoscalingClient client =
        titusClientProvider.getTitusAutoscalingClient(
            (NetflixTitusCredentials) accountCredentials, command.description.getRegion());
    if (client == null) {
      throw new UserException(
              new UnsupportedOperationException(
                  "Autoscaling is not supported for this account/region"))
          .setRetryable(false);
    }

    ScalingPolicyID id =
        ScalingPolicyID.newBuilder().setId(command.description.getScalingPolicyID()).build();

    client.deleteScalingPolicy(DeletePolicyRequest.newBuilder().setId(id).build());

    saga.log("Deleted Scaling Policy " + command.description.getScalingPolicyID());

    return new Result(
        null,
        Collections.singletonList(
            TitusScalingPolicyDeleted.builder()
                .region(command.description.getRegion())
                .policyId(command.description.getScalingPolicyID())
                .build()));
  }

  @Builder(builderClassName = "DeleteTitusScalingPolicyCommandBuilder", toBuilder = true)
  @JsonDeserialize(
      builder = DeleteTitusScalingPolicyCommand.DeleteTitusScalingPolicyCommandBuilder.class)
  @JsonTypeName("deleteTitusScalingPolicyCommand")
  @Value
  public static class DeleteTitusScalingPolicyCommand implements SagaCommand {
    @Nonnull private DeleteTitusScalingPolicyDescription description;

    @NonFinal private EventMetadata metadata;

    @Override
    public void setMetadata(EventMetadata metadata) {
      this.metadata = metadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class DeleteTitusScalingPolicyCommandBuilder {}
  }
}
