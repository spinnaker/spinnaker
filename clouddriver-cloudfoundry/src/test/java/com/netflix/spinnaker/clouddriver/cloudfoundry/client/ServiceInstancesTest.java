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

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ConfigFeatureFlag.ConfigFlag.SERVICE_INSTANCE_SHARING;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.IN_PROGRESS;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.SUCCEEDED;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.*;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ServiceInstance.Type.MANAGED_SERVICE_INSTANCE;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ServiceInstance.Type.USER_PROVIDED_SERVICE_INSTANCE;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.utils.TestUtils.assertThrows;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ConfigService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ServiceInstanceService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceInstanceResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateSharedServiceInstances;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import java.util.*;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import retrofit2.Response;
import retrofit2.mock.Calls;

class ServiceInstancesTest {
  private final CloudFoundryOrganization cloudFoundryOrganization =
      CloudFoundryOrganization.builder().id("some-org-guid").name("org").build();
  private final CloudFoundrySpace cloudFoundrySpace =
      CloudFoundrySpace.builder()
          .id("some-space-guid")
          .name("space")
          .organization(cloudFoundryOrganization)
          .build();
  private final ServiceInstanceService serviceInstanceService = mock(ServiceInstanceService.class);
  private final ConfigService configService = mock(ConfigService.class);
  private final Organizations organizations = mock(Organizations.class);
  private final Spaces spaces = mock(Spaces.class);
  private final ServiceInstances serviceInstances =
      new ServiceInstances(serviceInstanceService, configService, spaces);

  {
    when(serviceInstanceService.findService(any(), any()))
        .thenReturn(
            Calls.response(
                Response.success(
                    Page.singleton(new Service().setLabel("service1"), "service-guid"))));

    when(serviceInstanceService.findServicePlans(any(), any()))
        .thenReturn(
            Calls.response(
                Response.success(
                    Page.singleton(new ServicePlan().setName("ServicePlan1"), "plan-guid"))));
  }

  @Test
  void shouldCreateServiceBindingWhenServiceExists() {
    CloudFoundryServerGroup cloudFoundryServerGroup =
        CloudFoundryServerGroup.builder()
            .account("some-account")
            .id("servergroup-id")
            .space(cloudFoundrySpace)
            .build();

    Page<ServiceInstance> serviceMappingPageOne = Page.singleton(null, "service-instance-guid");
    CreateServiceBinding binding =
        new CreateServiceBinding(
            "service-instance-guid", cloudFoundryServerGroup.getId(), emptyMap());
    serviceMappingPageOne.setTotalResults(0);
    serviceMappingPageOne.setTotalPages(0);
    when(serviceInstanceService.all(eq(null), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(serviceMappingPageOne)));
    when(serviceInstanceService.all(eq(1), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(serviceMappingPageOne)));

    Page<UserProvidedServiceInstance> userProvidedServiceMappingPageOne =
        createEmptyUserProvidedServiceInstancePage();
    when(serviceInstanceService.allUserProvided(eq(null), any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(userProvidedServiceMappingPageOne)));
    when(serviceInstanceService.allUserProvided(eq(1), any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(userProvidedServiceMappingPageOne)));
    when(serviceInstanceService.createServiceBinding(binding))
        .thenAnswer(invocation -> Calls.response(Response.success(createServiceBindingResource())));

    serviceInstances.createServiceBinding(binding);
    verify(serviceInstanceService, atLeastOnce()).createServiceBinding(any());
  }

  @Test
  void shouldCreateServiceBindingWhenUserProvidedServiceExists() {
    CloudFoundryServerGroup cloudFoundryServerGroup =
        CloudFoundryServerGroup.builder()
            .account("some-account")
            .id("servergroup-id")
            .space(cloudFoundrySpace)
            .build();

    Page<ServiceInstance> serviceMappingPageOne = createEmptyOsbServiceInstancePage();
    CreateServiceBinding binding =
        new CreateServiceBinding(
            "service-instance-guid", cloudFoundryServerGroup.getId(), emptyMap());
    when(serviceInstanceService.all(eq(null), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(serviceMappingPageOne)));
    when(serviceInstanceService.all(eq(1), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(serviceMappingPageOne)));

    Page<UserProvidedServiceInstance> userProvidedServiceMappingPageOne =
        Page.singleton(null, "service-instance-guid");
    userProvidedServiceMappingPageOne.setTotalResults(0);
    userProvidedServiceMappingPageOne.setTotalPages(0);
    when(serviceInstanceService.allUserProvided(eq(null), any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(userProvidedServiceMappingPageOne)));
    when(serviceInstanceService.allUserProvided(eq(1), any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(userProvidedServiceMappingPageOne)));
    when(serviceInstanceService.createServiceBinding(binding))
        .thenAnswer(invocation -> Calls.response(Response.success(createServiceBindingResource())));

    serviceInstances.createServiceBinding(binding);

    verify(serviceInstanceService, atLeastOnce()).createServiceBinding(any());
  }

  @Test
  void shouldSucceedServiceBindingWhenServiceBindingExists() {
    CloudFoundryServerGroup cloudFoundryServerGroup =
        CloudFoundryServerGroup.builder()
            .account("some-account")
            .id("servergroup-id")
            .space(cloudFoundrySpace)
            .build();

    Page<ServiceInstance> serviceMappingPageOne = Page.singleton(null, "service-instance-guid");
    CreateServiceBinding binding =
        new CreateServiceBinding(
            "service-instance-guid", cloudFoundryServerGroup.getId(), emptyMap());
    serviceMappingPageOne.setTotalResults(0);
    serviceMappingPageOne.setTotalPages(0);
    when(serviceInstanceService.all(eq(null), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(serviceMappingPageOne)));
    when(serviceInstanceService.all(eq(1), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(serviceMappingPageOne)));

    Page<UserProvidedServiceInstance> userProvidedServiceMappingPageOne =
        createEmptyUserProvidedServiceInstancePage();
    when(serviceInstanceService.allUserProvided(eq(null), any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(userProvidedServiceMappingPageOne)));
    when(serviceInstanceService.allUserProvided(eq(1), any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(userProvidedServiceMappingPageOne)));
    when(serviceInstanceService.createServiceBinding(binding))
        .thenReturn(
            Calls.response(
                Response.error(
                    500,
                    ResponseBody.create(
                        MediaType.get("application/json"),
                        "{\"error_code\": \"CF-ServiceBindingAppServiceTaken\", \"description\":\"already bound\"}"))));

    serviceInstances.createServiceBinding(binding);
    verify(serviceInstanceService, atLeastOnce()).createServiceBinding(any());
  }

  @Test
  void shouldSuccessfullyCreateService() {
    Resource<ServiceInstance> succeededServiceInstanceResource = createServiceInstanceResource();
    succeededServiceInstanceResource
        .getEntity()
        .setLastOperation(new LastOperation().setType(CREATE).setState(SUCCEEDED));

    when(serviceInstanceService.all(any(), any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(createEmptyOsbServiceInstancePage())));
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class)))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createEmptyUserProvidedServiceInstancePage())));
    when(serviceInstanceService.createServiceInstance(any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(createServiceInstanceResource())));

    ServiceInstanceResponse response =
        serviceInstances.createServiceInstance(
            "new-service-instance-name",
            "serviceName",
            "ServicePlan1",
            Collections.emptySet(),
            null,
            true,
            cloudFoundrySpace);

    assertThat(response)
        .isEqualTo(
            new ServiceInstanceResponse()
                .setServiceInstanceName("new-service-instance-name")
                .setType(CREATE)
                .setState(IN_PROGRESS));
    verify(serviceInstanceService, times(1)).createServiceInstance(any());
    verify(serviceInstanceService, never()).updateServiceInstance(any(), any());
  }

  @Test
  void shouldThrowExceptionWhenCreationReturnsHttpNotFound() {

    when(serviceInstanceService.all(any(), anyListOf(String.class)))
        .thenAnswer(
            invocation -> Calls.response(Response.success(createEmptyOsbServiceInstancePage())));
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class)))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createEmptyUserProvidedServiceInstancePage())));
    when(serviceInstanceService.createServiceInstance(any()))
        .thenReturn(
            Calls.response(
                Response.error(
                    404,
                    ResponseBody.create(
                        MediaType.get("application/json"),
                        "{\"error_code\": \"CF-ResourceNotFound\", \"description\":\"service instance 'new-service-instance-name' could not be created\"}"))));

    assertThrows(
        () ->
            serviceInstances.createServiceInstance(
                "new-service-instance-name",
                "serviceName",
                "ServicePlan1",
                Collections.emptySet(),
                null,
                false,
                cloudFoundrySpace),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): service instance 'new-service-instance-name' could not be created");
    verify(serviceInstanceService, times(1)).createServiceInstance(any());
    verify(serviceInstanceService, never()).updateServiceInstance(any(), any());
  }

  @Test
  void throwExceptionWhenNoServicePlanExistsWithTheNameProvided() {
    Page<ServicePlan> servicePlansPageOne = new Page<>();
    servicePlansPageOne.setTotalResults(0);
    servicePlansPageOne.setTotalPages(1);
    servicePlansPageOne.setResources(Collections.emptyList());
    when(serviceInstanceService.findServicePlans(any(), anyListOf(String.class)))
        .thenAnswer(invocation -> Calls.response(Response.success(servicePlansPageOne)));

    assertThrows(
        () ->
            serviceInstances.createServiceInstance(
                "new-service-instance-name",
                "serviceName",
                "servicePlanName",
                Collections.emptySet(),
                null,
                true,
                cloudFoundrySpace),
        ResourceNotFoundException.class,
        "No plans available for service name 'serviceName'");
  }

  @Test
  void shouldUpdateTheServiceIfAlreadyExists() {
    when(serviceInstanceService.all(any(), anyListOf(String.class)))
        .thenAnswer(invocation -> Calls.response(Response.success(createOsbServiceInstancePage())));
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class)))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createEmptyUserProvidedServiceInstancePage())));
    when(serviceInstanceService.updateServiceInstance(any(), any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(createServiceInstanceResource())));

    ServiceInstanceResponse response =
        serviceInstances.createServiceInstance(
            "new-service-instance-name",
            "serviceName",
            "ServicePlan1",
            Collections.emptySet(),
            null,
            true,
            cloudFoundrySpace);

    assertThat(response)
        .isEqualTo(
            new ServiceInstanceResponse()
                .setServiceInstanceName("new-service-instance-name")
                .setType(UPDATE)
                .setState(IN_PROGRESS));
    verify(serviceInstanceService, times(0)).createServiceInstance(any());
    verify(serviceInstanceService, times(1)).updateServiceInstance(any(), any());
  }

  @Test
  void shouldNotUpdateTheServiceIfAlreadyExists() {
    when(serviceInstanceService.all(any(), anyListOf(String.class)))
        .thenAnswer(invocation -> Calls.response(Response.success(createOsbServiceInstancePage())));
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class)))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createEmptyUserProvidedServiceInstancePage())));
    when(serviceInstanceService.updateServiceInstance(any(), any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(createServiceInstanceResource())));

    ServiceInstanceResponse response =
        serviceInstances.createServiceInstance(
            "new-service-instance-name",
            "serviceName",
            "ServicePlan1",
            Collections.emptySet(),
            null,
            false,
            cloudFoundrySpace);

    assertThat(response)
        .isEqualTo(
            new ServiceInstanceResponse()
                .setServiceInstanceName("new-service-instance-name")
                .setType(CREATE)
                .setState(SUCCEEDED));
    verify(serviceInstanceService, times(0)).createServiceInstance(any());
    verify(serviceInstanceService, times(0)).updateServiceInstance(any(), any());
  }

  @Test
  void shouldThrowExceptionIfServiceExistsAndNeedsChangingButUpdateFails() {
    when(serviceInstanceService.all(any(), anyListOf(String.class)))
        .thenAnswer(invocation -> Calls.response(Response.success(createOsbServiceInstancePage())));
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class)))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createEmptyUserProvidedServiceInstancePage())));
    when(serviceInstanceService.updateServiceInstance(any(), any()))
        .thenReturn(
            Calls.response(
                Response.error(
                    418,
                    ResponseBody.create(
                        MediaType.get("application/json"),
                        "{\"description\":\"update failed\"}"))));

    assertThrows(
        () ->
            serviceInstances.createServiceInstance(
                "new-service-instance-name",
                "serviceName",
                "ServicePlan1",
                Collections.emptySet(),
                null,
                true,
                cloudFoundrySpace),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): update failed");

    verify(serviceInstanceService, times(1)).updateServiceInstance(any(), any());
  }

  @Test
  void shouldThrowCloudFoundryApiErrorWhenMoreThanOneServiceInstanceWithTheSameNameExists() {
    ServiceInstance serviceInstance = new ServiceInstance();
    serviceInstance.setServicePlanGuid("plan-guid").setName("new-service-instance-name");
    Page<ServiceInstance> serviceInstancePage = new Page<>();
    Resource<ServiceInstance> serviceInstanceResource = new Resource<>();
    Resource.Metadata serviceInstanceMetadata = new Resource.Metadata();
    serviceInstanceMetadata.setGuid("service-instance-guid");
    serviceInstanceResource.setMetadata(serviceInstanceMetadata);
    serviceInstanceResource.setEntity(serviceInstance);
    serviceInstancePage.setTotalResults(2);
    serviceInstancePage.setTotalPages(1);
    serviceInstancePage.setResources(
        Arrays.asList(serviceInstanceResource, serviceInstanceResource));

    when(serviceInstanceService.all(any(), anyListOf(String.class)))
        .thenAnswer(invocation -> Calls.response(Response.success(serviceInstancePage)));
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class)))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createEmptyUserProvidedServiceInstancePage())));

    assertThrows(
        () ->
            serviceInstances.createServiceInstance(
                "new-service-instance-name",
                "serviceName",
                "ServicePlan1",
                Collections.emptySet(),
                null,
                true,
                cloudFoundrySpace),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): 2 service instances found with name 'new-service-instance-name' in space 'space', but expected only 1");
  }

  @Test
  void shouldSuccessfullyCreateUserProvidedService() {
    when(serviceInstanceService.all(any(), any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(createEmptyOsbServiceInstancePage())));
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class)))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createEmptyUserProvidedServiceInstancePage())));
    when(serviceInstanceService.createUserProvidedServiceInstance(any()))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createUserProvidedServiceInstanceResource())));

    ServiceInstanceResponse response =
        serviceInstances.createUserProvidedServiceInstance(
            "new-up-service-instance-name",
            "syslogDrainUrl",
            Collections.emptySet(),
            Collections.emptyMap(),
            "routeServiceUrl",
            true,
            cloudFoundrySpace);

    assertThat(response)
        .isEqualTo(
            new ServiceInstanceResponse()
                .setServiceInstanceName("new-up-service-instance-name")
                .setType(CREATE)
                .setState(SUCCEEDED));
    verify(serviceInstanceService, times(1)).createUserProvidedServiceInstance(any());
    verify(serviceInstanceService, never()).updateUserProvidedServiceInstance(any(), any());
  }

  @Test
  void shouldUpdateUserProvidedServiceInstanceIfAlreadyExists() {
    when(serviceInstanceService.all(any(), any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(createEmptyOsbServiceInstancePage())));
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class)))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createUserProvidedServiceInstancePage())));
    when(serviceInstanceService.updateUserProvidedServiceInstance(any(), any()))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createUserProvidedServiceInstanceResource())));

    ServiceInstanceResponse response =
        serviceInstances.createUserProvidedServiceInstance(
            "new-up-service-instance-name",
            "syslogDrainUrl",
            Collections.emptySet(),
            Collections.emptyMap(),
            "routeServiceUrl",
            true,
            cloudFoundrySpace);

    assertThat(response)
        .isEqualTo(
            new ServiceInstanceResponse()
                .setServiceInstanceName("new-up-service-instance-name")
                .setType(UPDATE)
                .setState(SUCCEEDED));
    verify(serviceInstanceService, times(0)).createUserProvidedServiceInstance(any());
    verify(serviceInstanceService, times(1)).updateUserProvidedServiceInstance(any(), any());
  }

  @Test
  void shouldNotUpdateUserProvidedServiceInstanceIfAlreadyExists() {
    when(serviceInstanceService.all(any(), any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(createEmptyOsbServiceInstancePage())));
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class)))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createUserProvidedServiceInstancePage())));
    when(serviceInstanceService.updateUserProvidedServiceInstance(any(), any()))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createUserProvidedServiceInstanceResource())));

    ServiceInstanceResponse response =
        serviceInstances.createUserProvidedServiceInstance(
            "new-up-service-instance-name",
            "syslogDrainUrl",
            Collections.emptySet(),
            Collections.emptyMap(),
            "routeServiceUrl",
            false,
            cloudFoundrySpace);

    assertThat(response)
        .isEqualTo(
            new ServiceInstanceResponse()
                .setServiceInstanceName("new-up-service-instance-name")
                .setType(CREATE)
                .setState(SUCCEEDED));
    verify(serviceInstanceService, times(0)).createUserProvidedServiceInstance(any());
    verify(serviceInstanceService, times(0)).updateUserProvidedServiceInstance(any(), any());
  }

  @Test
  void vetShareServiceArgumentsAndGetSharingRegionIdsShouldThrowExceptionWhenRegionIsBlank() {
    assertThrows(
        () ->
            serviceInstances.vetShareServiceArgumentsAndGetSharingSpaces(
                "", "service-name", singleton("org1 > space1")),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): Please specify a region for the sharing service instance");
  }

  @Test
  void
      vetShareServiceArgumentsAndGetSharingRegionIdsShouldThrowExceptionWhenServiceSharingShareToSpaceIsTheSourceSpace() {
    when(organizations.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findSpaceByRegion(any())).thenReturn(Optional.of(cloudFoundrySpace));
    when(configService.getConfigFeatureFlags())
        .thenReturn(
            Calls.response(
                Response.success(
                    singleton(
                        new ConfigFeatureFlag()
                            .setName(SERVICE_INSTANCE_SHARING)
                            .setEnabled(true)))));
    when(serviceInstanceService.all(any(), any()))
        .thenReturn(
            Calls.response(
                Response.success(createOsbServiceInstancePage(USER_PROVIDED_SERVICE_INSTANCE))));

    assertThrows(
        () ->
            serviceInstances.vetShareServiceArgumentsAndGetSharingSpaces(
                "org > space", "service-instance-name", singleton("org > space")),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): Cannot specify 'org > space' as any of the sharing regions");
  }

  @Test
  void
      getOsbCloudFoundryServiceInstanceShouldThrowExceptionWhenServiceSharingServiceInstanceDoesNotExist() {
    when(organizations.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findSpaceByRegion(any())).thenReturn(Optional.of(cloudFoundrySpace));
    when(serviceInstanceService.all(any(), any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(createEmptyOsbServiceInstancePage())));

    assertThrows(
        () ->
            serviceInstances.getOsbServiceInstanceByRegion("org > space", "service-instance-name"),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): Cannot find service 'service-instance-name' in region 'org > space'");
  }

  @Test
  void getOsbCloudFoundryServiceInstanceShouldThrowExceptionWhenServiceSharingSpaceDoesNotExist() {
    when(organizations.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findSpaceByRegion(any())).thenReturn(Optional.empty());
    when(serviceInstanceService.all(any(), any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(createEmptyOsbServiceInstancePage())));

    assertThrows(
        () ->
            serviceInstances.getOsbServiceInstanceByRegion("org > space", "service-instance-name"),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): Cannot find region 'org > space'");
  }

  @Test
  void checkServiceShareableShouldThrowExceptionWhenManagedServiceSharingFlagIsNotPresent() {
    when(configService.getConfigFeatureFlags())
        .thenAnswer(invocation -> Calls.response(Response.success(Collections.emptySet())));

    assertThrows(
        () -> serviceInstances.checkServiceShareable("service-instance-name", null),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): 'service_instance_sharing' flag must be enabled in order to share services");
  }

  @Test
  void checkServiceShareableShouldThrowExceptionWhenManagedServiceSharingFlagIsSetToFalse() {
    when(configService.getConfigFeatureFlags())
        .thenReturn(
            Calls.response(
                Response.success(
                    singleton(new ConfigFeatureFlag().setName(SERVICE_INSTANCE_SHARING)))));

    assertThrows(
        () -> serviceInstances.checkServiceShareable("service-instance-name", null),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): 'service_instance_sharing' flag must be enabled in order to share services");
  }

  @Test
  void checkServiceShareableShouldThrowExceptionIfServicePlanNotFound() {
    when(configService.getConfigFeatureFlags())
        .thenReturn(
            Calls.response(
                Response.success(
                    singleton(
                        new ConfigFeatureFlag()
                            .setName(SERVICE_INSTANCE_SHARING)
                            .setEnabled(true)))));
    when(serviceInstanceService.all(any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(createOsbServiceInstancePage())));
    when(serviceInstanceService.findServicePlanByServicePlanId(any()))
        .thenAnswer(invocation -> Calls.response(Response.success(null)));

    assertThrows(
        () ->
            serviceInstances.checkServiceShareable(
                "service-instance-name",
                CloudFoundryServiceInstance.builder().planId("some-plan").build()),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): The service plan for 'new-service-plan-name' was not found");
  }

  @Test
  void checkServiceShareableShouldThrowExceptionWhenManagedServiceDoesNotExist() {
    when(configService.getConfigFeatureFlags())
        .thenReturn(
            Calls.response(
                Response.success(
                    singleton(
                        new ConfigFeatureFlag()
                            .setName(SERVICE_INSTANCE_SHARING)
                            .setEnabled(true)))));
    Resource<ServicePlan> rsp = new Resource<>();
    rsp.setEntity(new ServicePlan().setServiceGuid("service-guid"));
    when(serviceInstanceService.findServicePlanByServicePlanId(any()))
        .thenAnswer(invocation -> Calls.response(Response.success(rsp)));
    when(serviceInstanceService.findServiceByServiceId(any()))
        .thenReturn(Calls.response(Response.success(null)));

    assertThrows(
        () ->
            serviceInstances.checkServiceShareable(
                "service-instance-name",
                CloudFoundryServiceInstance.builder().planId("some-plan").build()),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): The service broker for 'service-instance-name' was not found");
  }

  @Test
  void checkServiceShareableShouldThrowExceptionWhenManagedServiceDoesNotSupportSharing() {
    when(organizations.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findSpaceByRegion(any())).thenReturn(Optional.of(cloudFoundrySpace));
    when(configService.getConfigFeatureFlags())
        .thenReturn(
            Calls.response(
                Response.success(
                    singleton(
                        new ConfigFeatureFlag()
                            .setName(SERVICE_INSTANCE_SHARING)
                            .setEnabled(true)))));
    when(serviceInstanceService.all(any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(createOsbServiceInstancePage())));
    Resource<ServicePlan> rsp = new Resource<>();
    rsp.setEntity(new ServicePlan().setServiceGuid("service-guid"));
    when(serviceInstanceService.findServicePlanByServicePlanId(any()))
        .thenAnswer(invocation -> Calls.response(Response.success(rsp)));
    Resource<Service> r = new Resource<>();
    r.setEntity(new Service().setExtra("{\"shareable\": false}"));
    when(serviceInstanceService.findServiceByServiceId(any()))
        .thenAnswer(invocation -> Calls.response(Response.success(r)));

    assertThrows(
        () ->
            serviceInstances.checkServiceShareable(
                "service-instance-name",
                CloudFoundryServiceInstance.builder().planId("some-plan").build()),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): The service broker must be configured as 'shareable' in order to share services");
  }

  @Test
  void shareServiceInstanceShouldSuccessfullyShareAnUnmanagedInstanceToAUniqueListOfRegions() {
    CloudFoundrySpace space1 =
        CloudFoundrySpace.builder()
            .id("space-guid-1")
            .name("some-space-1")
            .organization(cloudFoundryOrganization)
            .build();
    CloudFoundrySpace space2 =
        CloudFoundrySpace.builder()
            .id("space-guid-2")
            .name("some-space-2")
            .organization(cloudFoundryOrganization)
            .build();

    when(organizations.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findSpaceByRegion(any()))
        .thenReturn(Optional.of(space1))
        .thenReturn(Optional.of(space2))
        .thenReturn(Optional.of(cloudFoundrySpace));
    when(serviceInstanceService.all(any(), any()))
        .thenReturn(
            Calls.response(
                Response.success(createOsbServiceInstancePage(USER_PROVIDED_SERVICE_INSTANCE))));
    when(serviceInstanceService.getShareServiceInstanceSpaceIdsByServiceInstanceId(any()))
        .thenReturn(
            Calls.response(Response.success(new SharedTo().setData(Collections.emptySet()))));
    when(serviceInstanceService.shareServiceInstanceToSpaceIds(any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(202, null)));
    ArgumentCaptor<String> serviceInstanceIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<CreateSharedServiceInstances> shareToCaptor =
        ArgumentCaptor.forClass(CreateSharedServiceInstances.class);
    Set<Map<String, String>> s = new HashSet<>();
    s.add(Collections.singletonMap("guid", "space-guid-1"));
    s.add(Collections.singletonMap("guid", "space-guid-2"));
    CreateSharedServiceInstances expectedBody = new CreateSharedServiceInstances().setData(s);
    Set<String> sharedToRegions = new HashSet<>();
    sharedToRegions.add("org1 > space1");
    sharedToRegions.add("org2 > space2");
    ServiceInstanceResponse expectedResult =
        new ServiceInstanceResponse()
            .setServiceInstanceName("service-instance-name")
            .setType(SHARE)
            .setState(SUCCEEDED);

    ServiceInstanceResponse result =
        serviceInstances.shareServiceInstance(
            "org > space", "service-instance-name", sharedToRegions);

    verify(serviceInstanceService)
        .shareServiceInstanceToSpaceIds(serviceInstanceIdCaptor.capture(), shareToCaptor.capture());
    assertThat(serviceInstanceIdCaptor.getValue()).isEqualTo("service-instance-guid");
    assertThat(shareToCaptor.getValue()).isEqualToComparingFieldByFieldRecursively(expectedBody);
    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedResult);
  }

  @Test
  void
      shareServiceInstanceShouldShareManagedServiceInstanceOnlyIntoSpacesIntoWhichServiceInstanceHasNotBeenShared() {
    CloudFoundrySpace space1 =
        CloudFoundrySpace.builder()
            .id("space-guid-1")
            .name("some-space-1")
            .organization(cloudFoundryOrganization)
            .build();
    CloudFoundrySpace space2 =
        CloudFoundrySpace.builder()
            .id("space-guid-2")
            .name("some-space-2")
            .organization(cloudFoundryOrganization)
            .build();

    when(organizations.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findSpaceByRegion(any()))
        .thenReturn(Optional.of(space1))
        .thenReturn(Optional.of(space2))
        .thenReturn(Optional.of(cloudFoundrySpace));
    when(configService.getConfigFeatureFlags())
        .thenReturn(
            Calls.response(
                Response.success(
                    singleton(
                        new ConfigFeatureFlag()
                            .setName(SERVICE_INSTANCE_SHARING)
                            .setEnabled(true)))));
    when(serviceInstanceService.all(any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(createOsbServiceInstancePage())));
    Set<Map<String, String>> alreadySharedTo = new HashSet<>();
    alreadySharedTo.add(Collections.singletonMap("guid", "space-guid-1"));
    alreadySharedTo.add(Collections.singletonMap("guid", "space-guid-3"));
    when(serviceInstanceService.getShareServiceInstanceSpaceIdsByServiceInstanceId(any()))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(new SharedTo().setData(alreadySharedTo))));
    ArgumentCaptor<String> servicePlanIdCaptor = ArgumentCaptor.forClass(String.class);
    Resource<ServicePlan> rsp = new Resource<>();
    rsp.setEntity(new ServicePlan().setServiceGuid("service-guid"));
    when(serviceInstanceService.findServicePlanByServicePlanId(any()))
        .thenAnswer(invocation -> Calls.response(Response.success(rsp)));
    ArgumentCaptor<String> serviceIdCaptor = ArgumentCaptor.forClass(String.class);
    Resource<Service> r = new Resource<>();
    r.setEntity(new Service().setExtra("{\"shareable\": true}"));
    when(serviceInstanceService.findServiceByServiceId(any()))
        .thenAnswer(invocation -> Calls.response(Response.success(r)));
    when(serviceInstanceService.shareServiceInstanceToSpaceIds(any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(202, null)));
    ArgumentCaptor<String> serviceInstanceIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<CreateSharedServiceInstances> shareToCaptor =
        ArgumentCaptor.forClass(CreateSharedServiceInstances.class);
    Set<Map<String, String>> s = singleton(Collections.singletonMap("guid", "space-guid-2"));
    CreateSharedServiceInstances expectedBody = new CreateSharedServiceInstances().setData(s);
    ServiceInstanceResponse expectedResult =
        new ServiceInstanceResponse()
            .setServiceInstanceName("service-instance-name")
            .setType(SHARE)
            .setState(SUCCEEDED);
    Set<String> sharingToRegions = new HashSet<>();
    sharingToRegions.add("org1 > space1");
    sharingToRegions.add("org2 > space2");

    ServiceInstanceResponse result =
        serviceInstances.shareServiceInstance(
            "org > space", "service-instance-name", sharingToRegions);

    verify(serviceInstanceService).findServicePlanByServicePlanId(servicePlanIdCaptor.capture());
    assertThat(servicePlanIdCaptor.getValue()).isEqualTo("plan-guid");
    verify(serviceInstanceService).findServiceByServiceId(serviceIdCaptor.capture());
    assertThat(serviceIdCaptor.getValue()).isEqualTo("service-guid");
    verify(serviceInstanceService)
        .shareServiceInstanceToSpaceIds(serviceInstanceIdCaptor.capture(), shareToCaptor.capture());
    assertThat(serviceInstanceIdCaptor.getValue()).isEqualTo("service-instance-guid");
    assertThat(shareToCaptor.getValue()).isEqualToComparingFieldByFieldRecursively(expectedBody);
    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedResult);
  }

  @Test
  void
      shareServiceInstanceShouldNotShareManagedServiceInstanceIfThereAreNoSpacesIntoWhichItHasNotBeenShared() {
    CloudFoundrySpace space1 =
        CloudFoundrySpace.builder()
            .id("space-guid-1")
            .name("some-space-1")
            .organization(cloudFoundryOrganization)
            .build();
    CloudFoundrySpace space2 =
        CloudFoundrySpace.builder()
            .id("space-guid-2")
            .name("some-space-2")
            .organization(cloudFoundryOrganization)
            .build();

    when(organizations.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findSpaceByRegion(any()))
        .thenReturn(Optional.of(space1))
        .thenReturn(Optional.of(space2))
        .thenReturn(Optional.of(cloudFoundrySpace));
    when(configService.getConfigFeatureFlags())
        .thenReturn(
            Calls.response(
                Response.success(
                    singleton(
                        new ConfigFeatureFlag()
                            .setName(SERVICE_INSTANCE_SHARING)
                            .setEnabled(true)))));
    when(serviceInstanceService.all(any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(createOsbServiceInstancePage())));
    Set<Map<String, String>> alreadySharedTo = new HashSet<>();
    alreadySharedTo.add(Collections.singletonMap("guid", "space-guid-1"));
    alreadySharedTo.add(Collections.singletonMap("guid", "space-guid-2"));
    when(serviceInstanceService.getShareServiceInstanceSpaceIdsByServiceInstanceId(any()))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(new SharedTo().setData(alreadySharedTo))));
    Resource<ServicePlan> rsp = new Resource<>();
    rsp.setEntity(new ServicePlan().setServiceGuid("service-guid"));
    when(serviceInstanceService.findServicePlanByServicePlanId(any()))
        .thenAnswer(invocation -> Calls.response(Response.success(rsp)));
    Resource<Service> r = new Resource<>();
    r.setEntity(new Service().setExtra("{\"shareable\": true}"));
    when(serviceInstanceService.findServiceByServiceId(any()))
        .thenAnswer(invocation -> Calls.response(Response.success(r)));
    Set<Map<String, String>> s = singleton(Collections.singletonMap("guid", "space-guid-2"));
    ServiceInstanceResponse expectedResult =
        new ServiceInstanceResponse()
            .setServiceInstanceName("service-instance-name")
            .setType(SHARE)
            .setState(SUCCEEDED);
    Set<String> sharingToRegions = new HashSet<>();
    sharingToRegions.add("org1 > space1");
    sharingToRegions.add("org2 > space2");

    ServiceInstanceResponse result =
        serviceInstances.shareServiceInstance(
            "org > space", "service-instance-name", sharingToRegions);

    verify(serviceInstanceService, never()).shareServiceInstanceToSpaceIds(any(), any());
    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedResult);
  }

  @Test
  void
      vetUnshareServiceArgumentsAndGetSharingRegionIdsShouldThrowExceptionWhenServiceInstanceNameIsBlank() {
    assertThrows(
        () ->
            serviceInstances.vetUnshareServiceArgumentsAndGetSharingSpaces(
                "", singleton("org1 > space1")),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): Please specify a name for the unsharing service instance");
  }

  @Test
  void vetUnshareServiceArgumentsAndGetSharingRegionIdsShouldThrowExceptionWhenRegionListIsEmpty() {
    assertThrows(
        () ->
            serviceInstances.vetUnshareServiceArgumentsAndGetSharingSpaces(
                "service-instance-name", null),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): Please specify a list of regions for unsharing 'service-instance-name'");
  }

  @Test
  void
      vetUnshareServiceArgumentsAndGetSharingRegionIdsShouldThrowExceptionWhenServiceSharingRegionDoesNotExist() {
    when(organizations.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findSpaceByRegion(any())).thenReturn(Optional.empty());

    assertThrows(
        () ->
            serviceInstances.vetUnshareServiceArgumentsAndGetSharingSpaces(
                "service-instance-name", singleton("org1 > space1")),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): Cannot find region 'org1 > space1' for unsharing");
  }

  @Test
  void
      vetUnshareServiceArgumentsAndGetSharingRegionIdsShouldThrowExceptionWhenServiceSharingShareToSpaceDoesNotExist() {
    when(organizations.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findSpaceByRegion(any())).thenReturn(Optional.empty());
    when(configService.getConfigFeatureFlags())
        .thenReturn(
            Calls.response(
                Response.success(
                    singleton(
                        new ConfigFeatureFlag()
                            .setName(SERVICE_INSTANCE_SHARING)
                            .setEnabled(true)))));
    when(serviceInstanceService.all(any(), any()))
        .thenReturn(
            Calls.response(
                Response.success(createOsbServiceInstancePage(USER_PROVIDED_SERVICE_INSTANCE))));

    assertThrows(
        () ->
            serviceInstances.vetUnshareServiceArgumentsAndGetSharingSpaces(
                "service-instance-name", singleton("org1 > space1")),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): Cannot find region 'org1 > space1' for unsharing");
  }

  @Test
  void
      unshareServiceInstanceShouldSuccessfullyUnshareAnInstanceOnlyFromAUniqueListOfRegionIdsWhereItHadBeenShared() {
    CloudFoundrySpace space0 =
        CloudFoundrySpace.builder()
            .id("space-guid-0")
            .name("some-space-0")
            .organization(cloudFoundryOrganization)
            .build();
    CloudFoundrySpace space1 =
        CloudFoundrySpace.builder()
            .id("space-guid-1")
            .name("some-space-1")
            .organization(cloudFoundryOrganization)
            .build();
    CloudFoundrySpace space2 =
        CloudFoundrySpace.builder()
            .id("space-guid-2")
            .name("some-space-2")
            .organization(cloudFoundryOrganization)
            .build();

    when(organizations.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findSpaceByRegion(any()))
        .thenReturn(Optional.of(space0))
        .thenReturn(Optional.of(space1))
        .thenReturn(Optional.of(space2));
    when(spaces.getServiceInstanceByNameAndSpace(any(), eq(space0))).thenReturn(null);
    when(spaces.getServiceInstanceByNameAndSpace(any(), eq(space1)))
        .thenReturn(
            CloudFoundryServiceInstance.builder()
                .name("service-instance-name")
                .id("service-instance-guid-1")
                .build());
    when(spaces.getServiceInstanceByNameAndSpace(any(), eq(space2)))
        .thenReturn(
            CloudFoundryServiceInstance.builder()
                .name("service-instance-name")
                .id("service-instance-guid-2")
                .build());
    when(serviceInstanceService.unshareServiceInstanceFromSpaceId(any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(202, null)));
    Set<String> unshareFromRegions = new HashSet<>();
    unshareFromRegions.add("org0 > some-space-0");
    unshareFromRegions.add("org1 > some-space-1");
    unshareFromRegions.add("org2 > some-space-2");
    ServiceInstanceResponse expectedResult =
        new ServiceInstanceResponse()
            .setServiceInstanceName("service-instance-name")
            .setType(UNSHARE)
            .setState(SUCCEEDED);

    ServiceInstanceResponse result =
        serviceInstances.unshareServiceInstance("service-instance-name", unshareFromRegions);

    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedResult);
    verify(serviceInstanceService)
        .unshareServiceInstanceFromSpaceId("service-instance-guid-1", "space-guid-1");
    verify(serviceInstanceService)
        .unshareServiceInstanceFromSpaceId("service-instance-guid-2", "space-guid-2");
    verify(spaces).getServiceInstanceByNameAndSpace(eq("service-instance-name"), eq(space0));
    verify(spaces).getServiceInstanceByNameAndSpace(eq("service-instance-name"), eq(space1));
    verify(spaces).getServiceInstanceByNameAndSpace(eq("service-instance-name"), eq(space2));
  }

  @Test
  void getServiceInstanceShouldThrowAnExceptionWhenTheRegionCannotBeFound() {
    when(organizations.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findSpaceByRegion(any())).thenReturn(Optional.empty());

    assertThrows(
        () -> serviceInstances.getServiceInstance("org > space", "service-instance-name"),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): Cannot find region 'org > space'");
  }

  @Test
  void getServiceInstanceShouldReturnCloudFoundryOsbServiceInstance() {
    when(organizations.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findSpaceByRegion(any())).thenReturn(Optional.of(cloudFoundrySpace));

    when(serviceInstanceService.all(any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(createOsbServiceInstancePage())));

    CloudFoundryServiceInstance results =
        serviceInstances.getServiceInstance("org > space", "new-service-instance-name");
    CloudFoundryServiceInstance expected =
        CloudFoundryServiceInstance.builder()
            .id("service-instance-guid")
            .planId("plan-guid")
            .type(MANAGED_SERVICE_INSTANCE.toString())
            .serviceInstanceName("new-service-instance-name")
            .status(SUCCEEDED.toString())
            .build();

    assertThat(results).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  void getServiceInstanceShouldReturnCloudFoundryUserProvidedServiceInstance() {
    when(organizations.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findSpaceByRegion(any())).thenReturn(Optional.of(cloudFoundrySpace));

    when(serviceInstanceService.all(any(), any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(createEmptyOsbServiceInstancePage())));
    when(serviceInstanceService.allUserProvided(any(), any()))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createUserProvidedServiceInstancePage())));

    CloudFoundryServiceInstance results =
        serviceInstances.getServiceInstance("org > space", "up-service-instance-name");
    CloudFoundryServiceInstance expected =
        CloudFoundryServiceInstance.builder()
            .id("up-service-instance-guid")
            .type(USER_PROVIDED_SERVICE_INSTANCE.toString())
            .serviceInstanceName("up-service-instance-name")
            .status(SUCCEEDED.toString())
            .build();

    assertThat(results).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  void getOsbServiceInstanceShouldReturnAServiceInstanceWhenExactlyOneIsReturnedFromApi() {
    when(serviceInstanceService.all(any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(createOsbServiceInstancePage())));

    CloudFoundryServiceInstance service =
        serviceInstances.getOsbServiceInstance(cloudFoundrySpace, "service-instance-name");
    CloudFoundryServiceInstance expected =
        CloudFoundryServiceInstance.builder()
            .id("service-instance-guid")
            .planId("plan-guid")
            .type(MANAGED_SERVICE_INSTANCE.toString())
            .serviceInstanceName("new-service-instance-name")
            .status(SUCCEEDED.toString())
            .build();

    assertThat(service).isNotNull();
    assertThat(service).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  void getUserProvidedServiceInstanceShouldReturnAServiceInstanceWhenExactlyOneIsReturnedFromApi() {
    when(serviceInstanceService.allUserProvided(any(), any()))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createUserProvidedServiceInstancePage())));

    CloudFoundryServiceInstance service =
        serviceInstances.getUserProvidedServiceInstance(
            cloudFoundrySpace, "up-service-instance-name");

    assertThat(service).isNotNull();
    CloudFoundryServiceInstance expected =
        CloudFoundryServiceInstance.builder()
            .id("up-service-instance-guid")
            .type(USER_PROVIDED_SERVICE_INSTANCE.toString())
            .serviceInstanceName("up-service-instance-name")
            .status(SUCCEEDED.toString())
            .build();
    assertThat(service).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  void getOsbServiceInstanceShouldThrowAnExceptionWhenMultipleServicesAreReturnedFromApi() {
    when(serviceInstanceService.all(any(), any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(createEmptyOsbServiceInstancePage())));

    assertThat(
            serviceInstances.getOsbServiceInstance(cloudFoundrySpace, "new-service-instance-name"))
        .isNull();
  }

  @Test
  void getOsbServiceInstanceShouldThrowAnExceptionWhenNoServicesAreReturnedFromApi() {
    Page<ServiceInstance> page = new Page<>();
    page.setTotalResults(2);
    page.setTotalPages(1);
    page.setResources(
        Arrays.asList(createServiceInstanceResource(), createServiceInstanceResource()));
    when(serviceInstanceService.all(any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(page)));

    assertThrows(
        () ->
            serviceInstances.getOsbServiceInstance(cloudFoundrySpace, "new-service-instance-name"),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): 2 service instances found with name 'new-service-instance-name' in space 'space', but expected only 1");
  }

  @Test
  void getOsbServiceInstanceShouldThrowExceptionWhenServiceNameIsBlank() {
    assertThrows(
        () -> serviceInstances.getOsbServiceInstance(cloudFoundrySpace, ""),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): Please specify a name for the service being sought");
  }

  @Test
  void destroyServiceInstanceShouldSucceedWhenNoServiceBindingsExist() {
    when(serviceInstanceService.all(any(), anyListOf(String.class)))
        .thenAnswer(invocation -> Calls.response(Response.success(createOsbServiceInstancePage())));
    when(serviceInstanceService.getBindingsForServiceInstance("service-instance-guid", null, null))
        .thenAnswer(invocation -> Calls.response(Response.success(new Page<>())));
    when(serviceInstanceService.destroyServiceInstance(any()))
        .thenAnswer(invocation -> Calls.response(Response.success(202, null)));

    ServiceInstanceResponse response =
        serviceInstances.destroyServiceInstance(cloudFoundrySpace, "new-service-instance-name");

    assertThat(response)
        .isEqualTo(
            new ServiceInstanceResponse()
                .setServiceInstanceName("new-service-instance-name")
                .setType(DELETE)
                .setState(IN_PROGRESS));
    verify(serviceInstanceService, times(1)).all(any(), anyListOf(String.class));
    verify(serviceInstanceService, times(1)).destroyServiceInstance(any());
    verify(serviceInstanceService, never()).allUserProvided(any(), any());
  }

  @Test
  void destroyServiceInstanceShouldThrowExceptionWhenDeleteServiceInstanceFails() {
    Page<ServiceBinding> serviceBindingPage = new Page<>();
    serviceBindingPage.setTotalResults(0);
    serviceBindingPage.setTotalPages(1);

    when(serviceInstanceService.all(any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(createOsbServiceInstancePage())));
    when(serviceInstanceService.getBindingsForServiceInstance(any(), any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(serviceBindingPage)));
    when(serviceInstanceService.destroyServiceInstance(any()))
        .thenReturn(
            Calls.response(
                Response.error(500, ResponseBody.create(MediaType.get("application/json"), "{}"))));

    assertThrows(
        () -> serviceInstances.destroyServiceInstance(cloudFoundrySpace, "service-instance-name"),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): ");

    verify(serviceInstanceService, times(1)).destroyServiceInstance(any());
    verify(serviceInstanceService, never()).allUserProvided(any(), any());
  }

  @Test
  void destroyServiceInstanceShouldReturnSuccessWhenServiceInstanceDoesNotExist() {
    when(serviceInstanceService.all(any(), anyListOf(String.class)))
        .thenAnswer(
            invocation -> Calls.response(Response.success(createEmptyOsbServiceInstancePage())));
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class)))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createEmptyUserProvidedServiceInstancePage())));

    ServiceInstanceResponse response =
        serviceInstances.destroyServiceInstance(cloudFoundrySpace, "service-instance-name");

    assertThat(response)
        .isEqualTo(
            new ServiceInstanceResponse()
                .setServiceInstanceName("service-instance-name")
                .setType(DELETE)
                .setState(LastOperation.State.NOT_FOUND));
    verify(serviceInstanceService, never()).destroyServiceInstance(any());
  }

  @Test
  void destroyServiceInstanceShouldFailIfServiceBindingsExists() {
    when(serviceInstanceService.all(any(), anyListOf(String.class)))
        .thenAnswer(invocation -> Calls.response(Response.success(createOsbServiceInstancePage())));
    when(serviceInstanceService.getBindingsForServiceInstance("service-instance-guid", null, null))
        .thenReturn(
            Calls.response(
                Response.success(Page.singleton(new ServiceBinding(), "service-binding-guid"))));

    assertThrows(
        () -> serviceInstances.destroyServiceInstance(cloudFoundrySpace, "service-instance-name"),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): Unable to destroy service instance while 1 service binding(s) exist");

    verify(serviceInstanceService, never()).destroyServiceInstance(any());
    verify(serviceInstanceService, never()).allUserProvided(any(), any());
  }

  @Test
  void destroyUserProvidedServiceInstanceShouldSucceedWhenNoServiceBindingsExist() {
    when(serviceInstanceService.all(any(), anyListOf(String.class)))
        .thenAnswer(invocation -> Calls.response(Response.success(new Page<>())));
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class)))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createUserProvidedServiceInstancePage())));
    when(serviceInstanceService.getBindingsForUserProvidedServiceInstance(
            "up-service-instance-guid", null, null))
        .thenAnswer(invocation -> Calls.response(Response.success(new Page<>())));
    when(serviceInstanceService.destroyUserProvidedServiceInstance(any()))
        .thenAnswer(invocation -> Calls.response(Response.success("")));

    ServiceInstanceResponse response =
        serviceInstances.destroyServiceInstance(cloudFoundrySpace, "new-service-instance-name");

    assertThat(response)
        .isEqualTo(
            new ServiceInstanceResponse()
                .setServiceInstanceName("new-service-instance-name")
                .setType(DELETE)
                .setState(IN_PROGRESS));
    verify(serviceInstanceService, times(1)).all(any(), anyListOf(String.class));
    verify(serviceInstanceService, times(1)).allUserProvided(any(), any());
    verify(serviceInstanceService, times(1)).destroyUserProvidedServiceInstance(any());
    verify(serviceInstanceService, times(1))
        .getBindingsForUserProvidedServiceInstance(any(), any(), any());
    verify(serviceInstanceService, never()).destroyServiceInstance(any());
  }

  @Test
  void destroyUserProvidedServiceInstanceShouldThrowExceptionWhenDeleteServiceInstanceFails() {
    Page<ServiceBinding> serviceBindingPage = new Page<>();
    serviceBindingPage.setTotalResults(0);
    serviceBindingPage.setTotalPages(1);

    when(serviceInstanceService.all(any(), anyListOf(String.class)))
        .thenAnswer(invocation -> Calls.response(Response.success(new Page<>())));
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class)))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createUserProvidedServiceInstancePage())));
    when(serviceInstanceService.getBindingsForUserProvidedServiceInstance(any(), any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(serviceBindingPage)));
    when(serviceInstanceService.destroyUserProvidedServiceInstance(any()))
        .thenReturn(
            Calls.response(
                Response.error(
                    418,
                    ResponseBody.create(
                        MediaType.get("application/json"),
                        "{\"error_code\": \"CF-ServiceBindingAppServiceTaken\", \"description\":\"i'm a teapod\"}"))));

    assertThrows(
        () -> serviceInstances.destroyServiceInstance(cloudFoundrySpace, "service-instance-name"),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): i'm a teapod");

    verify(serviceInstanceService, times(1)).all(any(), anyList());
    verify(serviceInstanceService, times(1)).allUserProvided(any(), any());
    verify(serviceInstanceService, times(1))
        .getBindingsForUserProvidedServiceInstance(any(), any(), any());
    verify(serviceInstanceService, times(1)).destroyUserProvidedServiceInstance(any());
    verify(serviceInstanceService, never()).destroyServiceInstance(any());
  }

  @Test
  void destroyUserProvidedServiceInstanceShouldFailIfServiceBindingsExists() {
    when(serviceInstanceService.all(any(), any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(createEmptyOsbServiceInstancePage())));
    when(serviceInstanceService.allUserProvided(any(), any()))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(createUserProvidedServiceInstancePage())));
    when(serviceInstanceService.getBindingsForUserProvidedServiceInstance(
            "up-service-instance-guid", null, null))
        .thenReturn(
            Calls.response(
                Response.success(
                    Page.singleton(new ServiceBinding(), "up-service-instance-guid"))));

    assertThrows(
        () -> serviceInstances.destroyServiceInstance(cloudFoundrySpace, "service-instance-name"),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): Unable to destroy service instance while 1 service binding(s) exist");

    verify(serviceInstanceService, times(1)).all(any(), any());
    verify(serviceInstanceService, times(1)).allUserProvided(any(), any());
    verify(serviceInstanceService, never()).destroyUserProvidedServiceInstance(any());
    verify(serviceInstanceService, never()).destroyServiceInstance(any());
  }

  private Resource<ServiceInstance> createServiceInstanceResource() {
    ServiceInstance serviceInstance = new ServiceInstance();
    serviceInstance.setServicePlanGuid("plan-guid").setName("new-service-instance-name");
    Resource<ServiceInstance> serviceInstanceResource = new Resource<>();
    serviceInstanceResource.setMetadata(new Resource.Metadata().setGuid("service-instance-guid"));
    serviceInstanceResource.setEntity(serviceInstance);
    return serviceInstanceResource;
  }

  private Resource<ServiceBinding> createServiceBindingResource() {
    ServiceBinding serviceBinding = new ServiceBinding();
    serviceBinding.setAppGuid("servergroup-id");
    serviceBinding.setName("");
    serviceBinding.setServiceInstanceGuid("service-instance-guid");
    Resource<ServiceBinding> serviceBindingResource = new Resource<>();
    serviceBindingResource.setEntity(serviceBinding);
    serviceBindingResource.setMetadata(new Resource.Metadata().setGuid("service-binding-guid"));
    return serviceBindingResource;
  }

  private Resource<UserProvidedServiceInstance> createUserProvidedServiceInstanceResource() {
    UserProvidedServiceInstance userProvidedServiceInstance = new UserProvidedServiceInstance();
    userProvidedServiceInstance.setName("new-service-instance-name");
    Resource<UserProvidedServiceInstance> userProvidedServiceInstanceResource = new Resource<>();
    userProvidedServiceInstanceResource.setMetadata(
        new Resource.Metadata().setGuid("up-service-instance-guid"));
    userProvidedServiceInstanceResource.setEntity(userProvidedServiceInstance);
    return userProvidedServiceInstanceResource;
  }

  private Page<ServiceInstance> createOsbServiceInstancePage() {
    return createOsbServiceInstancePage(MANAGED_SERVICE_INSTANCE);
  }

  private Page<ServiceInstance> createOsbServiceInstancePage(ServiceInstance.Type type) {
    ServiceInstance serviceInstance = new ServiceInstance();
    serviceInstance
        .setLastOperation(new LastOperation().setType(CREATE).setState(SUCCEEDED))
        .setServicePlanGuid("plan-guid")
        .setType(type)
        .setName("new-service-instance-name")
        .setTags(singleton("spinnakerVersion-v001"));
    return Page.singleton(serviceInstance, "service-instance-guid");
  }

  private Page<ServiceInstance> createEmptyOsbServiceInstancePage() {
    Page<ServiceInstance> serviceInstancePage = new Page<>();
    serviceInstancePage.setTotalResults(0).setTotalPages(1);
    return serviceInstancePage;
  }

  private Page<UserProvidedServiceInstance> createUserProvidedServiceInstancePage() {
    UserProvidedServiceInstance serviceInstance = new UserProvidedServiceInstance();
    serviceInstance.setName("up-service-instance-name").setTags(singleton("spinnakerVersion-v000"));
    return Page.singleton(serviceInstance, "up-service-instance-guid");
  }

  private Page<UserProvidedServiceInstance> createEmptyUserProvidedServiceInstancePage() {
    Page<UserProvidedServiceInstance> userProvidedServiceInstancePage = new Page<>();
    userProvidedServiceInstancePage.setTotalResults(0).setTotalPages(1);
    return userProvidedServiceInstancePage;
  }
}
