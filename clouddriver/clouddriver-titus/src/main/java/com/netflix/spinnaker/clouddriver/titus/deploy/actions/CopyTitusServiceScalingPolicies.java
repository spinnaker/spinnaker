/*
 * Copyright 2019 Netflix, Inc.
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
import com.netflix.spinnaker.clouddriver.titus.TitusUtils;
import com.netflix.spinnaker.clouddriver.titus.client.TitusAutoscalingClient;
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient;
import com.netflix.spinnaker.clouddriver.titus.client.model.Job;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.UpsertTitusScalingPolicyDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusScalingPolicyCopied;
import com.netflix.spinnaker.clouddriver.titus.exceptions.InsufficientDeploySourceStateException;
import com.netflix.titus.grpc.protogen.PutPolicyRequest;
import com.netflix.titus.grpc.protogen.ScalingPolicyResult;
import com.netflix.titus.grpc.protogen.ScalingPolicyStatus.ScalingPolicyState;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CopyTitusServiceScalingPolicies extends AbstractTitusDeployAction
    implements SagaAction<CopyTitusServiceScalingPolicies.CopyTitusServiceScalingPoliciesCommand> {

  private static final List<ScalingPolicyState> IGNORED_STATES =
      Arrays.asList(ScalingPolicyState.Deleted, ScalingPolicyState.Deleting);

  @Autowired
  public CopyTitusServiceScalingPolicies(
      AccountCredentialsRepository accountCredentialsRepository,
      TitusClientProvider titusClientProvider) {
    super(accountCredentialsRepository, titusClientProvider);
  }

  @Nonnull
  @Override
  public Result apply(@Nonnull CopyTitusServiceScalingPoliciesCommand command, @Nonnull Saga saga) {
    final TitusDeployDescription description = command.description;

    prepareDeployDescription(description);

    if (!description.isCopySourceScalingPolicies()
        || !description.getCopySourceScalingPoliciesAndActions()) {
      saga.log("Not applying scaling policies: None to apply");
      return new Result();
    }

    TitusDeployDescription.Source source = description.getSource();
    TitusClient sourceClient = buildSourceTitusClient(source);
    if (sourceClient == null) {
      // No source, no copying.
      saga.log("Not applying scaling policies: No source to copy from");
      return new Result();
    }

    TitusAutoscalingClient autoscalingClient =
        titusClientProvider.getTitusAutoscalingClient(
            description.getCredentials(), description.getRegion());
    if (autoscalingClient == null) {
      saga.log("Unable to create client in target account/region; policies will not be copied");
      return new Result();
    }

    TitusAutoscalingClient sourceAutoscalingClient = buildSourceAutoscalingClient(source);
    if (sourceAutoscalingClient == null) {
      saga.log("Unable to create client in source account/region; policies will not be copied");
      return new Result();
    }

    Job sourceJob = sourceClient.findJobByName(source.getAsgName());
    if (sourceJob == null) {
      saga.log(
          "Unable to locate source (%s:%s:%s)",
          source.getAccount(), source.getRegion(), source.getAsgName());
    } else {
      final String jobUri = command.jobUri;
      final String serverGroupName = command.deployedServerGroupName;

      saga.log("Copying scaling policies from source (Job URI: %s)", jobUri);
      List<ScalingPolicyResult> policies =
          Optional.ofNullable(sourceAutoscalingClient.getJobScalingPolicies(sourceJob.getId()))
              .orElse(Collections.emptyList());
      saga.log("Found %d scaling policies for source (Job URI: %s)", policies.size(), jobUri);
      policies.forEach(
          policy -> {
            if (!IGNORED_STATES.contains(policy.getPolicyState().getState())) {
              PutPolicyRequest.Builder builder =
                  PutPolicyRequest.newBuilder()
                      .setJobId(jobUri)
                      .setScalingPolicy(
                          UpsertTitusScalingPolicyDescription.fromScalingPolicyResult(
                                  description.getRegion(), policy, serverGroupName)
                              .toScalingPolicyBuilder());
              autoscalingClient.createScalingPolicy(builder.build());
              saga.addEvent(
                  TitusScalingPolicyCopied.builder()
                      .serverGroupName(serverGroupName)
                      .region(description.getRegion())
                      .sourcePolicyId(policy.getId().getId())
                      .build());
            }
          });
    }

    saga.log("Copy scaling policies completed");

    return new Result();
  }

  private TitusAutoscalingClient buildSourceAutoscalingClient(
      TitusDeployDescription.Source source) {
    if (!isNullOrEmpty(source.getAccount())
        && !isNullOrEmpty(source.getRegion())
        && !isNullOrEmpty(source.getAsgName())) {
      AccountCredentials sourceCredentials =
          accountCredentialsRepository.getOne(source.getAccount());

      TitusUtils.assertTitusAccountCredentialsType(sourceCredentials);

      return titusClientProvider.getTitusAutoscalingClient(
          (NetflixTitusCredentials) sourceCredentials, source.getRegion());
    }

    throw new InsufficientDeploySourceStateException(
        "Could not create titus client from deployment Source",
        source.getAccount(),
        source.getRegion(),
        source.getAsgName());
  }

  @Builder(builderClassName = "CopyTitusServiceScalingPoliciesCommandBuilder", toBuilder = true)
  @JsonDeserialize(
      builder =
          CopyTitusServiceScalingPoliciesCommand.CopyTitusServiceScalingPoliciesCommandBuilder
              .class)
  @JsonTypeName("copyTitusServiceScalingPoliciesCommand")
  @Value
  public static class CopyTitusServiceScalingPoliciesCommand implements SagaCommand {
    @Nonnull private TitusDeployDescription description;
    @Nonnull private String jobUri;
    @Nonnull private String deployedServerGroupName;
    @NonFinal private EventMetadata metadata;

    @Override
    public void setMetadata(EventMetadata metadata) {
      this.metadata = metadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class CopyTitusServiceScalingPoliciesCommandBuilder {}
  }
}
