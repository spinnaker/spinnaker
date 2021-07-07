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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.MockCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.AbstractServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Resource;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ServiceBinding;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeleteCloudFoundryServiceBindingDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class DeleteCloudFoundryServiceBindingAtomicOperationTest
    extends AbstractCloudFoundryAtomicOperationTest {

  private final CloudFoundrySpace cloudFoundrySpace =
      CloudFoundrySpace.builder()
          .name("space")
          .organization(CloudFoundryOrganization.builder().name("org").build())
          .build();
  CloudFoundryClient client = new MockCloudFoundryClient();

  @Test
  public void shouldDeleteServiceBinding() {
    DeleteCloudFoundryServiceBindingDescription desc =
        new DeleteCloudFoundryServiceBindingDescription();
    desc.setSpace(cloudFoundrySpace);
    desc.setRegion(cloudFoundrySpace.getRegion());
    desc.setClient(client);
    desc.setServerGroupName("app1");
    desc.setServerGroupId("app-guid-123");

    DeleteCloudFoundryServiceBindingDescription.ServiceUnbindingRequest unbinding =
        new DeleteCloudFoundryServiceBindingDescription.ServiceUnbindingRequest("service1");
    desc.setServiceUnbindingRequests(Collections.singletonList(unbinding));

    DeleteCloudFoundryServiceBindingAtomicOperation operation =
        new DeleteCloudFoundryServiceBindingAtomicOperation(desc);
    ServiceBinding appServiceInstance = new ServiceBinding();
    appServiceInstance.setName("service1");
    appServiceInstance.setAppGuid("app1");
    appServiceInstance.setServiceInstanceGuid("service-guid-123");

    Resource<ServiceBinding> appResource = new Resource<>();
    Resource.Metadata appMetadata = new Resource.Metadata();
    appMetadata.setGuid("service-guid-123");
    appResource.setEntity(appServiceInstance);
    appResource.setMetadata(appMetadata);

    List<Resource<ServiceBinding>> appInstances = List.of(appResource);
    when(desc.getClient().getApplications().getServiceBindingsByApp(any()))
        .thenReturn(appInstances);

    ServiceInstance serviceInstance = new ServiceInstance();
    serviceInstance.setName("service1");

    Resource<ServiceInstance> resource = new Resource<>();
    Resource.Metadata metadata = new Resource.Metadata();
    metadata.setGuid("service-guid-123");
    resource.setEntity(serviceInstance);
    resource.setMetadata(metadata);

    List<Resource<? extends AbstractServiceInstance>> serviceInstances = List.of(resource);

    when(desc.getClient().getServiceInstances().findAllServicesBySpaceAndNames(any(), any()))
        .thenReturn(serviceInstances);

    Task task = runOperation(operation);

    verify(client.getServiceInstances()).deleteServiceBinding(any());
    assertThat(task.getHistory())
        .has(
            status("Unbinding Cloud Foundry application 'app1' from services: [service1]"),
            atIndex(1));
    assertThat(task.getHistory())
        .has(
            status(
                "Successfully unbound Cloud Foundry application 'app1' from services: [service1]"),
            atIndex(2));
  }
}
