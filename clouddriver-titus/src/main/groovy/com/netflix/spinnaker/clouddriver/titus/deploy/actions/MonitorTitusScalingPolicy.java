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
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusScalingPolicyModified;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import com.netflix.spinnaker.kork.exceptions.UserException;
import com.netflix.titus.grpc.protogen.ScalingPolicyResult;
import com.netflix.titus.grpc.protogen.ScalingPolicyStatus;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MonitorTitusScalingPolicy
    implements SagaAction<MonitorTitusScalingPolicy.MonitorTitusScalingPolicyCommand> {
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final TitusClientProvider titusClientProvider;
  private final RetrySupport retrySupport;

  @Autowired
  public MonitorTitusScalingPolicy(
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
      @NotNull MonitorTitusScalingPolicy.MonitorTitusScalingPolicyCommand command,
      @NotNull Saga saga) {
    TitusScalingPolicyModified event = saga.getEvent(TitusScalingPolicyModified.class);

    saga.log(
        "Monitored Titus Scaling Policy %s:%s:%s (scalingPolicyId: %s",
        event.getAccount(), event.getRegion(), event.getJobId(), event.getScalingPolicyId());

    AccountCredentials accountCredentials =
        accountCredentialsProvider.getCredentials(event.getAccount());

    TitusAutoscalingClient titusClient =
        titusClientProvider.getTitusAutoscalingClient(
            (NetflixTitusCredentials) accountCredentials, event.getRegion());

    if (titusClient == null) {
      throw new UserException("Autoscaling is not supported for this account/region");
    }

    // make sure the new policy was applied
    retrySupport.retry(
        () -> {
          ScalingPolicyResult updatedPolicy =
              titusClient.getScalingPolicy(event.getScalingPolicyId());
          if (updatedPolicy == null
              || (updatedPolicy.getPolicyState().getState()
                  != ScalingPolicyStatus.ScalingPolicyState.Applied)) {
            throw new IntegrationException("Scaling policy updates have not been applied")
                .setRetryable(true);
          }
          return true;
        },
        10,
        5000,
        false);

    saga.log(
        "Monitored Titus Scaling Policy %s:%s:%s (scalingPolicyId: %s)",
        event.getAccount(), event.getRegion(), event.getJobId(), event.getScalingPolicyId());

    return new Result();
  }

  @Builder(builderClassName = "MonitorTitusScalingPolicyCommandBuilder", toBuilder = true)
  @JsonDeserialize(
      builder =
          MonitorTitusScalingPolicy.MonitorTitusScalingPolicyCommand
              .MonitorTitusScalingPolicyCommandBuilder.class)
  @JsonTypeName("MonitorTitusScalingPolicyCommand")
  @Value
  public static class MonitorTitusScalingPolicyCommand implements SagaCommand {
    @NonFinal EventMetadata metadata;

    @Override
    public void setMetadata(EventMetadata metadata) {
      this.metadata = metadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class MonitorTitusScalingPolicyCommandBuilder {}
  }
}
