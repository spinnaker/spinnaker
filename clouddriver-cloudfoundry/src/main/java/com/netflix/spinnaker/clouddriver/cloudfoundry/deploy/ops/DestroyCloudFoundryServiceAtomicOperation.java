/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.NOT_FOUND;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceInstanceResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DestroyCloudFoundryServiceDescription;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DestroyCloudFoundryServiceAtomicOperation
    implements AtomicOperation<ServiceInstanceResponse> {
  private static final String PHASE = "DELETE_SERVICE";
  private final DestroyCloudFoundryServiceDescription description;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public ServiceInstanceResponse operate(List priorOutputs) {
    Task task = getTask();
    ServiceInstanceResponse response =
        description
            .getClient()
            .getServiceInstances()
            .destroyServiceInstance(description.getSpace(), description.getServiceInstanceName());
    task.updateStatus(
        PHASE,
        "Started removing service instance '"
            + description.getServiceInstanceName()
            + "' from space "
            + description.getSpace().getName());
    LastOperation.State state = response.getState();
    if (state == NOT_FOUND) {
      task.updateStatus(
          PHASE,
          "Finished removing service instance '" + description.getServiceInstanceName() + "'");
    }
    return response;
  }
}
