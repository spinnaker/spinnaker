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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceInstanceResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServiceDescription;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.IN_PROGRESS;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.SUCCEEDED;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.CREATE;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.UPDATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

class DeployCloudFoundryServiceAtomicOperationTest extends AbstractCloudFoundryAtomicOperationTest {
  private DeployCloudFoundryServiceDescription desc = new DeployCloudFoundryServiceDescription();

  @Test
  void deployService() {
    desc.setClient(client);
    desc.setServiceAttributes(new DeployCloudFoundryServiceDescription.ServiceAttributes()
      .setServiceInstanceName("some-service-name")
      .setService("some-service")
      .setServicePlan("some-service-plan")
    );

    ServiceInstanceResponse serviceInstanceResponse = new ServiceInstanceResponse()
      .setServiceInstanceName("some-service-name")
      .setType(UPDATE)
      .setState(IN_PROGRESS);
    when(client.getServiceInstances().createServiceInstance(any(), any(), any(), any(), any(), any()))
      .thenReturn(serviceInstanceResponse);

    DeployCloudFoundryServiceAtomicOperation op = new DeployCloudFoundryServiceAtomicOperation(desc);

    Task task = runOperation(op);
    List<Object> resultObjects = task.getResultObjects();
    assertThat(1).isEqualTo(resultObjects.size());
    Object o = resultObjects.get(0);
    assertThat(o).isInstanceOf(ServiceInstanceResponse.class);
    ServiceInstanceResponse response = (ServiceInstanceResponse) o;
    assertThat(response).isEqualToComparingFieldByFieldRecursively(serviceInstanceResponse);
    assertThat(task.getHistory())
      .has(status("Updating service instance 'some-service-name' from service some-service and service plan some-service-plan"), atIndex(1));
  }

  @Test
  void deployUserProvidedService() {
    desc.setUserProvided(true);
    desc.setClient(client);
    desc.setUserProvidedServiceAttributes(new DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes()
      .setServiceInstanceName("some-up-service-name")
    );

    ServiceInstanceResponse serviceInstanceResponse = new ServiceInstanceResponse()
      .setServiceInstanceName("some-up-service-name")
      .setType(CREATE)
      .setState(SUCCEEDED);
    when(client.getServiceInstances().createUserProvidedServiceInstance(any(), any(), any(), any(), any(), any()))
      .thenReturn(serviceInstanceResponse);

    DeployCloudFoundryServiceAtomicOperation op = new DeployCloudFoundryServiceAtomicOperation(desc);

    Task task = runOperation(op);
    List<Object> resultObjects = task.getResultObjects();
    assertThat(1).isEqualTo(resultObjects.size());
    Object o = resultObjects.get(0);
    assertThat(o).isInstanceOf(ServiceInstanceResponse.class);
    ServiceInstanceResponse response = (ServiceInstanceResponse) o;
    assertThat(response).isEqualToComparingFieldByFieldRecursively(serviceInstanceResponse);
    assertThat(task.getHistory())
      .has(status("Creating user-provided service instance 'some-up-service-name'"), atIndex(1))
      .has(status("Created user-provided service instance 'some-up-service-name'"), atIndex(2));
  }
}
