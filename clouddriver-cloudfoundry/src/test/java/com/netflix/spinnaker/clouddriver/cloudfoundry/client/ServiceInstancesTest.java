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
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceInstanceResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import org.junit.jupiter.api.Test;
import retrofit.RetrofitError;
import retrofit.client.Response;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Supplier;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.IN_PROGRESS;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.SUCCEEDED;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.CREATE;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.DELETE;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.UPDATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ServiceServiceInstancesTest {
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
  private ServiceInstances serviceInstances = new ServiceInstances(serviceInstanceService, orgs, spaces);

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
    verify(serviceInstanceService, never()).all(any(), any());
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
    when(serviceInstanceService.all(eq(null), any())).thenReturn(serviceMappingPageOne);
    when(serviceInstanceService.all(eq(1), any())).thenReturn(serviceMappingPageOne);

    Page<UserProvidedServiceInstance> userProvidedServiceMappingPageOne = createEmptyUserProvidedServiceInstancePage();
    when(serviceInstanceService.allUserProvided(eq(null), any())).thenReturn(userProvidedServiceMappingPageOne);
    when(serviceInstanceService.allUserProvided(eq(1), any())).thenReturn(userProvidedServiceMappingPageOne);

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

    Page<ServiceInstance> serviceMappingPageOne = createEmptyServiceInstancePage();
    when(serviceInstanceService.all(eq(null), any())).thenReturn(serviceMappingPageOne);
    when(serviceInstanceService.all(eq(1), any())).thenReturn(serviceMappingPageOne);

    Page<UserProvidedServiceInstance> userProvidedServiceMappingPageOne = Page.singleton(null, "service-instance-guid");
    userProvidedServiceMappingPageOne.setTotalResults(0);
    userProvidedServiceMappingPageOne.setTotalPages(0);
    when(serviceInstanceService.allUserProvided(eq(null), any())).thenReturn(userProvidedServiceMappingPageOne);
    when(serviceInstanceService.allUserProvided(eq(1), any())).thenReturn(userProvidedServiceMappingPageOne);

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

    when(serviceInstanceService.all(any(), any())).thenReturn(createEmptyServiceInstancePage());

    Page<UserProvidedServiceInstance> userProvidedServiceMappingPageOne = createEmptyUserProvidedServiceInstancePage();
    when(serviceInstanceService.allUserProvided(eq(null), any())).thenReturn(userProvidedServiceMappingPageOne);
    when(serviceInstanceService.allUserProvided(eq(1), any())).thenReturn(userProvidedServiceMappingPageOne);
    assertThrows(
      () -> serviceInstances.createServiceBindingsByName(
        cloudFoundryServerGroup,
        Collections.singletonList("service-instance")),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): Number of service instances does not match the number of service names");
  }

  @Test
  void shouldSuccessfullyCreateService() {
    Resource<ServiceInstance> succeededServiceInstanceResource = createServiceInstanceResource();
    succeededServiceInstanceResource.getEntity().setLastOperation(new LastOperation().setType(CREATE).setState(SUCCEEDED));

    when(serviceInstanceService.all(any(), any())).thenReturn(createEmptyServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createEmptyUserProvidedServiceInstancePage());
    when(serviceInstanceService.createServiceInstance(any())).thenReturn(createServiceInstanceResource());

    ServiceInstanceResponse response = serviceInstances.createServiceInstance("new-service-instance-name",
      "serviceName",
      "ServicePlan1",
      Collections.emptySet(),
      null,
      cloudFoundrySpace);

    assertThat(response).isEqualTo(new ServiceInstanceResponse()
      .setServiceInstanceId("service-instance-guid")
      .setServiceInstanceName("new-service-instance-name")
      .setType(CREATE)
      .setState(IN_PROGRESS)
    );
    verify(serviceInstanceService, times(1)).createServiceInstance(any());
    verify(serviceInstanceService, never()).updateServiceInstance(any(), any());
  }

  @Test
  void shouldThrowExceptionWhenCreationReturnsHttpNotFound() {
    RetrofitError retrofitErrorNotFound = mock(RetrofitError.class);
    Response notFoundResponse = new Response("someUri", 404, "whynot", Collections.emptyList(), null);
    when(retrofitErrorNotFound.getResponse()).thenReturn(notFoundResponse);

    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(createEmptyServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createEmptyUserProvidedServiceInstancePage());
    when(serviceInstanceService.createServiceInstance(any())).thenThrow(retrofitErrorNotFound);

    assertThrows(
      () -> serviceInstances.createServiceInstance("newServiceInstanceName",
        "serviceName",
        "ServicePlan1",
        Collections.emptySet(),
        null,
        cloudFoundrySpace),
      CloudFoundryApiException.class, "Cloud Foundry API returned with error(s): service instance 'newServiceInstanceName' could not be created");
    verify(serviceInstanceService, times(1)).createServiceInstance(any());
    verify(serviceInstanceService, never()).updateServiceInstance(any(), any());
  }

  @Test
  void throwExceptionWhenNoServicePlanExistsWithTheNameProvided() {
    Page<ServicePlan> servicePlansPageOne = new Page<>();
    servicePlansPageOne.setTotalResults(0);
    servicePlansPageOne.setTotalPages(1);
    servicePlansPageOne.setResources(Collections.emptyList());
    when(serviceInstanceService.findServicePlans(any(), anyListOf(String.class))).thenReturn(servicePlansPageOne);

    assertThrows(
      () -> serviceInstances.createServiceInstance("newServiceInstanceName",
        "serviceName",
        "servicePlanName",
        Collections.emptySet(),
        null,
        cloudFoundrySpace),
      ResourceNotFoundException.class, "No plans available for service name 'serviceName'");
  }

  @Test
  void shouldUpdateTheServiceIfAlreadyExists() {
    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(createServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createEmptyUserProvidedServiceInstancePage());
    when(serviceInstanceService.updateServiceInstance(any(), any())).thenReturn(createServiceInstanceResource());

    ServiceInstanceResponse response = serviceInstances.createServiceInstance("new-service-instance-name",
      "serviceName",
      "ServicePlan1",
      Collections.emptySet(),
      null,
      cloudFoundrySpace);

    assertThat(response).isEqualTo(new ServiceInstanceResponse()
      .setServiceInstanceId("service-instance-guid")
      .setServiceInstanceName("new-service-instance-name")
      .setType(UPDATE)
      .setState(IN_PROGRESS)
    );
    verify(serviceInstanceService, times(0)).createServiceInstance(any());
    verify(serviceInstanceService, times(1)).updateServiceInstance(any(), any());
  }

  @Test
  void shouldUpdateTheServiceIfAlreadyExistsAndVersionTagsDiffer() {
    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(createServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createEmptyUserProvidedServiceInstancePage());
    when(serviceInstanceService.updateServiceInstance(any(), any())).thenReturn(createServiceInstanceResource());

    ServiceInstanceResponse response = serviceInstances.createServiceInstance(
      "new-service-instance-name",
      "serviceName",
      "ServicePlan1",
      Collections.singleton("spinnakerVersion-v002"),
      null,
      cloudFoundrySpace);

    assertThat(response).isEqualTo(new ServiceInstanceResponse()
      .setServiceInstanceId("service-instance-guid")
      .setServiceInstanceName("new-service-instance-name")
      .setType(UPDATE)
      .setState(IN_PROGRESS)
    );
    verify(serviceInstanceService, times(0)).createServiceInstance(any());
    verify(serviceInstanceService, times(1)).updateServiceInstance(any(), any());
  }

  @Test
  void shouldThrowExceptionIfServiceExistsAndNeedsChangingButUpdateFails() {
    RetrofitError updateError = mock(RetrofitError.class);
    when(updateError.getResponse()).thenReturn(new Response("url", 418, "reason", Collections.emptyList(), null));
    when(updateError.getBodyAs(any())).thenReturn(new ErrorDescription());

    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(createServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createEmptyUserProvidedServiceInstancePage());
    when(serviceInstanceService.updateServiceInstance(any(), any())).thenThrow(updateError);

    assertThrows(
      () -> serviceInstances.createServiceInstance("newServiceInstanceName",
        "serviceName",
        "ServicePlan1",
        Collections.emptySet(),
        null,
        cloudFoundrySpace),
      CloudFoundryApiException.class, "Cloud Foundry API returned with error(s): ");

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

    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(serviceInstancePage);
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createEmptyUserProvidedServiceInstancePage());

    assertThrows(
      () -> serviceInstances.createServiceInstance("newServiceInstanceName",
        "serviceName",
        "ServicePlan1",
        Collections.emptySet(),
        null,
        cloudFoundrySpace),
      CloudFoundryApiException.class, "Cloud Foundry API returned with error(s): 2 service instances found with name 'newServiceInstanceName' in space some-space, but expected only 1");
  }

  @Test
  void shouldSuccessfullyCreateUserProvidedService() {
    when(serviceInstanceService.all(any(), any())).thenReturn(createEmptyServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createEmptyUserProvidedServiceInstancePage());
    when(serviceInstanceService.createUserProvidedServiceInstance(any())).thenReturn(createUserProvidedServiceInstanceResource());

    ServiceInstanceResponse response = serviceInstances.createUserProvidedServiceInstance(
      "new-up-service-instance-name",
      "syslogDrainUrl",
      Collections.emptySet(),
      Collections.emptyMap(),
      "routeServiceUrl",
      cloudFoundrySpace
    );

    assertThat(response).isEqualTo(new ServiceInstanceResponse()
      .setServiceInstanceId("up-service-instance-guid")
      .setServiceInstanceName("new-up-service-instance-name")
      .setType(CREATE)
      .setState(SUCCEEDED)
    );
    verify(serviceInstanceService, times(1)).createUserProvidedServiceInstance(any());
    verify(serviceInstanceService, never()).updateUserProvidedServiceInstance(any(), any());
  }

  @Test
  void shouldUpdateUserProvidedServiceInstanceIfAlreadyExists() {
    when(serviceInstanceService.all(any(), any())).thenReturn(createEmptyServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createUserProvidedServiceInstancePage());
    when(serviceInstanceService.updateUserProvidedServiceInstance(any(), any())).thenReturn(createUserProvidedServiceInstanceResource());

    ServiceInstanceResponse response = serviceInstances.createUserProvidedServiceInstance(
      "new-up-service-instance-name",
      "syslogDrainUrl",
      Collections.emptySet(),
      Collections.emptyMap(),
      "routeServiceUrl",
      cloudFoundrySpace
    );

    assertThat(response).isEqualTo(new ServiceInstanceResponse()
      .setServiceInstanceId("up-service-instance-guid")
      .setServiceInstanceName("new-up-service-instance-name")
      .setType(UPDATE)
      .setState(SUCCEEDED)
    );
    verify(serviceInstanceService, times(0)).createUserProvidedServiceInstance(any());
    verify(serviceInstanceService, times(1)).updateUserProvidedServiceInstance(any(), any());
  }

  @Test
  void shouldUpdateUserProvidedServiceInstanceIfVersionTagsDiffer() {
    when(serviceInstanceService.all(any(), any())).thenReturn(createEmptyServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createUserProvidedServiceInstancePage());
    when(serviceInstanceService.updateUserProvidedServiceInstance(any(), any())).thenReturn(createUserProvidedServiceInstanceResource());

    ServiceInstanceResponse response = serviceInstances.createUserProvidedServiceInstance(
      "new-up-service-instance-name",
      "syslogDrainUrl",
      Collections.singleton("spinnakerVersion-v001"),
      Collections.emptyMap(),
      "routeServiceUrl",
      cloudFoundrySpace
    );

    assertThat(response).isEqualTo(new ServiceInstanceResponse()
      .setServiceInstanceId("up-service-instance-guid")
      .setServiceInstanceName("new-up-service-instance-name")
      .setType(UPDATE)
      .setState(SUCCEEDED)
    );
    verify(serviceInstanceService, times(0)).createUserProvidedServiceInstance(any());
    verify(serviceInstanceService, times(1)).updateUserProvidedServiceInstance(any(), any());
  }

  @Test
  void getServiceInstanceShouldReturnAServiceInstanceWhenExactlyOneIsReturnedFromApi() {
    when(serviceInstanceService.all(any(), any())).thenReturn(createServiceInstancePage());

    Resource<ServiceInstance> service = serviceInstances.getServiceInstance(cloudFoundrySpace, "newServiceInstanceName");

    assertThat(service).isNotNull();
    assertThat(service.getEntity()).isNotNull();
  }

  @Test
  void getServiceInstanceShouldThrowAnExceptionWhenMultipleServicesAreReturnedFromApi() {
    Page<ServiceInstance> page = new Page<>();
    page.setTotalResults(0);
    page.setTotalPages(1);
    page.setResources(Collections.emptyList());
    when(serviceInstanceService.all(any(), any())).thenReturn(page);

    assertThat(serviceInstances.getServiceInstance(cloudFoundrySpace, "newServiceInstanceName")).isNull();
  }

  @Test
  void getServiceInstanceShouldThrowAnExceptionWhenNoServicesAreReturnedFromApi() {
    Page<ServiceInstance> page = new Page<>();
    page.setTotalResults(2);
    page.setTotalPages(1);
    page.setResources(Arrays.asList(createServiceInstanceResource(), createServiceInstanceResource()));
    when(serviceInstanceService.all(any(), any())).thenReturn(page);

    assertThrows(() -> serviceInstances.getServiceInstance(cloudFoundrySpace, "newServiceInstanceName"),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): 2 service instances found with name 'newServiceInstanceName' in space some-space, but expected only 1");
  }

  @Test
  void getServiceInstanceShouldThrowExceptionWhenServiceNameIsBlank() {
    assertThrows(() -> serviceInstances.getServiceInstance(cloudFoundrySpace, ""),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): Please specify a name for the service being sought");
  }

  @Test
  void destroyServiceInstanceShouldSucceedWhenNoServiceBindingsExist() {
    RetrofitError retrofitErrorNotFound = mock(RetrofitError.class);
    Response notFoundResponse = new Response("someUri", 404, "whynot", Collections.emptyList(), null);
    when(retrofitErrorNotFound.getResponse()).thenReturn(notFoundResponse);

    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(createServiceInstancePage());
    when(serviceInstanceService.getBindingsForServiceInstance("service-instance-guid", null, null)).thenReturn(new Page<>());
    when(serviceInstanceService.destroyServiceInstance(any())).thenReturn(new Response("url", 202, "reason", Collections.emptyList(), null));

    ServiceInstanceResponse response = serviceInstances
      .destroyServiceInstance(cloudFoundrySpace, "new-service-instance-name");

    assertThat(response).isEqualTo(new ServiceInstanceResponse()
      .setServiceInstanceId("service-instance-guid")
      .setServiceInstanceName("new-service-instance-name")
      .setType(DELETE)
      .setState(IN_PROGRESS)
    );
    verify(serviceInstanceService, times(1)).all(any(), anyListOf(String.class));
    verify(serviceInstanceService, times(1)).destroyServiceInstance(any());
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

    when(serviceInstanceService.all(anyInt(), any())).thenReturn(createServiceInstancePage());
    when(serviceInstanceService.getBindingsForServiceInstance(anyString(), anyInt(), any())).thenReturn(serviceBindingPage);
    when(serviceInstanceService.destroyServiceInstance(any())).thenThrow(destroyFailed);

    assertThrows(
      () -> serviceInstances.destroyServiceInstance(cloudFoundrySpace, "serviceInstanceName"),
      CloudFoundryApiException.class, "Cloud Foundry API returned with error(s): ");

    verify(serviceInstanceService, times(1)).destroyServiceInstance(any());
    verify(serviceInstanceService, never()).allUserProvided(any(), any());
  }

  @Test
  void destroyServiceInstanceShouldFailIfServiceBindingsExists() {
    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(createServiceInstancePage());
    when(serviceInstanceService.getBindingsForServiceInstance("service-instance-guid", null, null))
      .thenReturn(Page.singleton(new ServiceBinding(), "service-binding-guid"));

    assertThrows(
      () -> serviceInstances.destroyServiceInstance(cloudFoundrySpace, "serviceInstanceName"),
      CloudFoundryApiException.class, "Cloud Foundry API returned with error(s): Unable to destroy service instance while 1 service binding(s) exist");

    verify(serviceInstanceService, never()).destroyServiceInstance(any());
    verify(serviceInstanceService, never()).allUserProvided(any(), any());
  }

  @Test
  void destroyUserProvidedServiceInstanceShouldSucceedWhenNoServiceBindingsExist() {
    RetrofitError retrofitErrorNotFound = mock(RetrofitError.class);
    Response notFoundResponse = new Response("someUri", 404, "whynot", Collections.emptyList(), null);
    when(retrofitErrorNotFound.getResponse()).thenReturn(notFoundResponse);

    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(new Page<>());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createUserProvidedServiceInstancePage());
    when(serviceInstanceService.getBindingsForUserProvidedServiceInstance("up-service-instance-guid", null, null)).thenReturn(new Page<>());
    when(serviceInstanceService.destroyUserProvidedServiceInstance(any())).thenReturn(new Response("url", 204, "reason", Collections.emptyList(), null));

    ServiceInstanceResponse response = serviceInstances
      .destroyServiceInstance(cloudFoundrySpace, "newServiceInstanceName");

    assertThat(response).isEqualTo(new ServiceInstanceResponse()
      .setServiceInstanceId("up-service-instance-guid")
      .setServiceInstanceName("newServiceInstanceName")
      .setType(DELETE)
      .setState(LastOperation.State.NOT_FOUND)
    );
    verify(serviceInstanceService, times(1)).all(any(), anyListOf(String.class));
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

    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(new Page<>());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createUserProvidedServiceInstancePage());
    when(serviceInstanceService.getBindingsForUserProvidedServiceInstance(anyString(), anyInt(), any())).thenReturn(serviceBindingPage);
    when(serviceInstanceService.destroyUserProvidedServiceInstance(any())).thenThrow(destroyFailed);

    assertThrows(
      () -> serviceInstances.destroyServiceInstance(cloudFoundrySpace, "serviceInstanceName"),
      CloudFoundryApiException.class, "Cloud Foundry API returned with error(s): ");

    verify(serviceInstanceService, times(1)).all(any(), anyListOf(String.class));
    verify(serviceInstanceService, times(1)).allUserProvided(any(), any());
    verify(serviceInstanceService, times(1)).getBindingsForUserProvidedServiceInstance(any(), anyInt(), anyListOf(String.class));
    verify(serviceInstanceService, times(1)).destroyUserProvidedServiceInstance(any());
    verify(serviceInstanceService, never()).destroyServiceInstance(any());
  }

  @Test
  void destroyUserProvidedServiceInstanceShouldFailIfServiceBindingsExists() {
    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(new Page<>());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createUserProvidedServiceInstancePage());
    when(serviceInstanceService.getBindingsForUserProvidedServiceInstance("up-service-instance-guid", null, null))
      .thenReturn(Page.singleton(new ServiceBinding(), "up-service-instance-guid"));

    assertThrows(
      () -> serviceInstances.destroyServiceInstance(cloudFoundrySpace, "serviceInstanceName"),
      CloudFoundryApiException.class, "Cloud Foundry API returned with error(s): Unable to destroy service instance while 1 service binding(s) exist");

    verify(serviceInstanceService, times(1)).all(any(), anyListOf(String.class));
    verify(serviceInstanceService, times(1)).allUserProvided(any(), any());
    verify(serviceInstanceService, never()).destroyUserProvidedServiceInstance(any());
    verify(serviceInstanceService, never()).destroyServiceInstance(any());
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
    serviceInstance
      .setServicePlanGuid("plan-guid")
      .setName("newServiceInstanceName")
      .setTags(Collections.singleton("spinnakerVersion-v001"));
    return Page.singleton(serviceInstance, "service-instance-guid");
  }

  private Page<ServiceInstance> createEmptyServiceInstancePage() {
    Page<ServiceInstance> serviceInstancePage = new Page<>();
    serviceInstancePage
      .setTotalResults(0)
      .setTotalPages(1);
    return serviceInstancePage;
  }

  private Page<UserProvidedServiceInstance> createUserProvidedServiceInstancePage() {
    UserProvidedServiceInstance serviceInstance = new UserProvidedServiceInstance();
    serviceInstance
      .setName("up-service-name")
      .setTags(Collections.singleton("spinnakerVersion-v000"));
    return Page.singleton(serviceInstance, "up-service-instance-guid");
  }

  private Page<UserProvidedServiceInstance> createEmptyUserProvidedServiceInstancePage() {
    Page<UserProvidedServiceInstance> userProvidedServiceInstancePage = new Page<>();
    userProvidedServiceInstancePage
      .setTotalResults(0)
      .setTotalPages(1);
    return userProvidedServiceInstancePage;
  }

  private void assertThrows(Supplier<?> s, Class clazz, String errorString) {
    RuntimeException runtimeException = null;
    try {
      s.get();
    } catch (RuntimeException e) {
      runtimeException = e;
    }
    assertThat(runtimeException).isInstanceOf(clazz);
    assertThat(runtimeException.getMessage()).isEqualTo(errorString);
  }
}
