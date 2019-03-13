/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.TerminateCloudFoundryInstancesDescription;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.joining;

@RequiredArgsConstructor
public class TerminateCloudFoundryInstancesAtomicOperation implements AtomicOperation<Void> {
  private static final String PHASE = "TERMINATE_INSTANCES";
  private final TerminateCloudFoundryInstancesDescription description;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask().updateStatus(PHASE, "Terminating " + instanceDescription());
    final CloudFoundryClient client = description.getClient();

    for (String instance : description.getInstanceIds()) {
      try {
        String serverGroupId = instance.substring(0, instance.lastIndexOf("-"));
        String instanceIndex = instance.substring(instance.lastIndexOf("-") + 1);
        client.getApplications().deleteAppInstance(serverGroupId, instanceIndex);
        getTask().updateStatus(PHASE, "Terminated " + instanceDescription());
      } catch (CloudFoundryApiException e) {
        throw new CloudFoundryApiException("Failed to terminate '" + instance + "': " + e.getMessage());
      }
    }

    return null;
  }

  private String instanceDescription() {
    return description.getInstanceIds().length == 1 ?
      "application instance '" + description.getInstanceIds()[0] + "'" :
      "application instances [" + Arrays.stream(description.getInstanceIds())
        .map(id -> "'" + id + "'").collect(joining(", ")) + "]";
  }
}
