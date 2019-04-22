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


import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceKeyResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeleteCloudFoundryServiceKeyDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import io.vavr.collection.HashMap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.SUCCEEDED;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.DELETE_SERVICE_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;
import static org.mockito.Mockito.*;

class DeleteCloudFoundryServiceKeyAtomicOperationTest extends AbstractCloudFoundryAtomicOperationTest {
  private DeleteCloudFoundryServiceKeyDescription desc = new DeleteCloudFoundryServiceKeyDescription();
  private final CloudFoundrySpace cloudFoundrySpace = CloudFoundrySpace.builder()
    .name("space")
    .organization(CloudFoundryOrganization.builder()
      .name("org")
      .build())
    .build();

  @Test
  void printsTwoStatusesWhenDeletingServiceKeySucceeds() {
    String serviceInstanceName = "service-instance-name";
    String serviceKeyName = "service-key-name";
    desc.setSpace(cloudFoundrySpace);
    desc.setServiceInstanceName(serviceInstanceName);
    desc.setServiceKeyName(serviceKeyName);
    desc.setClient(client);
    ServiceKeyResponse serviceKeyResponse = (ServiceKeyResponse) new ServiceKeyResponse()
      .setServiceKey(HashMap.<String, Object>of(
        "username", "user-1"
      ).toJavaMap())
      .setServiceKeyName(serviceKeyName)
      .setServiceInstanceName(serviceInstanceName)
      .setType(DELETE_SERVICE_KEY)
      .setState(SUCCEEDED);
    when(client.getServiceKeys().deleteServiceKey(any(), any(), any()))
      .thenReturn(serviceKeyResponse);

    DeleteCloudFoundryServiceKeyAtomicOperation op = new DeleteCloudFoundryServiceKeyAtomicOperation(desc);

    Task task = runOperation(op);

    verify(client.getServiceKeys()).deleteServiceKey(eq(cloudFoundrySpace), eq(serviceInstanceName), eq(serviceKeyName));
    assertThat(task.getHistory())
      .has(status("Deleting service key 'service-key-name' for service 'service-instance-name' in 'org > space'"),
        atIndex(1));
    assertThat(task.getHistory())
      .has(status("Finished deleting service key 'service-key-name'"),
        atIndex(2));
    List<Object> resultObjects = task.getResultObjects();
    assertThat(resultObjects.size()).isEqualTo(1);
    Object o = resultObjects.get(0);
    assertThat(o).isInstanceOf(ServiceKeyResponse.class);
    ServiceKeyResponse response = (ServiceKeyResponse) o;
    assertThat(response).isEqualToComparingFieldByFieldRecursively(serviceKeyResponse);
  }

  @Test
  void printsOnlyOneStatusWhenDeletionFails() {
    String serviceInstanceName = "service-instance-name";
    String serviceKeyName = "service-key-name";
    desc.setSpace(cloudFoundrySpace);
    desc.setServiceInstanceName(serviceInstanceName);
    desc.setServiceKeyName(serviceKeyName);
    desc.setClient(client);

    when(client.getServiceKeys().deleteServiceKey(any(), any(), any()))
      .thenThrow(new CloudFoundryApiException("Much fail"));

    DeleteCloudFoundryServiceKeyAtomicOperation op = new DeleteCloudFoundryServiceKeyAtomicOperation(desc);

    Task task = runOperation(op);

    verify(client.getServiceKeys()).deleteServiceKey(eq(cloudFoundrySpace), eq(serviceInstanceName), eq(serviceKeyName));
    assertThat(task.getHistory().size()).isEqualTo(2);
    assertThat(task.getHistory())
      .has(status("Deleting service key 'service-key-name' for service 'service-instance-name' in 'org > space'"),
        atIndex(1));
    List<Object> resultObjects = task.getResultObjects();
    assertThat(resultObjects.size()).isEqualTo(1);
    Object o = resultObjects.get(0);
    assertThat(o).isNotInstanceOf(ServiceKeyResponse.class);
  }
}
