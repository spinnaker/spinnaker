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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DeleteLaunchTemplateRequest;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteAmazonLaunchTemplateDescription;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.core.RetrySupport;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class DeleteLaunchTemplateAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_LAUNCH_TEMPLATE";

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  private final DeleteAmazonLaunchTemplateDescription description;

  public DeleteLaunchTemplateAtomicOperation(DeleteAmazonLaunchTemplateDescription description) {
    this.description = description;
  }

  @Autowired private AmazonClientProvider amazonClientProvider;
  @Autowired private RetrySupport retrySupport;

  @Override
  public Void operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            String.format("Initializing Delete Launch Template operation for %s", description));

    AmazonEC2 ec2 =
        amazonClientProvider.getAmazonEC2(description.getCredentials(), description.getRegion());
    retrySupport.retry(
        () -> deleteLaunchTemplate(description.getLaunchTemplateId(), ec2),
        3,
        Duration.ofSeconds(3),
        false);

    getTask()
        .updateStatus(
            BASE_PHASE,
            String.format(
                "Launch Template %s in %s",
                description.getLaunchTemplateId(), description.getRegion()));
    return null;
  }

  private Boolean deleteLaunchTemplate(String launchTemplateId, AmazonEC2 ec2) {
    try {
      ec2.deleteLaunchTemplate(
          new DeleteLaunchTemplateRequest().withLaunchTemplateId(launchTemplateId));
      return true;
    } catch (Exception e) {
      if (e.getMessage().toLowerCase().contains("does not exist")) {
        return true;
      }

      throw e;
    }
  }
}
