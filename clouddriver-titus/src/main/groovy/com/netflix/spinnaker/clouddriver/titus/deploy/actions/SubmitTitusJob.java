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

import static com.netflix.spinnaker.clouddriver.titus.deploy.actions.AttachTitusServiceLoadBalancers.AttachTitusServiceLoadBalancersCommand;
import static com.netflix.spinnaker.clouddriver.titus.deploy.actions.CopyTitusServiceScalingPolicies.CopyTitusServiceScalingPoliciesCommand;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper;
import com.netflix.spinnaker.clouddriver.event.EventMetadata;
import com.netflix.spinnaker.clouddriver.orchestration.sagas.LoadFront50App;
import com.netflix.spinnaker.clouddriver.orchestration.sagas.LoadFront50App.Front50AppAware;
import com.netflix.spinnaker.clouddriver.saga.ManyCommands;
import com.netflix.spinnaker.clouddriver.saga.SagaCommand;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.titus.JobType;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusException;
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient;
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusJobSubmitted;
import com.netflix.spinnaker.kork.core.RetrySupport;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.Collections;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SubmitTitusJob extends AbstractTitusDeployAction
    implements SagaAction<SubmitTitusJob.SubmitTitusJobCommand> {

  private static final Logger log = LoggerFactory.getLogger(SubmitTitusJob.class);

  private final RetrySupport retrySupport;

  @Autowired
  public SubmitTitusJob(
      AccountCredentialsRepository accountCredentialsRepository,
      TitusClientProvider titusClientProvider,
      RetrySupport retrySupport) {
    super(accountCredentialsRepository, titusClientProvider);
    this.retrySupport = retrySupport;
  }

  /**
   * NOTE: The single-element array usage is to get around line-for-line Groovy conversion variable
   * references inside of the lambda. This should really be refactored so that pattern isn't
   * necessary. It's really gross as-is.
   */
  @Nonnull
  @Override
  public Result apply(@Nonnull SubmitTitusJobCommand command, @Nonnull Saga saga) {
    final TitusDeployDescription description = command.description;

    prepareDeployDescription(description);

    final TitusClient titusClient =
        titusClientProvider.getTitusClient(description.getCredentials(), description.getRegion());

    final SubmitJobRequest submitJobRequest = command.getSubmitJobRequest();
    final String[] nextServerGroupName = {command.getNextServerGroupName()};

    final int[] retryCount = {0};
    String jobUri =
        retrySupport.retry(
            () -> {
              try {
                return titusClient.submitJob(submitJobRequest.withJobName(nextServerGroupName[0]));
              } catch (StatusRuntimeException e) {
                if (isServiceExceptionRetryable(description, e)) {
                  String statusDescription = e.getStatus().getDescription();
                  if (statusDescription != null
                      && statusDescription.contains(
                          "Job sequence id reserved by another pending job")) {
                    try {
                      Thread.sleep(1000 ^ (int) Math.round(Math.pow(2, retryCount[0])));
                    } catch (InterruptedException ex) {
                      // TODO(rz): I feel like this is really bad to do...?
                      // Sweep this under the rug...
                    }
                    retryCount[0]++;
                  }
                  nextServerGroupName[0] =
                      TitusJobNameResolver.resolveJobName(titusClient, description);

                  saga.log("Resolved server group name to '%s'", nextServerGroupName[0]);

                  saga.log(
                      "Retrying with %s after %s attempts", nextServerGroupName[0], retryCount[0]);
                  throw e;
                }
                if (isStatusCodeRetryable(e.getStatus().getCode())) {
                  retryCount[0]++;
                  saga.log("Retrying after %s attempts", retryCount[0]);
                  throw e;
                } else {
                  log.error(
                      "Could not submit job and not retrying for status {}", e.getStatus(), e);
                  saga.log("Could not submit job %s: %s", e.getStatus(), e.getMessage());
                  throw e;
                }
              }
            },
            8,
            100,
            true);

    if (jobUri == null) {
      throw new TitusException("could not create job");
    }

    saga.log("Successfully submitted job request to Titus (Job URI: %s)", jobUri);

    return new Result(
        new ManyCommands(
            AttachTitusServiceLoadBalancersCommand.builder()
                .description(description)
                .jobUri(jobUri)
                .targetGroupLookupResult(command.targetGroupLookupResult)
                .build(),
            CopyTitusServiceScalingPoliciesCommand.builder()
                .description(description)
                .jobUri(jobUri)
                .deployedServerGroupName(nextServerGroupName[0])
                .build()),
        Collections.singletonList(
            TitusJobSubmitted.builder()
                .jobType(JobType.from(description.getJobType()))
                .serverGroupNameByRegion(
                    Collections.singletonMap(description.getRegion(), nextServerGroupName[0]))
                .jobUri(jobUri)
                .build()));
  }

  /**
   * TODO(rz): Figure out what conditions are not retryable and why. Then document, because what?
   */
  private static boolean isServiceExceptionRetryable(
      TitusDeployDescription description, StatusRuntimeException e) {
    String statusDescription = e.getStatus().getDescription();
    return JobType.SERVICE.isEqual(description.getJobType())
        && (e.getStatus().getCode() == Status.RESOURCE_EXHAUSTED.getCode()
            || e.getStatus().getCode() == Status.INVALID_ARGUMENT.getCode())
        && (statusDescription != null
            && (statusDescription.contains("Job sequence id reserved by another pending job")
                || statusDescription.contains("Constraint violation - job with group sequence")));
  }

  /**
   * TODO(rz): Figure out what conditions are not retryable and why. Then document, because what?
   */
  private static boolean isStatusCodeRetryable(Status.Code code) {
    return code == Status.UNAVAILABLE.getCode()
        || code == Status.INTERNAL.getCode()
        || code == Status.DEADLINE_EXCEEDED.getCode()
        || code == Status.RESOURCE_EXHAUSTED.getCode();
  }

  @Builder(builderClassName = "SubmitTitusJobCommandBuilder", toBuilder = true)
  @JsonDeserialize(builder = SubmitTitusJobCommand.SubmitTitusJobCommandBuilder.class)
  @JsonTypeName("submitTitusJobCommand")
  @Value
  public static class SubmitTitusJobCommand implements SagaCommand, Front50AppAware {
    @Nonnull private TitusDeployDescription description;
    @Nonnull private SubmitJobRequest submitJobRequest;
    @Nonnull private String nextServerGroupName;
    private TargetGroupLookupHelper.TargetGroupLookupResult targetGroupLookupResult;
    @NonFinal private LoadFront50App.Front50App front50App;
    @NonFinal private EventMetadata metadata;

    @Override
    public void setFront50App(LoadFront50App.Front50App front50App) {
      this.front50App = front50App;
    }

    @Override
    public void setMetadata(EventMetadata metadata) {
      this.metadata = metadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class SubmitTitusJobCommandBuilder {}
  }
}
