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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.MockCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.AbstractServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Resource;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.UserProvidedServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ProcessStats;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.CreateCloudFoundryServiceBindingDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class CreateCloudFoundryServiceBindingRequestAtomicOperationTest
    extends AbstractCloudFoundryAtomicOperationTest {

  OperationPoller poller = mock(OperationPoller.class);
  CloudFoundryClient client = new MockCloudFoundryClient();

  private final CloudFoundrySpace cloudFoundrySpace =
      CloudFoundrySpace.builder()
          .name("space")
          .organization(CloudFoundryOrganization.builder().name("org").build())
          .build();

  @Test
  public void shouldCreateServiceBinding() {
    CreateCloudFoundryServiceBindingDescription desc =
        new CreateCloudFoundryServiceBindingDescription();
    desc.setSpace(cloudFoundrySpace);
    desc.setRegion(cloudFoundrySpace.getRegion());
    desc.setClient(client);
    desc.setRestageRequired(true);
    desc.setServerGroupName("app1");
    CreateCloudFoundryServiceBindingDescription.ServiceBindingRequest binding =
        new CreateCloudFoundryServiceBindingDescription.ServiceBindingRequest(
            "service1", null, false);
    desc.setServiceBindingRequests(Collections.singletonList(binding));

    CreateCloudFoundryServiceBindingAtomicOperation operation =
        new CreateCloudFoundryServiceBindingAtomicOperation(poller, desc);

    UserProvidedServiceInstance serviceInstance = new UserProvidedServiceInstance();
    serviceInstance.setName("service1");

    Resource<UserProvidedServiceInstance> resource = new Resource<>();
    Resource.Metadata metadata = new Resource.Metadata();
    metadata.setGuid("123abc");
    resource.setEntity(serviceInstance);
    resource.setMetadata(metadata);

    List<Resource<? extends AbstractServiceInstance>> instances = List.of(resource);
    when(desc.getClient().getServiceInstances().findAllServicesBySpaceAndNames(any(), any()))
        .thenReturn(instances);
    when(poller.waitForOperation(any(Supplier.class), any(), any(), any(), any(), any()))
        .thenReturn(ProcessStats.State.RUNNING);

    Task task = runOperation(operation);

    verify(client.getServiceInstances()).createServiceBinding(any());
    assertThat(task.getHistory())
        .has(
            status(
                "Creating Cloud Foundry service bindings between application 'app1' and services: [service1]"),
            atIndex(1));
    assertThat(task.getHistory())
        .has(
            status(
                "Created Cloud Foundry service bindings between application 'app1' and services: [service1]"),
            atIndex(3));
  }

  @Test
  public void shouldCreateServiceBindingWithParameters() {
    CreateCloudFoundryServiceBindingDescription desc =
        new CreateCloudFoundryServiceBindingDescription();
    desc.setSpace(cloudFoundrySpace);
    desc.setRegion(cloudFoundrySpace.getRegion());
    desc.setClient(client);
    desc.setRestageRequired(true);
    desc.setServerGroupName("app1");
    CreateCloudFoundryServiceBindingDescription.ServiceBindingRequest binding =
        new CreateCloudFoundryServiceBindingDescription.ServiceBindingRequest(
            "service1", null, false);
    desc.setServiceBindingRequests(Collections.singletonList(binding));

    CreateCloudFoundryServiceBindingAtomicOperation operation =
        new CreateCloudFoundryServiceBindingAtomicOperation(poller, desc);

    UserProvidedServiceInstance serviceInstance = new UserProvidedServiceInstance();
    serviceInstance.setName("service1");

    Resource<UserProvidedServiceInstance> resource = new Resource<>();
    Resource.Metadata metadata = new Resource.Metadata();
    metadata.setGuid("123abc");
    resource.setEntity(serviceInstance);
    resource.setMetadata(metadata);

    List<Resource<? extends AbstractServiceInstance>> instances = List.of(resource);
    when(desc.getClient().getServiceInstances().findAllServicesBySpaceAndNames(any(), any()))
        .thenReturn(instances);
    when(poller.waitForOperation(any(Supplier.class), any(), any(), any(), any(), any()))
        .thenReturn(ProcessStats.State.RUNNING);

    Task task = runOperation(operation);

    verify(client.getServiceInstances()).createServiceBinding(any());
    assertThat(task.getHistory())
        .has(
            status(
                "Creating Cloud Foundry service bindings between application 'app1' and services: [service1]"),
            atIndex(1));
    assertThat(task.getHistory())
        .has(
            status(
                "Created Cloud Foundry service bindings between application 'app1' and services: [service1]"),
            atIndex(3));
  }
}
