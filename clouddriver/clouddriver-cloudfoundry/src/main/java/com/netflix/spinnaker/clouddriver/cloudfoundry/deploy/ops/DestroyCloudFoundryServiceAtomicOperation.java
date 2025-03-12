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
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ServiceBinding;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DestroyCloudFoundryServiceDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    if (description.isRemoveBindings()) {
      task.updateStatus(
          PHASE,
          "Started removing service bindings for '"
              + description.getServiceInstanceName()
              + "' from space "
              + description.getSpace().getName());

      // create map of binding guid to service binding entity
      Map<String, ServiceBinding> map = new HashMap<>();
      description
          .getClient()
          .getServiceInstances()
          .findAllServiceBindingsByServiceName(
              description.getRegion(), description.getServiceInstanceName())
          .stream()
          .forEach(r -> map.put(r.getMetadata().getGuid(), r.getEntity()));

      // make sure that the bindings are only to sg's that belong to the specific spinnaker
      // application
      // before deleting, or else throw
      for (ServiceBinding sb : map.values()) {
        CloudFoundryServerGroup sg =
            description.getClient().getApplications().findById(sb.getAppGuid());
        String appName = description.getApplications().stream().findFirst().get();
        if (!sg.getMoniker().getApp().equals(appName)) {
          throw new IllegalArgumentException(
              "Unable to unbind server group '"
                  + sg.getName()
                  + "' from '"
                  + description.getServiceInstanceName()
                  + "' because it doesn't belong to the application '"
                  + appName
                  + "'");
        }
        task.updateStatus(
            PHASE,
            "Finished removing service bindings for '"
                + description.getServiceInstanceName()
                + "' from space "
                + description.getSpace().getName());
      }

      // delete the service binding
      for (String sbKey : map.keySet()) {
        description.getClient().getServiceInstances().deleteServiceBinding(sbKey);
      }
    }

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
      throw new RuntimeException(
          "Service instance "
              + description.getServiceInstanceName()
              + " not found, in "
              + description.getSpace().getRegion());
    }
    return response;
  }
}
