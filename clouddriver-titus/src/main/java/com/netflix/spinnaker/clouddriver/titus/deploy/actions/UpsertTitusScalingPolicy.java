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
import com.netflix.spinnaker.clouddriver.titus.client.TitusAutoscalingClient;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.UpsertTitusScalingPolicyDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusScalingPolicyModified;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.exceptions.UserException;
import com.netflix.titus.grpc.protogen.PutPolicyRequest;
import com.netflix.titus.grpc.protogen.ScalingPolicy;
import com.netflix.titus.grpc.protogen.ScalingPolicyID;
import com.netflix.titus.grpc.protogen.UpdatePolicyRequest;
import java.util.Collections;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UpsertTitusScalingPolicy
    implements SagaAction<UpsertTitusScalingPolicy.UpsertTitusScalingPolicyCommand> {
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final TitusClientProvider titusClientProvider;
  private final RetrySupport retrySupport;

  @Autowired
  public UpsertTitusScalingPolicy(
      AccountCredentialsProvider accountCredentialsProvider,
      TitusClientProvider titusClientProvider,
      RetrySupport retrySupport) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.titusClientProvider = titusClientProvider;
    this.retrySupport = retrySupport;
  }

  @NotNull
  @Override
  public Result apply(
      @NotNull UpsertTitusScalingPolicy.UpsertTitusScalingPolicyCommand command,
      @NotNull Saga saga) {
    AccountCredentials accountCredentials =
        accountCredentialsProvider.getCredentials(command.description.getAccount());

    TitusAutoscalingClient titusClient =
        titusClientProvider.getTitusAutoscalingClient(
            (NetflixTitusCredentials) accountCredentials, command.description.getRegion());

    if (titusClient == null) {
      throw new UserException("Autoscaling is not supported for this account/region");
    }

    String scalingPolicyId = command.description.getScalingPolicyID();

    boolean shouldCreate = scalingPolicyId == null;
    if (shouldCreate) {
      scalingPolicyId = createScalingPolicy(command, saga, titusClient);
    } else {
      scalingPolicyId = updateScalingPolicy(command, saga, titusClient);
    }

    return new Result(
        MonitorTitusScalingPolicy.MonitorTitusScalingPolicyCommand.builder().build(),
        Collections.singletonList(
            TitusScalingPolicyModified.builder()
                .account(command.description.getAccount())
                .region(command.description.getRegion())
                .jobId(command.description.getJobId())
                .scalingPolicyId(scalingPolicyId)
                .build()));
  }

  @Nonnull
  private String createScalingPolicy(
      @NotNull UpsertTitusScalingPolicy.UpsertTitusScalingPolicyCommand command,
      @NotNull Saga saga,
      TitusAutoscalingClient titusClient) {
    saga.log(
        "Creating Titus Scaling Policy %s:%s:%s",
        command.description.getAccount(),
        command.description.getRegion(),
        command.description.getJobId());

    ScalingPolicy.Builder builder = command.description.toScalingPolicyBuilder();

    PutPolicyRequest.Builder requestBuilder =
        PutPolicyRequest.newBuilder()
            .setScalingPolicy(builder)
            .setJobId(command.description.getJobId());

    ScalingPolicyID result =
        retrySupport.retry(
            () -> titusClient.createScalingPolicy(requestBuilder.build()), 10, 3000, false);

    saga.log(
        "Created Titus Scaling Policy %s:%s:%s (scalingPolicyId: %s)",
        command.description.getAccount(),
        command.description.getRegion(),
        command.description.getJobId(),
        result.getId());

    return result.getId();
  }

  @Nonnull
  private String updateScalingPolicy(
      @NotNull UpsertTitusScalingPolicy.UpsertTitusScalingPolicyCommand command,
      @NotNull Saga saga,
      TitusAutoscalingClient titusClient) {
    saga.log(
        "Updating Titus Scaling Policy %s:%s:%s (scalingPolicyId: %s)",
        command.description.getAccount(),
        command.description.getRegion(),
        command.description.getJobId(),
        command.description.getScalingPolicyID());

    retrySupport.retry(
        () -> {
          titusClient.updateScalingPolicy(
              UpdatePolicyRequest.newBuilder()
                  .setScalingPolicy(command.description.toScalingPolicyBuilder().build())
                  .setPolicyId(
                      ScalingPolicyID.newBuilder()
                          .setId(command.description.getScalingPolicyID())
                          .build())
                  .build());
          return true;
        },
        10,
        3000,
        false);

    saga.log(
        "Updated Titus Scaling Policy %s:%s:%s (scalingPolicyId: %s)",
        command.description.getAccount(),
        command.description.getRegion(),
        command.description.getJobId(),
        command.description.getScalingPolicyID());

    return command.description.getScalingPolicyID();
  }

  @Builder(builderClassName = "UpsertTitusScalingPolicyCommandBuilder", toBuilder = true)
  @JsonDeserialize(
      builder =
          UpsertTitusScalingPolicy.UpsertTitusScalingPolicyCommand
              .UpsertTitusScalingPolicyCommandBuilder.class)
  @JsonTypeName("upsertTitusScalingPolicyCommand")
  @Value
  public static class UpsertTitusScalingPolicyCommand implements SagaCommand {
    @Nonnull UpsertTitusScalingPolicyDescription description;

    @NonFinal EventMetadata metadata;

    @Override
    public void setMetadata(EventMetadata metadata) {
      this.metadata = metadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class UpsertTitusScalingPolicyCommandBuilder {}
  }
}
