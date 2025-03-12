/*
 * Copyright 2019 Pivotal, Inc.
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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceKeyResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeleteCloudFoundryServiceKeyDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DeleteCloudFoundryServiceKeyAtomicOperation
    implements AtomicOperation<ServiceKeyResponse> {
  private static final String PHASE = "DELETE_SERVICE_KEY";
  private final DeleteCloudFoundryServiceKeyDescription description;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public ServiceKeyResponse operate(List priorOutputs) {
    Task task = getTask();

    CloudFoundrySpace space = description.getSpace();
    String serviceInstanceName = description.getServiceInstanceName();
    String serviceKeyName = description.getServiceKeyName();
    task.updateStatus(
        PHASE,
        "Deleting service key '"
            + serviceKeyName
            + "' for service '"
            + serviceInstanceName
            + "' in '"
            + space.getRegion()
            + "'");

    ServiceKeyResponse results =
        description
            .getClient()
            .getServiceKeys()
            .deleteServiceKey(space, serviceInstanceName, serviceKeyName);

    task.updateStatus(PHASE, "Finished deleting service key '" + serviceKeyName + "'");

    return results;
  }
}
