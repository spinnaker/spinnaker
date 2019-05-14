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

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.UPDATE;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceInstanceResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServiceDescription;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DeployCloudFoundryServiceAtomicOperation
    implements AtomicOperation<ServiceInstanceResponse> {
  private static final String PHASE = "DEPLOY_SERVICE";
  private final DeployCloudFoundryServiceDescription description;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public ServiceInstanceResponse operate(List priorOutputs) {
    Task task = getTask();
    final ServiceInstanceResponse serviceInstanceResponse;
    final String serviceInstanceName;
    if (!description.isUserProvided()) {
      DeployCloudFoundryServiceDescription.ServiceAttributes serviceAttributes =
          description.getServiceAttributes();
      serviceInstanceName = serviceAttributes.getServiceInstanceName();
      serviceInstanceResponse =
          description
              .getClient()
              .getServiceInstances()
              .createServiceInstance(
                  serviceInstanceName,
                  serviceAttributes.getService(),
                  serviceAttributes.getServicePlan(),
                  serviceAttributes.getTags(),
                  serviceAttributes.getParameterMap(),
                  serviceAttributes.isUpdatable(),
                  description.getSpace());
      String gerund = serviceInstanceResponse.getType() == UPDATE ? "Updating" : "Creating";
      task.updateStatus(
          PHASE,
          gerund
              + " service instance '"
              + serviceInstanceName
              + "' from service "
              + serviceAttributes.getService()
              + " and service plan "
              + serviceAttributes.getServicePlan());
    } else {
      DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes
          userProvidedServiceAttributes = description.getUserProvidedServiceAttributes();
      serviceInstanceName = userProvidedServiceAttributes.getServiceInstanceName();
      task.updateStatus(
          PHASE, "Creating user-provided service instance '" + serviceInstanceName + "'");
      serviceInstanceResponse =
          description
              .getClient()
              .getServiceInstances()
              .createUserProvidedServiceInstance(
                  serviceInstanceName,
                  userProvidedServiceAttributes.getSyslogDrainUrl(),
                  userProvidedServiceAttributes.getTags(),
                  userProvidedServiceAttributes.getCredentials(),
                  userProvidedServiceAttributes.getRouteServiceUrl(),
                  userProvidedServiceAttributes.isUpdatable(),
                  description.getSpace());
      String verb = serviceInstanceResponse.getType() == UPDATE ? "Updated" : "Created";
      task.updateStatus(
          PHASE, verb + " user-provided service instance '" + serviceInstanceName + "'");
    }

    return serviceInstanceResponse;
  }
}
