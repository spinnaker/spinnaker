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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.AmazonAutoScalingException;
import com.amazonaws.services.autoscaling.model.DeleteLaunchConfigurationRequest;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteAmazonLaunchConfigurationDescription;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class DeleteAmazonLaunchConfigurationAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_LAUNCH_CONFIGURATION";

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Autowired private AmazonClientProvider amazonClientProvider;
  @Autowired private RetrySupport retrySupport;

  private final DeleteAmazonLaunchConfigurationDescription description;

  public DeleteAmazonLaunchConfigurationAtomicOperation(
      DeleteAmazonLaunchConfigurationDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List<Void> priorOutputs) {
    final String region = description.getRegion();
    final NetflixAmazonCredentials credentials = description.getCredentials();
    final String launchConfigurationName = description.getLaunchConfigurationName();

    final AmazonAutoScaling autoScaling =
        amazonClientProvider.getAutoScaling(credentials, region, true);
    getTask()
        .updateStatus(
            BASE_PHASE, "Deleting launch config " + launchConfigurationName + " in " + region);

    retrySupport.retry(
        () -> deleteLaunchConfiguration(launchConfigurationName, autoScaling),
        5,
        Duration.ofSeconds(1),
        true);
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Finished Delete Launch Config operation for " + launchConfigurationName + "");
    return null;
  }

  private Boolean deleteLaunchConfiguration(
      String launchConfigurationName, AmazonAutoScaling autoScaling) {
    try {
      autoScaling.deleteLaunchConfiguration(
          new DeleteLaunchConfigurationRequest()
              .withLaunchConfigurationName(launchConfigurationName));
      return true;
    } catch (AmazonAutoScalingException e) {
      if (!e.getMessage().toLowerCase().contains("launch configuration name not found")) {
        throw new IntegrationException(e).setRetryable(true);
      }
    }

    return false;
  }
}
