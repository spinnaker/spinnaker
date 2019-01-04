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

import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServiceDescription;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class DeployCloudFoundryServiceAtomicOperation implements AtomicOperation<Void> {
  private static final String PHASE = "DEPLOY_SERVICE";
  private final DeployCloudFoundryServiceDescription description;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List priorOutputs) {
    Task task = getTask();
    switch (description.getServiceType()) {
      case "service":
        DeployCloudFoundryServiceDescription.ServiceAttributes serviceAttributes = description.getServiceAttributes();
        task.updateStatus(PHASE, "Creating service instance '" + serviceAttributes.getServiceName() + "' from service " + serviceAttributes.getService() + " and service plan " + serviceAttributes.getServicePlan());
        description
          .getClient()
          .getServiceInstances()
          .createServiceInstance(
            serviceAttributes.getServiceName(),
            serviceAttributes.getService(),
            serviceAttributes.getServicePlan(),
            serviceAttributes.getTags(),
            serviceAttributes.getParameterMap(),
            description.getSpace(),
            description.getTimeout());
        task.updateStatus(PHASE, "Created service instance '" + serviceAttributes.getServiceName() + "'");
        break;
      case "userProvided":
        DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes userProvidedServiceAttributes = description.getUserProvidedServiceAttributes();
        task.updateStatus(PHASE, "Creating user provided service instance '" + userProvidedServiceAttributes.getServiceName() + "'");
        description
          .getClient()
          .getServiceInstances()
          .createUserProvidedServiceInstance(
            userProvidedServiceAttributes.getServiceName(),
            userProvidedServiceAttributes.getSyslogDrainUrl(),
            userProvidedServiceAttributes.getTags(),
            userProvidedServiceAttributes.getCredentialsMap(),
            userProvidedServiceAttributes.getRouteServiceUrl(),
            description.getSpace()
          );
        task.updateStatus(PHASE, "Created user provided service instance '" + userProvidedServiceAttributes.getServiceName() + "'");
        break;
    }
    return null;
  }
}
