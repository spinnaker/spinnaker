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

import static java.lang.String.format;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.collect.Sets;
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
import com.netflix.spinnaker.clouddriver.titus.client.model.Task;
import com.netflix.spinnaker.clouddriver.titus.client.model.TerminateTasksAndShrinkJobRequest;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DetachTitusInstancesDescription;
import com.netflix.spinnaker.kork.exceptions.UserException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DetachTitusTasks implements SagaAction<DetachTitusTasks.DetachTitusTasksCommand> {
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final TitusClientProvider titusClientProvider;

  @Autowired
  public DetachTitusTasks(
      AccountCredentialsProvider accountCredentialsProvider,
      TitusClientProvider titusClientProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.titusClientProvider = titusClientProvider;
  }

  @NotNull
  @Override
  public Result apply(
      @NotNull DetachTitusTasks.DetachTitusTasksCommand command, @NotNull Saga saga) {
    saga.log(
        "Detaching Titus Tasks %s:%s:%s:%s",
        command.description.getAccount(),
        command.description.getRegion(),
        command.getDescription().getAsgName(),
        command.description.getInstanceIds());

    AccountCredentials accountCredentials =
        accountCredentialsProvider.getCredentials(command.description.getAccount());

    TitusClient titusClient =
        titusClientProvider.getTitusClient(
            (NetflixTitusCredentials) accountCredentials, command.description.getRegion());

    Job job = titusClient.findJobByName(command.description.getAsgName(), true);
    if (job == null) {
      saga.log("Job not found");
      throw new UserException(
          "No titus server group named '" + command.description.getAsgName() + "' found");
    }

    Set<String> validInstanceIds =
        Sets.intersection(
            new HashSet<>(command.getDescription().getInstanceIds()),
            job.getTasks().stream().map(Task::getId).collect(Collectors.toSet()));

    if (validInstanceIds.isEmpty()) {
      saga.log("No detachable instances");
      return new Result();
    }

    int newMin = job.getInstances() - validInstanceIds.size();
    if (newMin < job.getInstancesMin()) {
      if (command.description.getAdjustMinIfNecessary()) {
        if (newMin < 0) {
          saga.log("Cannot adjust min size below 0");
        } else {
          titusClient.resizeJob(
              (ResizeJobRequest)
                  new ResizeJobRequest()
                      .withInstancesDesired(job.getInstancesDesired())
                      .withInstancesMax(job.getInstancesMax())
                      .withInstancesMin(newMin)
                      .withJobId(job.getId())
                      .withUser(command.description.getUser()));
        }
      } else {
        saga.log(
            "Cannot decrement server group below minSize - set adjustMinIfNecessary to resize down minSize before detaching instances");
        throw new UserException(
            format(
                "Invalid server group capacity for detachInstances (min: %d, max: %d, desired: %d)",
                job.getInstancesMin(), job.getInstancesMax(), job.getInstancesDesired()));
      }
    }

    saga.log(
        "Filtered Titus Tasks %s:%s:%s:%s",
        command.description.getAccount(),
        command.description.getRegion(),
        command.getDescription().getAsgName(),
        validInstanceIds);

    titusClient.terminateTasksAndShrink(
        new TerminateTasksAndShrinkJobRequest()
            .withUser(command.description.getUser())
            .withShrink(true)
            .withTaskIds(new ArrayList<>(validInstanceIds)));

    saga.log(
        "Detached Titus Tasks %s:%s:%s:%s (filtered)",
        command.description.getAccount(),
        command.description.getRegion(),
        command.getDescription().getAsgName(),
        validInstanceIds);

    return new Result();
  }

  @Builder(builderClassName = "DetachTitusTasksCommandBuilder", toBuilder = true)
  @JsonDeserialize(
      builder = DetachTitusTasks.DetachTitusTasksCommand.DetachTitusTasksCommandBuilder.class)
  @JsonTypeName("detachTitusTasksCommand")
  @Value
  public static class DetachTitusTasksCommand implements SagaCommand {
    @Nonnull DetachTitusInstancesDescription description;

    @NonFinal EventMetadata metadata;

    @Override
    public void setMetadata(EventMetadata metadata) {
      this.metadata = metadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class DetachTitusTasksCommandBuilder {}
  }
}
