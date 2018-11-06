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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ServiceInstanceService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ErrorDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.config.CloudFoundryConfigurationProperties;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import org.junit.Before;
import org.junit.Test;
import retrofit.RetrofitError;
import retrofit.client.Response;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class ServiceInstancesTest {
  private ServiceInstances serviceInstances;
  private CloudFoundrySpace cloudFoundrySpace;
  private ServiceInstanceService serviceInstanceService;

  @Before
  public void before() {
    serviceInstanceService = mock(ServiceInstanceService.class);
    Organizations orgs = mock(Organizations.class);
    Spaces spaces = mock(Spaces.class);
    CloudFoundryConfigurationProperties configProps = new CloudFoundryConfigurationProperties();
    configProps.setAsyncOperationTimeoutSecondsDefault(2);
    configProps.setPollingIntervalSeconds(1);
    serviceInstances = new ServiceInstances(serviceInstanceService, orgs, spaces, Duration.ofSeconds(4), Duration.ofSeconds(1));

    Service service = new Service();
    service.setLabel("service1");
    Page<Service> serviceMappingPageOne = new Page<>();
    Resource<Service> resource = new Resource<>();
    Resource.Metadata metadata = new Resource.Metadata();
    metadata.setGuid("service-guid");
    resource.setMetadata(metadata);
    resource.setEntity(service);
    serviceMappingPageOne.setTotalResults(1);
    serviceMappingPageOne.setTotalPages(1);
    serviceMappingPageOne.setResources(Collections.singletonList(resource));
    when(serviceInstanceService.findService(any(), anyListOf(String.class))).thenReturn(serviceMappingPageOne);

    ServicePlan servicePlan = new ServicePlan();
    servicePlan.setName("ServicePlan1");
    Page<ServicePlan> servicePlansPageOne = new Page<>();
    Resource<ServicePlan> resourcePlans = new Resource<>();
    Resource.Metadata planMetadata = new Resource.Metadata();
    planMetadata.setGuid("plan-guid");
    resourcePlans.setMetadata(planMetadata);
    resourcePlans.setEntity(servicePlan);
    servicePlansPageOne.setTotalResults(1);
    servicePlansPageOne.setTotalPages(1);
    servicePlansPageOne.setResources(Collections.singletonList(resourcePlans));
    when(serviceInstanceService.findServicePlans(any(), anyListOf(String.class))).thenReturn(servicePlansPageOne);

    cloudFoundrySpace = CloudFoundrySpace.builder()
      .id("some-space-guid")
      .build();
  }

  @Test
  public void shouldNotMakeAPICallWhenNoServiceNamesAreProvided() {
    CloudFoundryServerGroup cloudFoundryServerGroup = CloudFoundryServerGroup.builder().build();
    serviceInstances.createServiceBindingsByName(cloudFoundryServerGroup, Collections.emptyList());
    verify(serviceInstanceService, never()).all(any(), any(), any());
  }

  @Test
  public void shouldCreateServiceBindingWhenServiceExists() {
    CloudFoundryServerGroup cloudFoundryServerGroup = CloudFoundryServerGroup.builder()
      .account("some-account")
      .id("servergroup-id")
      .space(cloudFoundrySpace)
      .build();

    Page<ServiceInstance> serviceMappingPageOne = new Page<>();
    Resource<ServiceInstance> resource = new Resource<>();
    Resource.Metadata metadata = new Resource.Metadata();
    metadata.setGuid("service-instance-guid");
    resource.setMetadata(metadata);
    serviceMappingPageOne.setTotalResults(1);
    serviceMappingPageOne.setTotalPages(1);
    serviceMappingPageOne.setResources(Collections.singletonList(resource));
    Page<ServiceInstance> serviceMappingPageTwo = new Page<>();
    serviceMappingPageOne.setTotalResults(0);
    serviceMappingPageOne.setTotalPages(0);
    serviceMappingPageTwo.setResources(Collections.emptyList());
    when(serviceInstanceService.all(eq(null), any(), any())).thenReturn(serviceMappingPageOne);
    when(serviceInstanceService.all(eq(1), any(), any())).thenReturn(serviceMappingPageOne);
    when(serviceInstanceService.all(eq(2), any(), any())).thenReturn(serviceMappingPageTwo);

    serviceInstances.createServiceBindingsByName(cloudFoundryServerGroup, Collections.singletonList("service-instance"));

    verify(serviceInstanceService, atLeastOnce()).createServiceBinding(any());
  }

  @Test(expected = CloudFoundryApiException.class)
  public void shouldThrowAnErrorIfServiceNotFound() {
    CloudFoundryServerGroup cloudFoundryServerGroup = CloudFoundryServerGroup.builder()
      .account("some-account")
      .id("servergroup-id")
      .space(cloudFoundrySpace)
      .build();

    Page<ServiceInstance> serviceMappingPageOne = new Page<>();
    serviceMappingPageOne.setTotalResults(0);
    serviceMappingPageOne.setTotalPages(1);
    serviceMappingPageOne.setResources(Collections.emptyList());
    when(serviceInstanceService.all(any(), any(), any())).thenReturn(serviceMappingPageOne);

    serviceInstances.createServiceBindingsByName(cloudFoundryServerGroup, Collections.singletonList("service-instance"));
  }

  @Test
  public void shouldSuccessfullyCreateService() {
    Resource<ServiceInstance> polledServiceInstanceResource = createServiceInstanceResource();
    LastOperation lastPolledOperation = new LastOperation();
    lastPolledOperation.setType(LastOperation.Type.CREATE);
    lastPolledOperation.setState(LastOperation.State.IN_PROGRESS);
    polledServiceInstanceResource.getEntity().setLastOperation(lastPolledOperation);
    Resource<ServiceInstance> succeededServiceInstanceResource = createServiceInstanceResource();
    LastOperation lastOperation = new LastOperation();
    lastOperation.setType(LastOperation.Type.CREATE);
    lastOperation.setState(LastOperation.State.SUCCEEDED);
    succeededServiceInstanceResource.getEntity().setLastOperation(lastOperation);

    when(serviceInstanceService.createServiceInstance(any())).thenReturn(createServiceInstanceResource());
    when(serviceInstanceService.getServiceInstanceById(any())).thenReturn(polledServiceInstanceResource, polledServiceInstanceResource, succeededServiceInstanceResource);

    serviceInstances.createServiceInstance("new-service-instance-name",
      "serviceName",
      "ServicePlan1",
      Collections.emptySet(),
      null,
      cloudFoundrySpace);

    verify(serviceInstanceService, times(1)).createServiceInstance(any());
    verify(serviceInstanceService, never()).updateServiceInstance(any(), any());
    verify(serviceInstanceService, times(3)).getServiceInstanceById("service-instance-guid");
  }

  @Test(expected = CloudFoundryApiException.class)
  public void shouldThrowExceptionWhenCreationReturnsHttpNotFound() {
    RetrofitError retrofitErrorNotFound = mock(RetrofitError.class);
    Response notFoundResponse = new Response("someUri", 404, "whynot", Collections.emptyList(), null);
    when(retrofitErrorNotFound.getResponse()).thenReturn(notFoundResponse);

    when(serviceInstanceService.createServiceInstance(any())).thenThrow(retrofitErrorNotFound);

    serviceInstances.createServiceInstance("newServiceInstanceName",
      "serviceName",
      "ServicePlan1",
      Collections.emptySet(),
      null,
      cloudFoundrySpace);

    verify(serviceInstanceService, times(1)).createServiceInstance(any());
    verify(serviceInstanceService, never()).getServiceInstanceById("service-instance-guid");
    verify(serviceInstanceService, never()).updateServiceInstance(any(), any());
  }

  @Test(expected = CloudFoundryApiException.class)
  public void shouldThrowExceptionWhenGetInstanceByGuidReturnsHttpNotFound() {
    Resource<ServiceInstance> polledServiceInstanceResource = createServiceInstanceResource();
    LastOperation lastOperation = new LastOperation();
    lastOperation.setType(LastOperation.Type.CREATE);
    lastOperation.setState(LastOperation.State.SUCCEEDED);
    polledServiceInstanceResource.getEntity().setLastOperation(lastOperation);

    RetrofitError retrofitErrorNotFound = mock(RetrofitError.class);
    Response notFoundResponse = new Response("someUri", 404, "whynot", Collections.emptyList(), null);
    when(retrofitErrorNotFound.getResponse()).thenReturn(notFoundResponse);

    when(serviceInstanceService.createServiceInstance(any())).thenReturn(createServiceInstanceResource());
    when(serviceInstanceService.getServiceInstanceById(any())).thenThrow(retrofitErrorNotFound);

    serviceInstances.createServiceInstance("newServiceInstanceName",
      "serviceName",
      "ServicePlan1",
      Collections.emptySet(),
      null,
      cloudFoundrySpace);

    verify(serviceInstanceService, times(1)).createServiceInstance(any());
    verify(serviceInstanceService, atLeastOnce()).getServiceInstanceById("service-instance-guid");
    verify(serviceInstanceService, never()).updateServiceInstance(any(), any());
  }

  @Test
  public void shouldThrowExceptionWhenServiceInstanceCreationFails() {
    Resource<ServiceInstance> polledServiceInstanceResource = createServiceInstanceResource();
    LastOperation lastPolledOperation = new LastOperation();
    lastPolledOperation.setType(LastOperation.Type.CREATE);
    lastPolledOperation.setState(LastOperation.State.IN_PROGRESS);
    polledServiceInstanceResource.getEntity().setLastOperation(lastPolledOperation);
    Resource<ServiceInstance> failedServiceInstanceResource = createServiceInstanceResource();
    LastOperation lastFailedOperation = new LastOperation();
    lastFailedOperation.setType(LastOperation.Type.CREATE);
    lastFailedOperation.setState(LastOperation.State.FAILED);
    failedServiceInstanceResource.getEntity().setLastOperation(lastFailedOperation);

    when(serviceInstanceService.createServiceInstance(any())).thenReturn(createServiceInstanceResource());
    when(serviceInstanceService.getServiceInstanceById(any())).thenReturn(polledServiceInstanceResource, polledServiceInstanceResource, failedServiceInstanceResource);

    try {
      serviceInstances.createServiceInstance("newServiceInstanceName",
        "serviceName",
        "ServicePlan1",
        Collections.emptySet(),
        null,
        cloudFoundrySpace);
      fail("Expected CloudFoundryApiException");
    } catch (CloudFoundryApiException cfa) {
      assertThat(cfa.getMessage()).contains("Service instance 'newServiceInstanceName' creation failed");
    } catch (Throwable t) {
      fail("Expected CloudFoundryApiException; got " + t);
    }

    verify(serviceInstanceService, atLeastOnce()).createServiceInstance(any());
    verify(serviceInstanceService, times(3)).getServiceInstanceById("service-instance-guid");
    verify(serviceInstanceService, never()).updateServiceInstance(any(), any());
  }

  @Test
  public void shouldThrowExceptionWhenServiceInstanceCreationTimesOut() {
    Resource<ServiceInstance> polledServiceInstanceResource = createServiceInstanceResource();
    LastOperation lastOperation = new LastOperation();
    lastOperation.setType(LastOperation.Type.CREATE);
    lastOperation.setState(LastOperation.State.IN_PROGRESS);
    polledServiceInstanceResource.getEntity().setLastOperation(lastOperation);

    when(serviceInstanceService.createServiceInstance(any())).thenReturn(createServiceInstanceResource());
    when(serviceInstanceService.getServiceInstanceById(any())).thenReturn(polledServiceInstanceResource);

    try {
      serviceInstances.createServiceInstance("newServiceInstanceName",
        "serviceName",
        "ServicePlan1",
        Collections.emptySet(),
        null,
        cloudFoundrySpace);
      fail("Expected CloudFoundryApiException");
    } catch(CloudFoundryApiException cfa) {
      assertThat(cfa.getMessage()).contains("Service instance 'newServiceInstanceName' creation did not complete");
    } catch (Throwable t) {
      fail("Expected CloudFoundryApiException; got " + t);
    }

    verify(serviceInstanceService, times(1)).createServiceInstance(any());
    verify(serviceInstanceService, times(4)).getServiceInstanceById("service-instance-guid");
    verify(serviceInstanceService, never()).updateServiceInstance(any(), any());
  }

  @Test(expected = RuntimeException.class)
  public void throwExceptionWhenNoServicePlanExistsWithTheNameProvided() {
    Page<ServicePlan> servicePlansPageOne = new Page<>();
    servicePlansPageOne.setTotalResults(0);
    servicePlansPageOne.setTotalPages(1);
    servicePlansPageOne.setResources(Collections.emptyList());
    when(serviceInstanceService.findServicePlans(any(), anyListOf(String.class))).thenReturn(servicePlansPageOne);

    serviceInstances.createServiceInstance("newServiceInstanceName",
      "serviceName",
      "servicePlanName",
      Collections.emptySet(),
      null,
      cloudFoundrySpace);
  }

  @Test
  public void shouldUpdateTheServiceIfAlreadyExists() {
    Resource<ServiceInstance> polledServiceInstanceResource = createServiceInstanceResource();
    LastOperation lastOperation = new LastOperation();
    lastOperation.setType(LastOperation.Type.UPDATE);
    lastOperation.setState(LastOperation.State.SUCCEEDED);
    polledServiceInstanceResource.getEntity().setLastOperation(lastOperation);

    ErrorDescription errorDescription = new ErrorDescription();
    errorDescription.setCode(ErrorDescription.Code.SERVICE_ALREADY_EXISTS);
    RetrofitError retrofitError = mock(RetrofitError.class);
    when(retrofitError.getBodyAs(any())).thenReturn(errorDescription);

    when(serviceInstanceService.createServiceInstance(any())).thenThrow(retrofitError);
    when(serviceInstanceService.findAllServiceInstancesBySpaceId(any(), any(), anyListOf(String.class))).thenReturn(createServiceInstancePage());
    when(serviceInstanceService.updateServiceInstance(any(), any())).thenReturn(createServiceInstanceResource());
    when(serviceInstanceService.getServiceInstanceById(any())).thenReturn(polledServiceInstanceResource);

    serviceInstances.createServiceInstance("newServiceInstanceName",
      "serviceName",
      "ServicePlan1",
      Collections.emptySet(),
      null,
      cloudFoundrySpace);

    verify(serviceInstanceService, times(1)).createServiceInstance(any());
    verify(serviceInstanceService, times(1)).updateServiceInstance(any(), any());
    verify(serviceInstanceService, atLeastOnce()).getServiceInstanceById("service-instance-guid");
  }


  @Test(expected = CloudFoundryApiException.class)
  public void shouldThrowExceptionIfServiceExistsAndNeedsChangingButUpdateFails() {
    Page<ServiceInstance> serviceInstancePage = createServiceInstancePage();

    ErrorDescription errorDescription = new ErrorDescription();
    errorDescription.setCode(ErrorDescription.Code.SERVICE_ALREADY_EXISTS);
    RetrofitError retrofitError = mock(RetrofitError.class);
    when(retrofitError.getBodyAs(any())).thenReturn(errorDescription);

    RetrofitError updateError = mock(RetrofitError.class);
    when(updateError.getResponse()).thenReturn(new Response("url", 418, "reason", Collections.emptyList(), null));
    when(updateError.getBodyAs(any())).thenReturn(new ErrorDescription());

    when(serviceInstanceService.createServiceInstance(any())).thenThrow(retrofitError);
    when(serviceInstanceService.findAllServiceInstancesBySpaceId(any(), any(), anyListOf(String.class))).thenReturn(serviceInstancePage);
    when(serviceInstanceService.updateServiceInstance(any(), any())).thenThrow(updateError);

    serviceInstances.createServiceInstance("newServiceInstanceName",
      "serviceName",
      "ServicePlan1",
      Collections.emptySet(),
      null,
      cloudFoundrySpace);

    verify(serviceInstanceService, times(1)).createServiceInstance(any());
    verify(serviceInstanceService, times(1)).updateServiceInstance(any(), any());
  }

  @Test(expected = CloudFoundryApiException.class)
  public void shouldThrowCloudFoundryApiErrorWhenMoreThanOneServiceInstanceWithTheSameNameExists() {
    ServiceInstance serviceInstance = new ServiceInstance();
    serviceInstance.setName("newServiceInstanceName");
    serviceInstance.setServicePlanGuid("plan-guid");

    Page<ServiceInstance> serviceInstancePage = new Page<>();
    Resource<ServiceInstance> serviceInstanceResource = new Resource<>();
    Resource.Metadata serviceInstanceMetadata = new Resource.Metadata();
    serviceInstanceMetadata.setGuid("service-instance-guid");
    serviceInstanceResource.setMetadata(serviceInstanceMetadata);
    serviceInstanceResource.setEntity(serviceInstance);
    serviceInstancePage.setTotalResults(2);
    serviceInstancePage.setTotalPages(1);
    serviceInstancePage.setResources(Arrays.asList(serviceInstanceResource, serviceInstanceResource));

    ErrorDescription errorDescription = new ErrorDescription();
    errorDescription.setCode(ErrorDescription.Code.SERVICE_ALREADY_EXISTS);
    RetrofitError retrofitError = mock(RetrofitError.class);
    when(retrofitError.getBodyAs(any())).thenReturn(errorDescription);

    when(serviceInstanceService.createServiceInstance(any())).thenThrow(retrofitError);
    when(serviceInstanceService.findAllServiceInstancesBySpaceId(any(), any(), anyListOf(String.class))).thenReturn(serviceInstancePage);

    serviceInstances.createServiceInstance("newServiceInstanceName",
      "serviceName",
      "ServicePlan1",
      Collections.emptySet(),
      null,
      cloudFoundrySpace);
  }

  private Resource<ServiceInstance> createServiceInstanceResource() {
    ServiceInstance serviceInstance = new ServiceInstance();
    serviceInstance.setName("newServiceInstanceName");
    serviceInstance.setServicePlanGuid("plan-guid");
    Resource<ServiceInstance> serviceInstanceResource = new Resource<>();
    Resource.Metadata serviceInstanceMetadata = new Resource.Metadata();
    serviceInstanceMetadata.setGuid("service-instance-guid");
    serviceInstanceResource.setMetadata(serviceInstanceMetadata);
    serviceInstanceResource.setEntity(serviceInstance);
    return serviceInstanceResource;
  }

  private Page<ServiceInstance> createServiceInstancePage() {
    Page<ServiceInstance> serviceInstancePage = new Page<>();
    serviceInstancePage.setTotalResults(1);
    serviceInstancePage.setTotalPages(1);
    serviceInstancePage.setResources(Collections.singletonList(createServiceInstanceResource()));
    return serviceInstancePage;
  }
}
