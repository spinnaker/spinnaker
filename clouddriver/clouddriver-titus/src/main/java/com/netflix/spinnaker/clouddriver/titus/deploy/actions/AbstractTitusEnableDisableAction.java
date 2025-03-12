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

import static com.netflix.spinnaker.clouddriver.eureka.deploy.ops.AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE;
import static com.netflix.spinnaker.clouddriver.eureka.deploy.ops.AbstractEurekaSupport.DiscoveryStatus.UP;

import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.AbstractEurekaSupport.DiscoveryStatus;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusException;
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient;
import com.netflix.spinnaker.clouddriver.titus.client.TitusLoadBalancerClient;
import com.netflix.spinnaker.clouddriver.titus.client.model.ActivateJobRequest;
import com.netflix.spinnaker.clouddriver.titus.client.model.Job;
import com.netflix.spinnaker.clouddriver.titus.client.model.Task;
import com.netflix.spinnaker.clouddriver.titus.client.model.TaskState;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.EnableDisableInstanceDiscoveryDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.EnableDisableServerGroupDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.ops.discovery.TitusEurekaSupport;
import com.netflix.spinnaker.kork.exceptions.UserException;
import com.netflix.titus.grpc.protogen.LoadBalancerId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractTitusEnableDisableAction {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final AccountCredentialsProvider accountCredentialsProvider;
  private final TitusEurekaSupport discoverySupport;
  private final TitusClientProvider titusClientProvider;

  public AbstractTitusEnableDisableAction(
      AccountCredentialsProvider accountCredentialsProvider,
      TitusEurekaSupport discoverySupport,
      TitusClientProvider titusClientProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.discoverySupport = discoverySupport;
    this.titusClientProvider = titusClientProvider;
  }

  /**
   * Mark a Titus job and containing tasks as UP or OUT_OF_SERVICE in discovery _and_ any associated
   * load balancers.
   *
   * @param saga Enclosing saga.
   * @param description Enclosing description.
   * @param shouldDisable Whether or not job and tasks should be marked OUT_OF_SERVICE (true) or UP
   *     (false)
   */
  void markJob(Saga saga, EnableDisableServerGroupDescription description, boolean shouldDisable) {
    String presentParticipling = shouldDisable ? "Disabling" : "Enabling";
    String verb = shouldDisable ? "Disable" : "Enable";

    saga.log(
        "%s server group %s/%s",
        presentParticipling, description.getRegion(), description.getServerGroupName());

    NetflixTitusCredentials credentials =
        (NetflixTitusCredentials)
            accountCredentialsProvider.getCredentials(description.getAccount());
    description.setCredentials(credentials);

    try {
      TitusClient titusClient =
          titusClientProvider.getTitusClient(description.getCredentials(), description.getRegion());

      TitusLoadBalancerClient titusLoadBalancerClient =
          titusClientProvider.getTitusLoadBalancerClient(
              description.getCredentials(), description.getRegion());

      String serverGroupName = description.getServerGroupName();
      String region = description.getRegion();

      Job job = titusClient.findJobByName(serverGroupName, true);
      if (job == null) {
        saga.log("No job named '%s' found in %s", serverGroupName, region);
        return;
      }

      if (shouldDisable
          && titusLoadBalancerClient != null
          && description.getDesiredPercentage() != null) {
        if (job.getLabels().containsKey("spinnaker.targetGroups")) {
          throw new TitusException(
              "Titus does not support percentage-based disabling for server groups in one or more target groups");
        }
      }

      // If desired percentage is part of the description (ie. Monitored Deploy), disable the job
      // only if it's set to 100
      if (shouldDisable && description.getDesiredPercentage() != null) {
        if (description.getDesiredPercentage() == 100) {
          saga.log("Disabling job (desiredPercentage: %d)", description.getDesiredPercentage());
          activateJob(titusClient, job, false);
          saga.log("Disabled job (desiredPercentage: %d)", description.getDesiredPercentage());
        } else {
          saga.log("Not disabling job (desiredPercentage: %d)", description.getDesiredPercentage());
        }
      } else {
        activateJob(titusClient, job, !shouldDisable);
      }

      if (titusLoadBalancerClient != null
          && job.getLabels().containsKey("spinnaker.targetGroups")) {
        if (shouldDisable) {
          saga.log("Removing %s from target groups", job.getId());
          titusLoadBalancerClient
              .getJobLoadBalancers(job.getId())
              .forEach(
                  loadBalancerId -> {
                    saga.log("Removing %s from %s", job.getId(), loadBalancerId.getId());
                    titusLoadBalancerClient.removeLoadBalancer(job.getId(), loadBalancerId.getId());
                    saga.log("Removed %s from %s", job.getId(), loadBalancerId.getId());
                  });
          saga.log("Removed %s from target groups", job.getId());
        } else {
          saga.log("Restoring %s into target groups", job.getId());
          Set<String> attachedLoadBalancers =
              titusLoadBalancerClient.getJobLoadBalancers(job.getId()).stream()
                  .map(LoadBalancerId::getId)
                  .collect(Collectors.toSet());

          for (String loadBalancerId : job.getLabels().get("spinnaker.targetGroups").split(",")) {
            if (!attachedLoadBalancers.contains(loadBalancerId)) {
              saga.log("Restoring %s into %s", job.getId(), loadBalancerId);
              titusLoadBalancerClient.addLoadBalancer(job.getId(), loadBalancerId);
            }
          }

          saga.log("Restored %s into target groups", job.getId());
        }
      }

      if (job.getTasks() != null && !job.getTasks().isEmpty()) {
        DiscoveryStatus status = shouldDisable ? OUT_OF_SERVICE : UP;
        saga.log("Marking server group %s as %s with Discovery", serverGroupName, status);

        List<String> instanceIds =
            job.getTasks().stream().map(Task::getId).collect(Collectors.toList());

        EnableDisableInstanceDiscoveryDescription updateDiscoveryDescription =
            new EnableDisableInstanceDiscoveryDescription();
        updateDiscoveryDescription.setCredentials(description.getCredentials());
        updateDiscoveryDescription.setRegion(region);
        updateDiscoveryDescription.setAsgName(serverGroupName);
        updateDiscoveryDescription.setInstanceIds(instanceIds);

        if (description.getDesiredPercentage() != null && shouldDisable) {
          instanceIds =
              discoverySupport.getInstanceToModify(
                  description.getAccount(),
                  region,
                  serverGroupName,
                  instanceIds,
                  description.getDesiredPercentage());

          saga.log(
              "Disabling instances %s on ASG %s with percentage %s",
              instanceIds, serverGroupName, description.getDesiredPercentage());
        }

        discoverySupport.updateDiscoveryStatusForInstances(
            updateDiscoveryDescription, getTask(), verb.toUpperCase(), status, instanceIds);
      }

      try {
        titusClient.setAutoscaleEnabled(job.getId(), !shouldDisable);
      } catch (Exception e) {
        log.error(
            "Error toggling autoscale enabled for Titus job {} in {}}/{}",
            job.getId(),
            description.getAccount(),
            description.getRegion(),
            e);
      }

      saga.log("Finished %s server group %s", presentParticipling, serverGroupName);
    } catch (Exception e) {
      String errorMessage =
          String.format(
              "Could not %s server group '%s' in region %s! Failure Type: %s; Message: %s",
              verb,
              description.getServerGroupName(),
              description.getRegion(),
              e.getClass().getSimpleName(),
              e.getMessage());
      log.error(errorMessage, e);
      saga.log(errorMessage);

      throw e;
    }
  }

  /**
   * Mark one or more Titus tasks as UP or OUT_OF_SERVICE in discovery.
   *
   * <p>No other changes are made to the tasks or job they are a member of.
   *
   * @param saga Enclosing saga.
   * @param description Enclosing description.
   * @param shouldDisable Whether or not instances should be marked OUT_OF_SERVICE (true) or UP
   *     (false)
   */
  void markTasks(
      Saga saga, EnableDisableInstanceDiscoveryDescription description, boolean shouldDisable) {
    String presentParticipling = shouldDisable ? "Disabling" : "Enabling";
    String verb = shouldDisable ? "Disable" : "Enable";

    saga.log(
        "%s instances %s from %s/%s in discovery",
        presentParticipling,
        description.getInstanceIds(),
        description.getRegion(),
        description.getAsgName());

    NetflixTitusCredentials credentials =
        (NetflixTitusCredentials)
            accountCredentialsProvider.getCredentials(description.getAccount());
    description.setCredentials(credentials);

    if (!description.getCredentials().getDiscoveryEnabled()) {
      throw new UserException("Discovery is not enabled, unable to modify instance status");
    }

    TitusClient titusClient =
        titusClientProvider.getTitusClient(description.getCredentials(), description.getRegion());
    Job job = titusClient.findJobByName(description.getAsgName(), true);
    if (job == null) {
      return;
    }

    List<String> titusInstanceIds =
        job.getTasks().stream()
            .filter(it -> it.getState() == TaskState.RUNNING || it.getState() == TaskState.STARTING)
            .filter(it -> description.getInstanceIds().contains(it.getId()))
            .map(Task::getId)
            .collect(Collectors.toList());

    if (titusInstanceIds.isEmpty()) {
      return;
    }

    DiscoveryStatus status = shouldDisable ? OUT_OF_SERVICE : UP;
    discoverySupport.updateDiscoveryStatusForInstances(
        description, getTask(), verb.toUpperCase(), status, titusInstanceIds);
  }

  private void activateJob(TitusClient provider, Job job, boolean inService) {
    provider.activateJob(
        (ActivateJobRequest)
            new ActivateJobRequest()
                .withInService(inService)
                .withUser("spinnaker")
                .withJobId(job.getId()));
  }

  private com.netflix.spinnaker.clouddriver.data.task.Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
