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
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import org.junit.jupiter.api.Test;
import retrofit.RetrofitError;
import retrofit.client.Response;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ErrorDescription.Code.SERVICE_ALREADY_EXISTS;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.*;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

class ServiceInstancesTest {
  private CloudFoundryOrganization cloudFoundryOrganization = CloudFoundryOrganization.builder()
    .id("some-org-guid")
    .build();
  private CloudFoundrySpace cloudFoundrySpace = CloudFoundrySpace.builder()
    .id("some-space-guid")
    .name("some-space")
    .organization(cloudFoundryOrganization)
    .build();
  private ServiceInstanceService serviceInstanceService = mock(ServiceInstanceService.class);
  private Organizations orgs = mock(Organizations.class);
  private Spaces spaces = mock(Spaces.class);
  private ServiceInstances serviceInstances = new ServiceInstances(serviceInstanceService, orgs, spaces, Duration.ofMillis(10), Duration.ofMillis(10));

  {
    when(serviceInstanceService.findService(any(), anyListOf(String.class)))
      .thenReturn(Page.singleton(new Service().setLabel("service1"), "service-guid"));

    when(serviceInstanceService.findServicePlans(any(), anyListOf(String.class)))
      .thenReturn(Page.singleton(new ServicePlan().setName("ServicePlan1"), "plan-guid"));
  }

  @Test
  void shouldNotMakeAPICallWhenNoServiceNamesAreProvided() {
    CloudFoundryServerGroup cloudFoundryServerGroup = CloudFoundryServerGroup.builder().build();
    serviceInstances.createServiceBindingsByName(cloudFoundryServerGroup, Collections.emptyList());
    verify(serviceInstanceService, never()).all(any(), any(), any());
  }

  @Test
  void shouldCreateServiceBindingWhenServiceExists() {
    CloudFoundryServerGroup cloudFoundryServerGroup = CloudFoundryServerGroup.builder()
      .account("some-account")
      .id("servergroup-id")
      .space(cloudFoundrySpace)
      .build();

    Page<ServiceInstance> serviceMappingPageOne = Page.singleton(null, "service-instance-guid");
    serviceMappingPageOne.setTotalResults(0);
    serviceMappingPageOne.setTotalPages(0);
    when(serviceInstanceService.all(eq(null), any(), any())).thenReturn(serviceMappingPageOne);
    when(serviceInstanceService.all(eq(1), any(), any())).thenReturn(serviceMappingPageOne);

    Page<UserProvidedServiceInstance> userProvidedserviceMappingPageOne = new Page<>();
    userProvidedserviceMappingPageOne.setTotalResults(0);
    userProvidedserviceMappingPageOne.setTotalPages(0);
    when(serviceInstanceService.allUserProvided(eq(null), any())).thenReturn(userProvidedserviceMappingPageOne);
    when(serviceInstanceService.allUserProvided(eq(1), any())).thenReturn(userProvidedserviceMappingPageOne);

    serviceInstances.createServiceBindingsByName(cloudFoundryServerGroup, Collections.singletonList("service-instance"));

    verify(serviceInstanceService, atLeastOnce()).createServiceBinding(any());
  }

  @Test
  void shouldCreateServiceBindingWhenUserProvidedServiceExists() {
    CloudFoundryServerGroup cloudFoundryServerGroup = CloudFoundryServerGroup.builder()
      .account("some-account")
      .id("servergroup-id")
      .space(cloudFoundrySpace)
      .build();

    Page<ServiceInstance> serviceMappingPageOne = new Page<>();
    serviceMappingPageOne.setTotalResults(0);
    serviceMappingPageOne.setTotalPages(0);
    when(serviceInstanceService.all(eq(null), any(), any())).thenReturn(serviceMappingPageOne);
    when(serviceInstanceService.all(eq(1), any(), any())).thenReturn(serviceMappingPageOne);

    Page<UserProvidedServiceInstance> userProvidedserviceMappingPageOne = Page.singleton(null, "service-instance-guid");
    userProvidedserviceMappingPageOne.setTotalResults(0);
    userProvidedserviceMappingPageOne.setTotalPages(0);
    when(serviceInstanceService.allUserProvided(eq(null), any())).thenReturn(userProvidedserviceMappingPageOne);
    when(serviceInstanceService.allUserProvided(eq(1), any())).thenReturn(userProvidedserviceMappingPageOne);

    serviceInstances.createServiceBindingsByName(cloudFoundryServerGroup, Collections.singletonList("service-instance"));

    verify(serviceInstanceService, atLeastOnce()).createServiceBinding(any());
  }

  @Test
  void shouldThrowAnErrorIfServiceNotFound() {
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

    Page<UserProvidedServiceInstance> userProvidedserviceMappingPageOne = new Page<>();
    userProvidedserviceMappingPageOne.setTotalResults(0);
    userProvidedserviceMappingPageOne.setTotalPages(0);
    when(serviceInstanceService.allUserProvided(eq(null), any())).thenReturn(userProvidedserviceMappingPageOne);
    when(serviceInstanceService.allUserProvided(eq(1), any())).thenReturn(userProvidedserviceMappingPageOne);

    assertThrows(CloudFoundryApiException.class, () ->
      serviceInstances.createServiceBindingsByName(cloudFoundryServerGroup, Collections.singletonList("service-instance"))
    );
  }

  @Test
  void shouldSuccessfullyCreateService() {
    Resource<ServiceInstance> succeededServiceInstanceResource = createServiceInstanceResource();
    succeededServiceInstanceResource.getEntity().setLastOperation(new LastOperation().setType(CREATE).setState(SUCCEEDED));

    when(serviceInstanceService.createServiceInstance(any())).thenReturn(createServiceInstanceResource());
    when(serviceInstanceService.getServiceInstanceById(any())).thenReturn(succeededServiceInstanceResource);

    serviceInstances.createServiceInstance("new-service-instance-name",
      "serviceName",
      "ServicePlan1",
      Collections.emptySet(),
      null,
      cloudFoundrySpace,
      Duration.ofSeconds(4));

    verify(serviceInstanceService, times(1)).createServiceInstance(any());
    verify(serviceInstanceService, never()).updateServiceInstance(any(), any());
    verify(serviceInstanceService, times(1)).getServiceInstanceById("service-instance-guid");
  }

  @Test
  void shouldThrowExceptionWhenCreationReturnsHttpNotFound() {
    RetrofitError retrofitErrorNotFound = mock(RetrofitError.class);
    Response notFoundResponse = new Response("someUri", 404, "whynot", Collections.emptyList(), null);
    when(retrofitErrorNotFound.getResponse()).thenReturn(notFoundResponse);

    when(serviceInstanceService.createServiceInstance(any())).thenThrow(retrofitErrorNotFound);

    assertThrows(CloudFoundryApiException.class, () ->
      serviceInstances.createServiceInstance("newServiceInstanceName",
        "serviceName",
        "ServicePlan1",
        Collections.emptySet(),
        null,
        cloudFoundrySpace,
        Duration.ofMillis(40))
    );

    verify(serviceInstanceService, times(1)).createServiceInstance(any());
    verify(serviceInstanceService, never()).getServiceInstanceById("service-instance-guid");
    verify(serviceInstanceService, never()).updateServiceInstance(any(), any());
  }

  @Test
  void throwExceptionWhenNoServicePlanExistsWithTheNameProvided() {
    Page<ServicePlan> servicePlansPageOne = new Page<>();
    servicePlansPageOne.setTotalResults(0);
    servicePlansPageOne.setTotalPages(1);
    servicePlansPageOne.setResources(Collections.emptyList());
    when(serviceInstanceService.findServicePlans(any(), anyListOf(String.class))).thenReturn(servicePlansPageOne);

    assertThrows(ResourceNotFoundException.class, () ->
      serviceInstances.createServiceInstance("newServiceInstanceName",
        "serviceName",
        "servicePlanName",
        Collections.emptySet(),
        null,
        cloudFoundrySpace,
        Duration.ofMillis(40))
    );
  }

  @Test
  void shouldUpdateTheServiceIfAlreadyExists() {
    Resource<ServiceInstance> polledServiceInstanceResource = createServiceInstanceResource();
    polledServiceInstanceResource.getEntity().setLastOperation(new LastOperation().setType(UPDATE).setState(SUCCEEDED));

    RetrofitError retrofitError = mock(RetrofitError.class);
    when(retrofitError.getBodyAs(any())).thenReturn(new ErrorDescription().setCode(SERVICE_ALREADY_EXISTS));

    when(serviceInstanceService.createServiceInstance(any())).thenThrow(retrofitError);
    when(serviceInstanceService.all(any(), any(), anyListOf(String.class))).thenReturn(createServiceInstancePage());
    when(serviceInstanceService.updateServiceInstance(any(), any())).thenReturn(createServiceInstanceResource());
    when(serviceInstanceService.getServiceInstanceById(any())).thenReturn(polledServiceInstanceResource);

    serviceInstances.createServiceInstance("newServiceInstanceName",
      "serviceName",
      "ServicePlan1",
      Collections.emptySet(),
      null,
      cloudFoundrySpace,
      Duration.ofMillis(40));

    verify(serviceInstanceService, times(1)).createServiceInstance(any());
    verify(serviceInstanceService, times(1)).updateServiceInstance(any(), any());
    verify(serviceInstanceService, atLeastOnce()).getServiceInstanceById("service-instance-guid");
  }

  @Test
  void shouldThrowExceptionIfServiceExistsAndNeedsChangingButUpdateFails() {
    RetrofitError retrofitError = mock(RetrofitError.class);
    when(retrofitError.getBodyAs(any())).thenReturn(new ErrorDescription().setCode(SERVICE_ALREADY_EXISTS));

    RetrofitError updateError = mock(RetrofitError.class);
    when(updateError.getResponse()).thenReturn(new Response("url", 418, "reason", Collections.emptyList(), null));
    when(updateError.getBodyAs(any())).thenReturn(new ErrorDescription());

    when(serviceInstanceService.createServiceInstance(any())).thenThrow(retrofitError);
    when(serviceInstanceService.all(any(), any(), anyListOf(String.class))).thenReturn(createServiceInstancePage());
    when(serviceInstanceService.updateServiceInstance(any(), any())).thenThrow(updateError);

    assertThrows(CloudFoundryApiException.class, () ->
      serviceInstances.createServiceInstance("newServiceInstanceName",
        "serviceName",
        "ServicePlan1",
        Collections.emptySet(),
        null,
        cloudFoundrySpace,
        Duration.ofMillis(40))
    );

    verify(serviceInstanceService, times(1)).createServiceInstance(any());
    verify(serviceInstanceService, times(1)).updateServiceInstance(any(), any());
  }

  @Test
  void shouldThrowCloudFoundryApiErrorWhenMoreThanOneServiceInstanceWithTheSameNameExists() {
    ServiceInstance serviceInstance = new ServiceInstance();
    serviceInstance.setServicePlanGuid("plan-guid").setName("newServiceInstanceName");
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
    errorDescription.setCode(SERVICE_ALREADY_EXISTS);
    RetrofitError retrofitError = mock(RetrofitError.class);
    when(retrofitError.getBodyAs(any())).thenReturn(errorDescription);

    when(serviceInstanceService.createServiceInstance(any())).thenThrow(retrofitError);
    when(serviceInstanceService.all(any(), any(), anyListOf(String.class))).thenReturn(serviceInstancePage);

    assertThrows(CloudFoundryApiException.class, () ->
      serviceInstances.createServiceInstance("newServiceInstanceName",
        "serviceName",
        "ServicePlan1",
        Collections.emptySet(),
        null,
        cloudFoundrySpace,
        Duration.ofMillis(40))
    );
  }

  @Test
  void shouldSuccessfullyCreateUserProvidedService() {
    when(serviceInstanceService.createUserProvidedServiceInstance(any())).thenReturn(createUserProvidedServiceInstanceResource());

    serviceInstances.createUserProvidedServiceInstance(
      "new-up-service-instance-name",
      "syslogDrainUrl",
      Collections.emptySet(),
      Collections.emptyMap(),
      "routeServiceUrl",
      cloudFoundrySpace
    );

    verify(serviceInstanceService, times(1)).createUserProvidedServiceInstance(any());
    verify(serviceInstanceService, never()).updateUserProvidedServiceInstance(any(), any());
  }

  @Test
  void shouldUpdateTheUpdateServiceInstanceIfAlreadyExists() {
    RetrofitError retrofitError = mock(RetrofitError.class);
    when(retrofitError.getBodyAs(any())).thenReturn(new ErrorDescription().setCode(SERVICE_ALREADY_EXISTS));

    when(serviceInstanceService.createUserProvidedServiceInstance(any())).thenThrow(retrofitError);
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createUserProvidedServiceInstancePage());
    when(serviceInstanceService.updateUserProvidedServiceInstance(any(), any())).thenReturn(createUserProvidedServiceInstanceResource());

    serviceInstances.createUserProvidedServiceInstance(
      "new-up-service-instance-name",
      "syslogDrainUrl",
      Collections.emptySet(),
      Collections.emptyMap(),
      "routeServiceUrl",
      cloudFoundrySpace
    );

    verify(serviceInstanceService, times(1)).createUserProvidedServiceInstance(any());
    verify(serviceInstanceService, times(1)).updateUserProvidedServiceInstance(any(), any());
  }

  @Test
  void getServiceInstanceShouldReturnAServiceInstanceWhenExactlyOneIsReturnedFromApi() {
    when(serviceInstanceService.all(any(), any(), any())).thenReturn(createServiceInstancePage());

    Resource<ServiceInstance> service = serviceInstances.getServiceInstance(cloudFoundrySpace, "newServiceInstanceName");

    assertThat(service.getEntity()).isNotNull();
  }

  @Test
  void getServiceInstanceShouldThrowAnExceptionWhenMultipleServicesAreReturnedFromApi() {
    Page<ServiceInstance> page = new Page<>();
    page.setTotalResults(0);
    page.setTotalPages(1);
    page.setResources(Collections.emptyList());
    when(serviceInstanceService.all(any(), any(), any())).thenReturn(page);

    try {
      serviceInstances.getServiceInstance(cloudFoundrySpace, "newServiceInstanceName");
    } catch (CloudFoundryApiException cfe) {
      assertThat(cfe.getMessage()).contains("No service instances with name 'newServiceInstanceName' found in space some-space");
    } catch (Throwable t) {
      fail("Expected CloudFoundryApiException; got " + t);
    }
  }

  @Test
  void getServiceInstanceShouldThrowAnExceptionWhenNoServicesAreReturnedFromApi() {
    Page<ServiceInstance> page = new Page<>();
    page.setTotalResults(2);
    page.setTotalPages(1);
    page.setResources(Arrays.asList(createServiceInstanceResource(), createServiceInstanceResource()));
    when(serviceInstanceService.all(any(), any(), any())).thenReturn(page);

    try {
      serviceInstances.getServiceInstance(cloudFoundrySpace, "newServiceInstanceName");
    } catch (CloudFoundryApiException cfe) {
      assertThat(cfe.getMessage()).contains("2 service instances found with name 'newServiceInstanceName' in space some-space, but expected only 1");
    } catch (Throwable t) {
      fail("Expected CloudFoundryApiException; got " + t);
    }
  }

  @Test
  void getServiceInstanceShouldThrowExceptionWhenServiceNameIsBlank() {
    try {
      serviceInstances.getServiceInstance(cloudFoundrySpace, " ");
    } catch (CloudFoundryApiException cfe) {
      assertThat(cfe.getMessage()).contains("Please specify a name for the service being sought");
    } catch (Throwable t) {
      fail("Expected CloudFoundryApiException; got " + t);
    }
  }

  @Test
  void destroyServiceInstanceShouldSucceedWhenNoServiceBindingsExist() {
    Resource<ServiceInstance> pollingServiceInstanceResource = createServiceInstanceResource();
    pollingServiceInstanceResource.getEntity().setLastOperation(new LastOperation().setType(DELETE).setState(IN_PROGRESS));

    RetrofitError retrofitErrorNotFound = mock(RetrofitError.class);
    Response notFoundResponse = new Response("someUri", 404, "whynot", Collections.emptyList(), null);
    when(retrofitErrorNotFound.getResponse()).thenReturn(notFoundResponse);

    when(serviceInstanceService.all(any(), any(), anyListOf(String.class))).thenReturn(createServiceInstancePage());
    when(serviceInstanceService.getBindingsForServiceInstance("service-instance-guid", null, null)).thenReturn(new Page<>());
    when(serviceInstanceService.destroyServiceInstance(any())).thenReturn(new Response("url", 202, "reason", Collections.emptyList(), null));
    when(serviceInstanceService.getServiceInstanceById(any())).thenReturn(pollingServiceInstanceResource).thenThrow(retrofitErrorNotFound);

    serviceInstances.destroyServiceInstance(cloudFoundrySpace, "newServiceInstanceName", Duration.ofMillis(40));

    verify(serviceInstanceService, times(1)).all(any(), any(), anyListOf(String.class));
    verify(serviceInstanceService, times(1)).destroyServiceInstance(any());
    verify(serviceInstanceService, times(2)).getServiceInstanceById("service-instance-guid");
    verify(serviceInstanceService, never()).allUserProvided(any(), any());
  }

  @Test
  void destroyServiceInstanceShouldThrowExceptionWhenDeleteServiceInstanceFails() {
    Page<ServiceBinding> serviceBindingPage = new Page<>();
    serviceBindingPage.setTotalResults(0);
    serviceBindingPage.setTotalPages(1);

    RetrofitError destroyFailed = mock(RetrofitError.class);
    Response notFoundResponse = new Response("someUri", 418, "I'm a teapot", Collections.emptyList(), null);
    when(destroyFailed.getResponse()).thenReturn(notFoundResponse);
    ErrorDescription errorDescription = new ErrorDescription();
    errorDescription.setCode(ErrorDescription.Code.RESOURCE_NOT_FOUND);
    when(destroyFailed.getBodyAs(any())).thenReturn(errorDescription);

    when(serviceInstanceService.all(anyInt(), any(), any())).thenReturn(createServiceInstancePage());
    when(serviceInstanceService.getBindingsForServiceInstance(anyString(), anyInt(), any())).thenReturn(serviceBindingPage);
    when(serviceInstanceService.destroyServiceInstance(any())).thenThrow(destroyFailed);

    try {
      serviceInstances.destroyServiceInstance(cloudFoundrySpace, "serviceInstanceName", Duration.ofMillis(40));
      fail("Expected CloudFoundryApiException");
    } catch (CloudFoundryApiException cfe) {
      // expected behavior
    } catch (Throwable t) {
      fail("Expected CloudFoundryApiException; got " + t);
    }

    verify(serviceInstanceService, times(1)).destroyServiceInstance(any());
    verify(serviceInstanceService, never()).getServiceInstanceById(any());
    verify(serviceInstanceService, never()).allUserProvided(any(), any());
  }

  @Test
  void destroyServiceInstanceShouldFailIfServiceBindingsExists() {
    when(serviceInstanceService.all(any(), any(), anyListOf(String.class))).thenReturn(createServiceInstancePage());
    when(serviceInstanceService.getBindingsForServiceInstance("service-instance-guid", null, null))
      .thenReturn(Page.singleton(new ServiceBinding(), "service-binding-guid"));

    try {
      serviceInstances.destroyServiceInstance(cloudFoundrySpace, "serviceInstanceName", Duration.ofMillis(40));
      fail("Expected CloudFoundryApiException");
    } catch (CloudFoundryApiException cfe) {
      // expected behavior
    } catch (Throwable t) {
      fail("Expected CloudFoundryApiException; got " + t);
    }

    verify(serviceInstanceService, never()).destroyServiceInstance(any());
    verify(serviceInstanceService, never()).getServiceInstanceById(any());
    verify(serviceInstanceService, never()).allUserProvided(any(), any());
  }

  @Test
  void destroyUserProvidedServiceInstanceShouldSucceedWhenNoServiceBindingsExist() {
    Resource<ServiceInstance> pollingServiceInstanceResource = createServiceInstanceResource();
    pollingServiceInstanceResource.getEntity().setLastOperation(new LastOperation().setType(DELETE).setState(IN_PROGRESS));

    RetrofitError retrofitErrorNotFound = mock(RetrofitError.class);
    Response notFoundResponse = new Response("someUri", 404, "whynot", Collections.emptyList(), null);
    when(retrofitErrorNotFound.getResponse()).thenReturn(notFoundResponse);

    when(serviceInstanceService.all(any(), any(), anyListOf(String.class))).thenReturn(new Page<>());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createUserProvidedServiceInstancePage());
    when(serviceInstanceService.getBindingsForUserProvidedServiceInstance("up-service-instance-guid", null, null)).thenReturn(new Page<>());
    when(serviceInstanceService.destroyUserProvidedServiceInstance(any())).thenReturn(new Response("url", 204, "reason", Collections.emptyList(), null));

    serviceInstances.destroyServiceInstance(cloudFoundrySpace, "newServiceInstanceName", Duration.ofMillis(40));

    verify(serviceInstanceService, times(1)).all(any(), any(), anyListOf(String.class));
    verify(serviceInstanceService, times(1)).allUserProvided(any(), any());
    verify(serviceInstanceService, times(1)).destroyUserProvidedServiceInstance(any());
    verify(serviceInstanceService, times(1)).getBindingsForUserProvidedServiceInstance(any(), anyInt(), anyListOf(String.class));
    verify(serviceInstanceService, never()).destroyServiceInstance(any());
  }

  @Test
  void destroyUserProvidedServiceInstanceShouldThrowExceptionWhenDeleteServiceInstanceFails() {
    Page<ServiceBinding> serviceBindingPage = new Page<>();
    serviceBindingPage.setTotalResults(0);
    serviceBindingPage.setTotalPages(1);

    RetrofitError destroyFailed = mock(RetrofitError.class);
    Response notFoundResponse = new Response("someUri", 418, "I'm a teapot", Collections.emptyList(), null);
    when(destroyFailed.getResponse()).thenReturn(notFoundResponse);
    ErrorDescription errorDescription = new ErrorDescription();
    errorDescription.setCode(ErrorDescription.Code.RESOURCE_NOT_FOUND);
    when(destroyFailed.getBodyAs(any())).thenReturn(errorDescription);

    when(serviceInstanceService.all(any(), any(), anyListOf(String.class))).thenReturn(new Page<>());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createUserProvidedServiceInstancePage());
    when(serviceInstanceService.getBindingsForUserProvidedServiceInstance(anyString(), anyInt(), any())).thenReturn(serviceBindingPage);
    when(serviceInstanceService.destroyUserProvidedServiceInstance(any())).thenThrow(destroyFailed);

    try {
      serviceInstances.destroyServiceInstance(cloudFoundrySpace, "serviceInstanceName", Duration.ofMillis(40));
      fail("Expected CloudFoundryApiException");
    } catch (CloudFoundryApiException cfe) {
      // expected behavior
    } catch (Throwable t) {
      fail("Expected CloudFoundryApiException; got " + t);
    }

    verify(serviceInstanceService, times(1)).all(any(), any(), anyListOf(String.class));
    verify(serviceInstanceService, times(1)).allUserProvided(any(), any());
    verify(serviceInstanceService, times(1)).getBindingsForUserProvidedServiceInstance(any(), anyInt(), anyListOf(String.class));
    verify(serviceInstanceService, times(1)).destroyUserProvidedServiceInstance(any());
    verify(serviceInstanceService, never()).destroyServiceInstance(any());
  }

  @Test
  void destroyUserProvidedServiceInstanceShouldFailIfServiceBindingsExists() {
    when(serviceInstanceService.all(any(), any(), anyListOf(String.class))).thenReturn(new Page<>());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createUserProvidedServiceInstancePage());
    when(serviceInstanceService.getBindingsForUserProvidedServiceInstance("up-service-instance-guid", null, null))
      .thenReturn(Page.singleton(new ServiceBinding(), "up-service-instance-guid"));

    try {
      serviceInstances.destroyServiceInstance(cloudFoundrySpace, "serviceInstanceName", Duration.ofMillis(40));
      fail("Expected CloudFoundryApiException");
    } catch (CloudFoundryApiException cfe) {
      // expected behavior
    } catch (Throwable t) {
      fail("Expected CloudFoundryApiException; got " + t);
    }

    verify(serviceInstanceService, times(1)).all(any(), any(), anyListOf(String.class));
    verify(serviceInstanceService, times(1)).allUserProvided(any(), any());
    verify(serviceInstanceService, never()).destroyUserProvidedServiceInstance(any());
    verify(serviceInstanceService, never()).destroyServiceInstance(any());
  }

  @Test
  void pollServiceInstanceStatusShouldSucceedWhenTheOperationFinishesBeforeTimeoutIsExceeded() {
    Resource<ServiceInstance> polledServiceInstanceResource = createServiceInstanceResource();
    polledServiceInstanceResource.getEntity().setLastOperation(new LastOperation().setType(CREATE).setState(IN_PROGRESS));
    Resource<ServiceInstance> succeededServiceInstanceResource = createServiceInstanceResource();
    succeededServiceInstanceResource.getEntity()
      .setLastOperation(new LastOperation().setType(LastOperation.Type.CREATE).setState(SUCCEEDED));

    when(serviceInstanceService.getServiceInstanceById(any()))
      .thenReturn(polledServiceInstanceResource, polledServiceInstanceResource, succeededServiceInstanceResource);

    serviceInstances.pollServiceInstanceStatus("new-service-instance-name", "service-instance-guid", DELETE, Duration.ofMillis(40));

    verify(serviceInstanceService, times(3)).getServiceInstanceById("service-instance-guid");
  }

  @Test
  void pollServiceInstanceStatusShouldThrowExceptionWhenTimeoutIsExceeded() {
    Resource<ServiceInstance> polledServiceInstanceResource = createServiceInstanceResource();
    polledServiceInstanceResource.getEntity().setLastOperation(new LastOperation().setType(DELETE).setState(IN_PROGRESS));

    when(serviceInstanceService.createServiceInstance(any())).thenReturn(createServiceInstanceResource());
    when(serviceInstanceService.getServiceInstanceById(any())).thenReturn(polledServiceInstanceResource);

    try {
      serviceInstances.pollServiceInstanceStatus("service-instance-name", "service-instance-guid", DELETE, Duration.ofMillis(40));
      fail("Expected CloudFoundryApiException");
    } catch (CloudFoundryApiException cfe) {
      assertThat(cfe.getMessage()).contains("Service instance 'service-instance-name' DELETE did not complete");
    } catch (Throwable t) {
      fail("Expected CloudFoundryApiException; got " + t);
    }

    verify(serviceInstanceService, times(4)).getServiceInstanceById("service-instance-guid");
  }

  @Test
  void pollServiceInstanceStatusShouldThrowExceptionWhenOperationFails() {
    Resource<ServiceInstance> polledServiceInstanceResource = createServiceInstanceResource();
    polledServiceInstanceResource.getEntity().setLastOperation(new LastOperation().setType(UPDATE).setState(IN_PROGRESS));
    Resource<ServiceInstance> failedServiceInstanceResource = createServiceInstanceResource();
    failedServiceInstanceResource.getEntity().setLastOperation(new LastOperation().setType(UPDATE).setState(FAILED));

    when(serviceInstanceService.createServiceInstance(any())).thenReturn(createServiceInstanceResource());
    when(serviceInstanceService.getServiceInstanceById(any())).thenReturn(polledServiceInstanceResource, polledServiceInstanceResource, failedServiceInstanceResource);

    try {
      serviceInstances.pollServiceInstanceStatus("service-instance-name", "service-instance-guid", UPDATE, Duration.ofMillis(40));
      fail("Expected CloudFoundryApiException");
    } catch (CloudFoundryApiException cfe) {
      assertThat(cfe.getMessage()).contains("Service instance 'service-instance-name' UPDATE failed");
    } catch (Throwable t) {
      fail("Expected CloudFoundryApiException; got " + t);
    }

    verify(serviceInstanceService, times(3)).getServiceInstanceById("service-instance-guid");
  }

  private Resource<ServiceInstance> createServiceInstanceResource() {
    ServiceInstance serviceInstance = new ServiceInstance();
    serviceInstance.setServicePlanGuid("plan-guid").setName("newServiceInstanceName");
    Resource<ServiceInstance> serviceInstanceResource = new Resource<>();
    serviceInstanceResource.setMetadata(new Resource.Metadata().setGuid("service-instance-guid"));
    serviceInstanceResource.setEntity(serviceInstance);
    return serviceInstanceResource;
  }

  private Resource<UserProvidedServiceInstance> createUserProvidedServiceInstanceResource() {
    UserProvidedServiceInstance userProvidedServiceInstance = new UserProvidedServiceInstance();
    userProvidedServiceInstance.setName("newServiceInstanceName");
    Resource<UserProvidedServiceInstance> userProvidedServiceInstanceResource = new Resource<>();
    userProvidedServiceInstanceResource.setMetadata(new Resource.Metadata().setGuid("up-service-instance-guid"));
    userProvidedServiceInstanceResource.setEntity(userProvidedServiceInstance);
    return userProvidedServiceInstanceResource;
  }

  private Page<ServiceInstance> createServiceInstancePage() {
    ServiceInstance serviceInstance = new ServiceInstance();
    serviceInstance.setServicePlanGuid("plan-guid").setName("newServiceInstanceName");
    return Page.singleton(serviceInstance, "service-instance-guid");
  }

  private Page<UserProvidedServiceInstance> createUserProvidedServiceInstancePage() {
    UserProvidedServiceInstance serviceInstance = new UserProvidedServiceInstance();
    serviceInstance.setName("up-service-name");
    return Page.singleton(serviceInstance, "up-service-instance-guid");
  }
}
