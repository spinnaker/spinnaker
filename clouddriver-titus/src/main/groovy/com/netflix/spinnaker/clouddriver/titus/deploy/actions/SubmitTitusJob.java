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

import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper;
import com.netflix.spinnaker.clouddriver.saga.ManyCommands;
import com.netflix.spinnaker.clouddriver.saga.SagaCommand;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.titus.JobType;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusException;
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient;
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest;
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.LoadFront50App.Front50AppAware;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusJobSubmitted;
import com.netflix.spinnaker.kork.core.RetrySupport;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.Collections;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SubmitTitusJob implements SagaAction<SubmitTitusJob.SubmitTitusJobCommand> {

  private static final Logger log = LoggerFactory.getLogger(SubmitTitusJob.class);

  private final TitusClientProvider titusClientProvider;
  private final RetrySupport retrySupport;

  @Autowired
  public SubmitTitusJob(TitusClientProvider titusClientProvider, RetrySupport retrySupport) {
    this.titusClientProvider = titusClientProvider;
    this.retrySupport = retrySupport;
  }

  private static boolean isServiceExceptionRetryable(
      TitusDeployDescription description, StatusRuntimeException e) {
    String statusDescription = e.getStatus().getDescription();
    return JobType.isEqual(description.getJobType(), JobType.SERVICE)
        && (e.getStatus().getCode() == Status.RESOURCE_EXHAUSTED.getCode()
            || e.getStatus().getCode() == Status.INVALID_ARGUMENT.getCode())
        && (statusDescription != null
            && (statusDescription.contains("Job sequence id reserved by another pending job")
                || statusDescription.contains("Constraint violation - job with group sequence")));
  }

  private static boolean isStatusCodeRetryable(Status.Code code) {
    return code == Status.UNAVAILABLE.getCode()
        || code == Status.INTERNAL.getCode()
        || code == Status.DEADLINE_EXCEEDED.getCode();
  }

  /**
   * NOTE: The single-element array usage is to get around line-for-line Groovy conversion variable
   * references inside of the lambda. This should really be refactored so that pattern isn't
   * necessary. It's really gross as-is.
   */
  @NotNull
  @Override
  public Result apply(@NotNull SubmitTitusJobCommand command, @NotNull Saga saga) {
    final TitusDeployDescription description = command.description;

    final TitusClient titusClient =
        titusClientProvider.getTitusClient(description.getCredentials(), description.getRegion());

    final SubmitJobRequest submitJobRequest = command.getSubmitJobRequest();
    final String[] nextServerGroupName = {command.getNextServerGroupName()};

    final int[] retryCount = {0};
    String jobUri =
        retrySupport.retry(
            () -> {
              try {
                return titusClient.submitJob(submitJobRequest);
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
                      TitusJobNameResolver.resolveJobName(
                          titusClient, description, submitJobRequest);

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
            new AttachTitusServiceLoadBalancersCommand(
                description, jobUri, command.targetGroupLookupResult),
            new CopyTitusServiceScalingPoliciesCommand(
                description, jobUri, nextServerGroupName[0])),
        Collections.singletonList(
            new TitusJobSubmitted(
                Collections.singletonMap(description.getRegion(), nextServerGroupName[0]),
                jobUri,
                JobType.from(description.getJobType()))));
  }

  @EqualsAndHashCode(callSuper = true)
  @Getter
  public static class SubmitTitusJobCommand extends SagaCommand implements Front50AppAware {
    @Nonnull private final TitusDeployDescription description;
    @Nonnull private final SubmitJobRequest submitJobRequest;
    @Nonnull private final String nextServerGroupName;
    @Nullable private final TargetGroupLookupHelper.TargetGroupLookupResult targetGroupLookupResult;
    @Nullable @NonFinal private LoadFront50App.Front50App front50App;

    public SubmitTitusJobCommand(
        @Nonnull TitusDeployDescription description,
        @Nonnull SubmitJobRequest submitJobRequest,
        @Nonnull String nextServerGroupName,
        @Nullable TargetGroupLookupHelper.TargetGroupLookupResult targetGroupLookupResult) {
      super();
      this.description = description;
      this.submitJobRequest = submitJobRequest;
      this.nextServerGroupName = nextServerGroupName;
      this.targetGroupLookupResult = targetGroupLookupResult;
    }

    @Override
    public void setFront50App(LoadFront50App.Front50App app) {
      this.front50App = app;
    }
  }
}
