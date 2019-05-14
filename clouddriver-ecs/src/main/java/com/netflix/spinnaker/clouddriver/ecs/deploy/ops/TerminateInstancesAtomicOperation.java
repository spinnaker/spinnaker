/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.StopTaskRequest;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.TerminateInstancesDescription;
import java.util.List;

public class TerminateInstancesAtomicOperation
    extends AbstractEcsAtomicOperation<TerminateInstancesDescription, Void> {

  public TerminateInstancesAtomicOperation(TerminateInstancesDescription description) {
    super(description, "TERMINATE_ECS_INSTANCES");
  }

  @Override
  public Void operate(List priorOutputs) {
    updateTaskStatus("Initializing Terminate ECS Instances Operation...");
    AmazonECS ecs = getAmazonEcsClient();

    for (String taskId : description.getEcsTaskIds()) {
      updateTaskStatus("Terminating instance: " + taskId);
      String clusterArn =
          containerInformationService.getClusterArn(
              description.getCredentialAccount(), description.getRegion(), taskId);
      StopTaskRequest request = new StopTaskRequest().withTask(taskId).withCluster(clusterArn);
      ecs.stopTask(request);
    }

    return null;
  }
}
