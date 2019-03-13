/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceInstanceResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DestroyCloudFoundryServiceDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.IN_PROGRESS;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.NOT_FOUND;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.DELETE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

class DestroyCloudFoundryServiceAtomicOperationTest extends AbstractCloudFoundryAtomicOperationTest {
  private DestroyCloudFoundryServiceDescription desc = new DestroyCloudFoundryServiceDescription();

  @Test
  void destroyCloudFoundryService() {
    desc.setServiceInstanceName("service-instance-name");
    desc.setSpace(CloudFoundrySpace.builder().name("space-name").build());
    desc.setClient(client);

    ServiceInstanceResponse serviceInstanceResponse = new ServiceInstanceResponse()
      .setServiceInstanceName("service-instance-name")
      .setType(DELETE)
      .setState(IN_PROGRESS);

    when(client.getServiceInstances().destroyServiceInstance(any(), any()))
      .thenReturn(serviceInstanceResponse);

    DestroyCloudFoundryServiceAtomicOperation op = new DestroyCloudFoundryServiceAtomicOperation(desc);

    Task task = runOperation(op);
    List<Object> resultObjects = task.getResultObjects();
    assertThat(1).isEqualTo(resultObjects.size());
    Object o = resultObjects.get(0);
    assertThat(o).isInstanceOf(ServiceInstanceResponse.class);
    ServiceInstanceResponse response = (ServiceInstanceResponse) o;
    assertThat(response).isEqualToComparingFieldByFieldRecursively(serviceInstanceResponse);
    assertThat(task.getHistory())
      .has(status("Started removing service instance 'service-instance-name' from space space-name"), atIndex(1));
  }

  @Test
  void destroyUserProvidedService() {
    desc.setServiceInstanceName("up-service-instance-name");
    desc.setSpace(CloudFoundrySpace.builder().name("space-name").build());
    desc.setClient(client);

    ServiceInstanceResponse serviceInstanceResponse = new ServiceInstanceResponse()
      .setServiceInstanceName("up-service-instance-name")
      .setType(DELETE)
      .setState(NOT_FOUND);

    when(client.getServiceInstances().destroyServiceInstance(any(), any()))
      .thenReturn(serviceInstanceResponse);

    DestroyCloudFoundryServiceAtomicOperation op = new DestroyCloudFoundryServiceAtomicOperation(desc);

    Task task = runOperation(op);
    List<Object> resultObjects = task.getResultObjects();
    assertThat(1).isEqualTo(resultObjects.size());
    Object o = resultObjects.get(0);
    assertThat(o).isInstanceOf(ServiceInstanceResponse.class);
    ServiceInstanceResponse response = (ServiceInstanceResponse) o;
    assertThat(response).isEqualToComparingFieldByFieldRecursively(serviceInstanceResponse);
    assertThat(task.getHistory())
      .has(status("Started removing service instance 'up-service-instance-name' from space space-name"), atIndex(1))
      .has(status("Finished removing service instance 'up-service-instance-name'"), atIndex(2));
  }
}
