/*
 * Copyright 2021 Armory, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Resource;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ServiceBinding;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeleteCloudFoundryServiceBindingDescription;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DeleteCloudFoundryServiceBindingAtomicOperation implements AtomicOperation<Void> {

  private static final String PHASE = "DELETE_SERVICE_BINDINGS";
  private final DeleteCloudFoundryServiceBindingDescription description;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List<Void> priorOutputs) {

    List<String> unbindingServiceInstanceNames =
        description.getServiceUnbindingRequests().stream()
            .map(s -> s.getServiceInstanceName())
            .collect(Collectors.toList());

    getTask()
        .updateStatus(
            PHASE,
            "Unbinding Cloud Foundry application '"
                + description.getServerGroupName()
                + "' from services: "
                + unbindingServiceInstanceNames);

    List<Resource<ServiceBinding>> bindings =
        description
            .getClient()
            .getApplications()
            .getServiceBindingsByApp(description.getServerGroupId());

    removeBindings(bindings, unbindingServiceInstanceNames);

    getTask()
        .updateStatus(
            PHASE,
            "Successfully unbound Cloud Foundry application '"
                + description.getServerGroupName()
                + "' from services: "
                + unbindingServiceInstanceNames);

    return null;
  }

  private void removeBindings(
      List<Resource<ServiceBinding>> bindings, List<String> unbindingServiceInstanceNames) {
    bindings.stream()
        .filter(b -> unbindingServiceInstanceNames.contains(b.getEntity().getName()))
        .forEach(
            b -> {
              description
                  .getClient()
                  .getServiceInstances()
                  .deleteServiceBinding(b.getMetadata().getGuid());
            });
  }
}
