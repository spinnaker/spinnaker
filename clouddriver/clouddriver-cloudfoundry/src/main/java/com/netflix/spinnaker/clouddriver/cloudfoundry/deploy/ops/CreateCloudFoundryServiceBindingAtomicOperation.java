/*
 * Copyright 2020 Armory, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.CloudFoundryOperationUtils.describeProcessState;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.CreateServiceBinding;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ProcessStats;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.CreateCloudFoundryServiceBindingDescription;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateCloudFoundryServiceBindingAtomicOperation implements AtomicOperation<Void> {

  private static final String PHASE = "CREATE_SERVICE_BINDINGS";
  private final OperationPoller operationPoller;
  private final CreateCloudFoundryServiceBindingDescription description;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List<Void> priorOutputs) {

    List<String> serviceInstanceNames =
        description.getServiceBindingRequests().stream()
            .map(s -> s.getServiceInstanceName())
            .collect(Collectors.toList());

    getTask()
        .updateStatus(
            PHASE,
            "Creating Cloud Foundry service bindings between application '"
                + description.getServerGroupName()
                + "' and services: "
                + serviceInstanceNames);

    Map<String, String> serviceInstanceGuids = new HashMap<>();

    description
        .getClient()
        .getServiceInstances()
        .findAllServicesBySpaceAndNames(description.getSpace(), serviceInstanceNames)
        .forEach(s -> serviceInstanceGuids.put(s.getEntity().getName(), s.getMetadata().getGuid()));

    List<CreateServiceBinding> bindings =
        description.getServiceBindingRequests().stream()
            .map(
                s -> {
                  String serviceGuid = serviceInstanceGuids.get(s.getServiceInstanceName());
                  if (serviceGuid == null || serviceGuid.isEmpty()) {
                    throw new CloudFoundryApiException(
                        "Unable to find service with the name: '"
                            + s.getServiceInstanceName()
                            + "'");
                  }
                  if (s.isUpdatable()) {
                    removeBindings(serviceGuid, description.getServerGroupId());
                  }
                  return new CreateServiceBinding(
                      serviceGuid,
                      description.getServerGroupId(),
                      s.getServiceInstanceName(),
                      s.getParameters());
                })
            .collect(Collectors.toList());

    bindings.forEach(b -> description.getClient().getServiceInstances().createServiceBinding(b));

    if (description.isRestageRequired()) {
      getTask().updateStatus(PHASE, "Restaging application '" + description.getServerGroupName());
      description.getClient().getApplications().restageApplication(description.getServerGroupId());
    } else {
      getTask().updateStatus(PHASE, "Restarting application '" + description.getServerGroupName());
      description.getClient().getApplications().stopApplication(description.getServerGroupId());
      operationPoller.waitForOperation(
          () ->
              description.getClient().getApplications().getAppState(description.getServerGroupId()),
          inProgressState ->
              inProgressState == ProcessStats.State.DOWN
                  || inProgressState == ProcessStats.State.CRASHED,
          null,
          getTask(),
          description.getServerGroupName(),
          PHASE);
      description.getClient().getApplications().startApplication(description.getServerGroupId());
    }

    ProcessStats.State state =
        operationPoller.waitForOperation(
            () ->
                description
                    .getClient()
                    .getApplications()
                    .getAppState(description.getServerGroupId()),
            inProgressState ->
                inProgressState == ProcessStats.State.RUNNING
                    || inProgressState == ProcessStats.State.CRASHED,
            null,
            getTask(),
            description.getServerGroupName(),
            PHASE);

    if (state != ProcessStats.State.RUNNING) {
      getTask()
          .updateStatus(
              PHASE,
              "Failed to create Cloud Foundry service bindings between application '"
                  + description.getServerGroupName()
                  + "' and services: "
                  + serviceInstanceNames);
      throw new CloudFoundryApiException(
          "Failed to start '"
              + description.getServerGroupName()
              + "' which instead "
              + describeProcessState(state));
    }

    getTask()
        .updateStatus(
            PHASE,
            "Created Cloud Foundry service bindings between application '"
                + description.getServerGroupName()
                + "' and services: "
                + serviceInstanceNames);

    return null;
  }

  private void removeBindings(String serviceGuid, String appGuid) {
    description.getClient().getApplications().getServiceBindingsByApp(appGuid).stream()
        .filter(s -> serviceGuid.equalsIgnoreCase(s.getEntity().getServiceInstanceGuid()))
        .findAny()
        .ifPresent(
            s ->
                description
                    .getClient()
                    .getServiceInstances()
                    .deleteServiceBinding(s.getMetadata().getGuid()));
  }
}
