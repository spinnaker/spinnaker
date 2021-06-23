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

import static com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.CloudFoundryOperationUtils.describeProcessState;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ProcessStats;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeleteCloudFoundryServiceBindingDescription;
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
public class DeleteCloudFoundryServiceBindingAtomicOperation implements AtomicOperation<Void> {

  private static final String PHASE = "DELETE_SERVICE_BINDINGS";
  private final OperationPoller operationPoller;
  private final DeleteCloudFoundryServiceBindingDescription description;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List<Void> priorOutputs) {

    List<String> serviceInstanceNames =
        description.getServiceUnbindingRequests().stream()
            .map(s -> s.getServiceInstanceName())
            .collect(Collectors.toList());

    getTask()
        .updateStatus(
            PHASE,
            "Deleting Cloud Foundry service bindings between application '"
                + description.getServerGroupName()
                + "' and services: "
                + serviceInstanceNames);

    Map<String, String> serviceInstanceGuids = new HashMap<>();

    description
        .getClient()
        .getApplications()
        .getServiceBindingsByApp(description.getServerGroupId())
        .stream()
        .forEach(
            s ->
                serviceInstanceGuids.put(
                    s.getEntity().getName(), s.getEntity().getServiceInstanceGuid()));

    if (serviceInstanceNames.size() != description.getServiceUnbindingRequests().size()) {
      throw new CloudFoundryApiException(
          "Number of service instances found does not match the number of service unbinding requests.");
    }

    List<String> unbindings =
        description.getServiceUnbindingRequests().stream()
            .map(
                s -> {
                  String serviceGuid = serviceInstanceGuids.get(s.getServiceInstanceName());
                  if (serviceGuid == null || serviceGuid.isEmpty()) {
                    throw new CloudFoundryApiException(
                        "Unable to find service with the name: '"
                            + s.getServiceInstanceName()
                            + "'");
                  }
                  return serviceGuid;
                })
            .collect(Collectors.toList());

    unbindings.forEach(u -> removeBindings(u, description.getServerGroupId()));

    // Restart by default
    getTask().updateStatus(PHASE, "Restaging application '" + description.getServerGroupName());
    description.getClient().getApplications().restageApplication(description.getServerGroupId());

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
              "Failed to delete Cloud Foundry service bindings between application '"
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
            "Deleted Cloud Foundry service from application '"
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
