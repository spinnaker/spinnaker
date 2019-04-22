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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ConfigService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ServiceInstanceService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ErrorDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceInstanceResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateSharedServiceInstances;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import retrofit.RetrofitError;
import retrofit.client.Response;

import java.util.*;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ConfigFeatureFlag.ConfigFlag.SERVICE_INSTANCE_SHARING;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.IN_PROGRESS;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.SUCCEEDED;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.*;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ServiceInstance.Type.MANAGED_SERVICE_INSTANCE;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ServiceInstance.Type.USER_PROVIDED_SERVICE_INSTANCE;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.utils.TestUtils.assertThrows;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ServiceInstancesTest {
  private CloudFoundryOrganization cloudFoundryOrganization = CloudFoundryOrganization.builder()
    .id("some-org-guid")
    .name("org")
    .build();
  private CloudFoundrySpace cloudFoundrySpace = CloudFoundrySpace.builder()
    .id("some-space-guid")
    .name("space")
    .organization(cloudFoundryOrganization)
    .build();
  private ServiceInstanceService serviceInstanceService = mock(ServiceInstanceService.class);
  private ConfigService configService = mock(ConfigService.class);
  private Organizations orgs = mock(Organizations.class);
  private Spaces spaces = mock(Spaces.class);
  private ServiceInstances serviceInstances = new ServiceInstances(serviceInstanceService, configService, orgs, spaces);

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

    Page<ServiceInstance> serviceMappingPageOne = createEmptyOsbServiceInstancePage();
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

    when(serviceInstanceService.all(any(), any())).thenReturn(createEmptyOsbServiceInstancePage());

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

    when(serviceInstanceService.all(any(), any())).thenReturn(createEmptyOsbServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createEmptyUserProvidedServiceInstancePage());
    when(serviceInstanceService.createServiceInstance(any())).thenReturn(createServiceInstanceResource());

    ServiceInstanceResponse response = serviceInstances.createServiceInstance("new-service-instance-name",
      "serviceName",
      "ServicePlan1",
      Collections.emptySet(),
      null,
      true,
      cloudFoundrySpace);

    assertThat(response).isEqualTo(new ServiceInstanceResponse()
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

    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(createEmptyOsbServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createEmptyUserProvidedServiceInstancePage());
    when(serviceInstanceService.createServiceInstance(any())).thenThrow(retrofitErrorNotFound);

    assertThrows(
      () -> serviceInstances.createServiceInstance("new-service-instance-name",
        "serviceName",
        "ServicePlan1",
        Collections.emptySet(),
        null,
        false,
        cloudFoundrySpace),
      CloudFoundryApiException.class, "Cloud Foundry API returned with error(s): service instance 'new-service-instance-name' could not be created");
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
      () -> serviceInstances.createServiceInstance("new-service-instance-name",
        "serviceName",
        "servicePlanName",
        Collections.emptySet(),
        null,
        true,
        cloudFoundrySpace),
      ResourceNotFoundException.class, "No plans available for service name 'serviceName'");
  }

  @Test
  void shouldUpdateTheServiceIfAlreadyExists() {
    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(createOsbServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createEmptyUserProvidedServiceInstancePage());
    when(serviceInstanceService.updateServiceInstance(any(), any())).thenReturn(createServiceInstanceResource());

    ServiceInstanceResponse response = serviceInstances.createServiceInstance("new-service-instance-name",
      "serviceName",
      "ServicePlan1",
      Collections.emptySet(),
      null,
      true,
      cloudFoundrySpace);

    assertThat(response).isEqualTo(new ServiceInstanceResponse()
      .setServiceInstanceName("new-service-instance-name")
      .setType(UPDATE)
      .setState(IN_PROGRESS)
    );
    verify(serviceInstanceService, times(0)).createServiceInstance(any());
    verify(serviceInstanceService, times(1)).updateServiceInstance(any(), any());
  }

  @Test
  void shouldNotUpdateTheServiceIfAlreadyExists() {
    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(createOsbServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createEmptyUserProvidedServiceInstancePage());
    when(serviceInstanceService.updateServiceInstance(any(), any())).thenReturn(createServiceInstanceResource());

    ServiceInstanceResponse response = serviceInstances.createServiceInstance("new-service-instance-name",
      "serviceName",
      "ServicePlan1",
      Collections.emptySet(),
      null,
      false,
      cloudFoundrySpace);

    assertThat(response).isEqualTo(new ServiceInstanceResponse()
      .setServiceInstanceName("new-service-instance-name")
      .setType(CREATE)
      .setState(SUCCEEDED)
    );
    verify(serviceInstanceService, times(0)).createServiceInstance(any());
    verify(serviceInstanceService, times(0)).updateServiceInstance(any(), any());
  }

  @Test
  void shouldThrowExceptionIfServiceExistsAndNeedsChangingButUpdateFails() {
    RetrofitError updateError = mock(RetrofitError.class);
    when(updateError.getResponse()).thenReturn(new Response("url", 418, "reason", Collections.emptyList(), null));
    when(updateError.getBodyAs(any())).thenReturn(new ErrorDescription());

    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(createOsbServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createEmptyUserProvidedServiceInstancePage());
    when(serviceInstanceService.updateServiceInstance(any(), any())).thenThrow(updateError);

    assertThrows(
      () -> serviceInstances.createServiceInstance("new-service-instance-name",
        "serviceName",
        "ServicePlan1",
        Collections.emptySet(),
        null,
        true,
        cloudFoundrySpace),
      CloudFoundryApiException.class, "Cloud Foundry API returned with error(s): ");

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
    serviceInstancePage.setResources(Arrays.asList(serviceInstanceResource, serviceInstanceResource));

    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(serviceInstancePage);
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createEmptyUserProvidedServiceInstancePage());

    assertThrows(
      () -> serviceInstances.createServiceInstance("new-service-instance-name",
        "serviceName",
        "ServicePlan1",
        Collections.emptySet(),
        null,
        true,
        cloudFoundrySpace),
      CloudFoundryApiException.class, "Cloud Foundry API returned with error(s): 2 service instances found with name 'new-service-instance-name' in space 'space', but expected only 1");
  }

  @Test
  void shouldSuccessfullyCreateUserProvidedService() {
    when(serviceInstanceService.all(any(), any())).thenReturn(createEmptyOsbServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createEmptyUserProvidedServiceInstancePage());
    when(serviceInstanceService.createUserProvidedServiceInstance(any())).thenReturn(createUserProvidedServiceInstanceResource());

    ServiceInstanceResponse response = serviceInstances.createUserProvidedServiceInstance(
      "new-up-service-instance-name",
      "syslogDrainUrl",
      Collections.emptySet(),
      Collections.emptyMap(),
      "routeServiceUrl",
      true,
      cloudFoundrySpace
    );

    assertThat(response).isEqualTo(new ServiceInstanceResponse()
      .setServiceInstanceName("new-up-service-instance-name")
      .setType(CREATE)
      .setState(SUCCEEDED)
    );
    verify(serviceInstanceService, times(1)).createUserProvidedServiceInstance(any());
    verify(serviceInstanceService, never()).updateUserProvidedServiceInstance(any(), any());
  }

  @Test
  void shouldUpdateUserProvidedServiceInstanceIfAlreadyExists() {
    when(serviceInstanceService.all(any(), any())).thenReturn(createEmptyOsbServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createUserProvidedServiceInstancePage());
    when(serviceInstanceService.updateUserProvidedServiceInstance(any(), any())).thenReturn(createUserProvidedServiceInstanceResource());

    ServiceInstanceResponse response = serviceInstances.createUserProvidedServiceInstance(
      "new-up-service-instance-name",
      "syslogDrainUrl",
      Collections.emptySet(),
      Collections.emptyMap(),
      "routeServiceUrl",
      true,
      cloudFoundrySpace
    );

    assertThat(response).isEqualTo(new ServiceInstanceResponse()
      .setServiceInstanceName("new-up-service-instance-name")
      .setType(UPDATE)
      .setState(SUCCEEDED)
    );
    verify(serviceInstanceService, times(0)).createUserProvidedServiceInstance(any());
    verify(serviceInstanceService, times(1)).updateUserProvidedServiceInstance(any(), any());
  }

  @Test
  void shouldNotUpdateUserProvidedServiceInstanceIfAlreadyExists() {
    when(serviceInstanceService.all(any(), any())).thenReturn(createEmptyOsbServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createUserProvidedServiceInstancePage());
    when(serviceInstanceService.updateUserProvidedServiceInstance(any(), any())).thenReturn(createUserProvidedServiceInstanceResource());

    ServiceInstanceResponse response = serviceInstances.createUserProvidedServiceInstance(
      "new-up-service-instance-name",
      "syslogDrainUrl",
      Collections.emptySet(),
      Collections.emptyMap(),
      "routeServiceUrl",
      false,
      cloudFoundrySpace
    );

    assertThat(response).isEqualTo(new ServiceInstanceResponse()
      .setServiceInstanceName("new-up-service-instance-name")
      .setType(CREATE)
      .setState(SUCCEEDED)
    );
    verify(serviceInstanceService, times(0)).createUserProvidedServiceInstance(any());
    verify(serviceInstanceService, times(0)).updateUserProvidedServiceInstance(any(), any());
  }

  @Test
  void vetShareServiceArgumentsAndGetSharingRegionIdsShouldThrowExceptionWhenRegionIsBlank() {
    assertThrows(() -> serviceInstances.vetShareServiceArgumentsAndGetSharingSpaces(
      "",
      "service-name",
      singleton("org1 > space1")),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): Please specify a region for the sharing service instance");
  }

  @Test
  void vetShareServiceArgumentsAndGetSharingRegionIdsShouldThrowExceptionWhenServiceSharingShareToSpaceIsTheSourceSpace() {
    when(orgs.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findByName(any(), any())).thenReturn(cloudFoundrySpace);
    when(configService.getConfigFeatureFlags())
      .thenReturn(singleton(new ConfigFeatureFlag().setName(SERVICE_INSTANCE_SHARING).setEnabled(true)));
    when(serviceInstanceService.all(any(), any())).thenReturn(createOsbServiceInstancePage(USER_PROVIDED_SERVICE_INSTANCE));

    assertThrows(() -> serviceInstances.vetShareServiceArgumentsAndGetSharingSpaces(
      "org > space",
      "service-instance-name",
      singleton("org > space")),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): Cannot specify 'org > space' as any of the sharing regions");
  }

  @Test
  void getOsbCloudFoundryServiceInstanceShouldThrowExceptionWhenServiceSharingServiceInstanceDoesNotExist() {
    when(orgs.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findByName(any(), any())).thenReturn(cloudFoundrySpace);
    when(serviceInstanceService.all(any(), any())).thenReturn(createEmptyOsbServiceInstancePage());

    assertThrows(() -> serviceInstances.getOsbServiceInstanceByRegion(
      "org > space",
      "service-instance-name"),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): Cannot find service 'service-instance-name' in region 'org > space'");
  }

  @Test
  void getOsbCloudFoundryServiceInstanceShouldThrowExceptionWhenServiceSharingSpaceDoesNotExist() {
    when(orgs.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findByName(any(), any())).thenReturn(null);
    when(serviceInstanceService.all(any(), any())).thenReturn(createEmptyOsbServiceInstancePage());

    assertThrows(() -> serviceInstances.getOsbServiceInstanceByRegion(
      "org > space",
      "service-instance-name"),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): Cannot find region 'org > space'");
  }

  @Test
  void checkServiceShareableShouldThrowExceptionWhenManagedServiceSharingFlagIsNotPresent() {
    when(configService.getConfigFeatureFlags()).thenReturn(Collections.emptySet());

    assertThrows(() -> serviceInstances.checkServiceShareable("service-instance-name", null),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): 'service_instance_sharing' flag must be enabled in order to share services");
  }

  @Test
  void checkServiceShareableShouldThrowExceptionWhenManagedServiceSharingFlagIsSetToFalse() {
    when(configService.getConfigFeatureFlags())
      .thenReturn(singleton(new ConfigFeatureFlag().setName(SERVICE_INSTANCE_SHARING)));

    assertThrows(() -> serviceInstances.checkServiceShareable("service-instance-name", null),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): 'service_instance_sharing' flag must be enabled in order to share services");
  }

  @Test
  void checkServiceShareableShouldThrowExceptionIfServicePlanNotFound() {
    when(configService.getConfigFeatureFlags())
      .thenReturn(singleton(new ConfigFeatureFlag().setName(SERVICE_INSTANCE_SHARING).setEnabled(true)));
    when(serviceInstanceService.all(any(), any())).thenReturn(createOsbServiceInstancePage());
    when(serviceInstanceService.findServicePlanByServicePlanId(any())).thenReturn(null);

    assertThrows(() -> serviceInstances.checkServiceShareable(
      "service-instance-name",
      CloudFoundryServiceInstance.builder()
        .planId("some-plan")
        .build()),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): The service plan for 'new-service-plan-name' was not found");
  }

  @Test
  void checkServiceShareableShouldThrowExceptionWhenManagedServiceDoesNotExist() {
    when(configService.getConfigFeatureFlags())
      .thenReturn(singleton(new ConfigFeatureFlag().setName(SERVICE_INSTANCE_SHARING).setEnabled(true)));
    Resource<ServicePlan> rsp = new Resource<>();
    rsp.setEntity(new ServicePlan().setServiceGuid("service-guid"));
    when(serviceInstanceService.findServicePlanByServicePlanId(any())).thenReturn(rsp);
    when(serviceInstanceService.findServiceByServiceId(any())).thenReturn(null);

    assertThrows(() -> serviceInstances.checkServiceShareable(
      "service-instance-name",
      CloudFoundryServiceInstance.builder()
        .planId("some-plan")
        .build()),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): The service broker for 'service-instance-name' was not found");
  }

  @Test
  void checkServiceShareableShouldThrowExceptionWhenManagedServiceDoesNotSupportSharing() {
    when(orgs.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findByName(any(), any())).thenReturn(cloudFoundrySpace);
    when(configService.getConfigFeatureFlags())
      .thenReturn(singleton(new ConfigFeatureFlag().setName(SERVICE_INSTANCE_SHARING).setEnabled(true)));
    when(serviceInstanceService.all(any(), any())).thenReturn(createOsbServiceInstancePage());
    Resource<ServicePlan> rsp = new Resource<>();
    rsp.setEntity(new ServicePlan().setServiceGuid("service-guid"));
    when(serviceInstanceService.findServicePlanByServicePlanId(any())).thenReturn(rsp);
    Resource<Service> r = new Resource<>();
    r.setEntity(new Service().setExtra("{\"shareable\": false}"));
    when(serviceInstanceService.findServiceByServiceId(any())).thenReturn(r);

    assertThrows(() -> serviceInstances.checkServiceShareable(
      "service-instance-name",
      CloudFoundryServiceInstance.builder()
        .planId("some-plan")
        .build()),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): The service broker must be configured as 'shareable' in order to share services");
  }

  @Test
  void shareServiceInstanceShouldSuccessfullyShareAnUnmanagedInstanceToAUniqueListOfRegions() {
    CloudFoundrySpace space1 = CloudFoundrySpace.builder()
      .id("space-guid-1")
      .name("some-space-1")
      .organization(cloudFoundryOrganization)
      .build();
    CloudFoundrySpace space2 = CloudFoundrySpace.builder()
      .id("space-guid-2")
      .name("some-space-2")
      .organization(cloudFoundryOrganization)
      .build();

    when(orgs.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findByName(any(), any()))
      .thenReturn(space1)
      .thenReturn(space2)
      .thenReturn(cloudFoundrySpace);
    when(serviceInstanceService.all(any(), any())).thenReturn(createOsbServiceInstancePage(USER_PROVIDED_SERVICE_INSTANCE));
    when(serviceInstanceService.getShareServiceInstanceSpaceIdsByServiceInstanceId(any()))
      .thenReturn(new SharedTo().setData(Collections.emptySet()));
    when(serviceInstanceService.shareServiceInstanceToSpaceIds(any(), any()))
      .thenReturn(new Response("url", 202, "reason", Collections.emptyList(), null));
    ArgumentCaptor<String> serviceInstanceIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<CreateSharedServiceInstances> shareToCaptor = ArgumentCaptor.forClass(CreateSharedServiceInstances.class);
    Set<Map<String, String>> s = new HashSet<>();
    s.add(Collections.singletonMap("guid", "space-guid-1"));
    s.add(Collections.singletonMap("guid", "space-guid-2"));
    CreateSharedServiceInstances expectedBody = new CreateSharedServiceInstances().setData(s);
    Set<String> sharedToRegions = new HashSet<>();
    sharedToRegions.add("org1 > space1");
    sharedToRegions.add("org2 > space2");
    ServiceInstanceResponse expectedResult = new ServiceInstanceResponse()
      .setServiceInstanceName("service-instance-name")
      .setType(SHARE)
      .setState(SUCCEEDED);

    ServiceInstanceResponse result = serviceInstances.shareServiceInstance("org > space", "service-instance-name", sharedToRegions);

    verify(serviceInstanceService).shareServiceInstanceToSpaceIds(serviceInstanceIdCaptor.capture(), shareToCaptor.capture());
    assertThat(serviceInstanceIdCaptor.getValue()).isEqualTo("service-instance-guid");
    assertThat(shareToCaptor.getValue()).isEqualToComparingFieldByFieldRecursively(expectedBody);
    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedResult);
  }

  @Test
  void shareServiceInstanceShouldShareManagedServiceInstanceOnlyIntoSpacesIntoWhichServiceInstanceHasNotBeenShared() {
    CloudFoundrySpace space1 = CloudFoundrySpace.builder()
      .id("space-guid-1")
      .name("some-space-1")
      .organization(cloudFoundryOrganization)
      .build();
    CloudFoundrySpace space2 = CloudFoundrySpace.builder()
      .id("space-guid-2")
      .name("some-space-2")
      .organization(cloudFoundryOrganization)
      .build();

    when(orgs.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findByName(any(), any()))
      .thenReturn(space1)
      .thenReturn(space2)
      .thenReturn(cloudFoundrySpace);
    when(configService.getConfigFeatureFlags())
      .thenReturn(singleton(new ConfigFeatureFlag().setName(SERVICE_INSTANCE_SHARING).setEnabled(true)));
    when(serviceInstanceService.all(any(), any())).thenReturn(createOsbServiceInstancePage());
    Set<Map<String, String>> alreadySharedTo = new HashSet<>();
    alreadySharedTo.add(Collections.singletonMap("guid", "space-guid-1"));
    alreadySharedTo.add(Collections.singletonMap("guid", "space-guid-3"));
    when(serviceInstanceService.getShareServiceInstanceSpaceIdsByServiceInstanceId(any()))
      .thenReturn(new SharedTo().setData(alreadySharedTo));
    ArgumentCaptor<String> servicePlanIdCaptor = ArgumentCaptor.forClass(String.class);
    Resource<ServicePlan> rsp = new Resource<>();
    rsp.setEntity(new ServicePlan().setServiceGuid("service-guid"));
    when(serviceInstanceService.findServicePlanByServicePlanId(any())).thenReturn(rsp);
    ArgumentCaptor<String> serviceIdCaptor = ArgumentCaptor.forClass(String.class);
    Resource<Service> r = new Resource<>();
    r.setEntity(new Service().setExtra("{\"shareable\": true}"));
    when(serviceInstanceService.findServiceByServiceId(any())).thenReturn(r);
    when(serviceInstanceService.shareServiceInstanceToSpaceIds(any(), any()))
      .thenReturn(new Response("url", 202, "reason", Collections.emptyList(), null));
    ArgumentCaptor<String> serviceInstanceIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<CreateSharedServiceInstances> shareToCaptor = ArgumentCaptor.forClass(CreateSharedServiceInstances.class);
    Set<Map<String, String>> s = singleton(Collections.singletonMap("guid", "space-guid-2"));
    CreateSharedServiceInstances expectedBody = new CreateSharedServiceInstances().setData(s);
    ServiceInstanceResponse expectedResult = new ServiceInstanceResponse()
      .setServiceInstanceName("service-instance-name")
      .setType(SHARE)
      .setState(SUCCEEDED);
    Set<String> sharingToRegions = new HashSet<>();
    sharingToRegions.add("org1 > space1");
    sharingToRegions.add("org2 > space2");

    ServiceInstanceResponse result = serviceInstances.shareServiceInstance("org > space", "service-instance-name", sharingToRegions);

    verify(serviceInstanceService).findServicePlanByServicePlanId(servicePlanIdCaptor.capture());
    assertThat(servicePlanIdCaptor.getValue()).isEqualTo("plan-guid");
    verify(serviceInstanceService).findServiceByServiceId(serviceIdCaptor.capture());
    assertThat(serviceIdCaptor.getValue()).isEqualTo("service-guid");
    verify(serviceInstanceService).shareServiceInstanceToSpaceIds(serviceInstanceIdCaptor.capture(), shareToCaptor.capture());
    assertThat(serviceInstanceIdCaptor.getValue()).isEqualTo("service-instance-guid");
    assertThat(shareToCaptor.getValue()).isEqualToComparingFieldByFieldRecursively(expectedBody);
    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedResult);
  }

  @Test
  void shareServiceInstanceShouldNotShareManagedServiceInstanceIfThereAreNoSpacesIntoWhichItHasNotBeenShared() {
    CloudFoundrySpace space1 = CloudFoundrySpace.builder()
      .id("space-guid-1")
      .name("some-space-1")
      .organization(cloudFoundryOrganization)
      .build();
    CloudFoundrySpace space2 = CloudFoundrySpace.builder()
      .id("space-guid-2")
      .name("some-space-2")
      .organization(cloudFoundryOrganization)
      .build();

    when(orgs.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findByName(any(), any()))
      .thenReturn(space1)
      .thenReturn(space2)
      .thenReturn(cloudFoundrySpace);
    when(configService.getConfigFeatureFlags())
      .thenReturn(singleton(new ConfigFeatureFlag().setName(SERVICE_INSTANCE_SHARING).setEnabled(true)));
    when(serviceInstanceService.all(any(), any())).thenReturn(createOsbServiceInstancePage());
    Set<Map<String, String>> alreadySharedTo = new HashSet<>();
    alreadySharedTo.add(Collections.singletonMap("guid", "space-guid-1"));
    alreadySharedTo.add(Collections.singletonMap("guid", "space-guid-2"));
    when(serviceInstanceService.getShareServiceInstanceSpaceIdsByServiceInstanceId(any()))
      .thenReturn(new SharedTo().setData(alreadySharedTo));
    Resource<ServicePlan> rsp = new Resource<>();
    rsp.setEntity(new ServicePlan().setServiceGuid("service-guid"));
    when(serviceInstanceService.findServicePlanByServicePlanId(any())).thenReturn(rsp);
    Resource<Service> r = new Resource<>();
    r.setEntity(new Service().setExtra("{\"shareable\": true}"));
    when(serviceInstanceService.findServiceByServiceId(any())).thenReturn(r);
    Set<Map<String, String>> s = singleton(Collections.singletonMap("guid", "space-guid-2"));
    ServiceInstanceResponse expectedResult = new ServiceInstanceResponse()
      .setServiceInstanceName("service-instance-name")
      .setType(SHARE)
      .setState(SUCCEEDED);
    Set<String> sharingToRegions = new HashSet<>();
    sharingToRegions.add("org1 > space1");
    sharingToRegions.add("org2 > space2");

    ServiceInstanceResponse result = serviceInstances.shareServiceInstance("org > space", "service-instance-name", sharingToRegions);

    verify(serviceInstanceService, never()).shareServiceInstanceToSpaceIds(any(), any());
    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedResult);
  }

  @Test
  void vetUnshareServiceArgumentsAndGetSharingRegionIdsShouldThrowExceptionWhenServiceInstanceNameIsBlank() {
    assertThrows(() -> serviceInstances.vetUnshareServiceArgumentsAndGetSharingSpaces(
      "",
      singleton("org1 > space1")),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): Please specify a name for the unsharing service instance");
  }

  @Test
  void vetUnshareServiceArgumentsAndGetSharingRegionIdsShouldThrowExceptionWhenRegionListIsEmpty() {
    assertThrows(() -> serviceInstances.vetUnshareServiceArgumentsAndGetSharingSpaces(
      "service-instance-name",
      null),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): Please specify a list of regions for unsharing 'service-instance-name'");
  }

  @Test
  void vetUnshareServiceArgumentsAndGetSharingRegionIdsShouldThrowExceptionWhenServiceSharingRegionDoesNotExist() {
    when(orgs.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findByName(any(), any())).thenReturn(null);

    assertThrows(() -> serviceInstances.vetUnshareServiceArgumentsAndGetSharingSpaces(
      "service-instance-name",
      singleton("org1 > space1")),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): Cannot find region 'org1 > space1' for unsharing");
  }

  @Test
  void vetUnshareServiceArgumentsAndGetSharingRegionIdsShouldThrowExceptionWhenServiceSharingShareToSpaceDoesNotExist() {
    when(orgs.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findByName(any(), eq("some")))
      .thenReturn(cloudFoundrySpace)
      .thenReturn(null);
    when(configService.getConfigFeatureFlags())
      .thenReturn(singleton(new ConfigFeatureFlag().setName(SERVICE_INSTANCE_SHARING).setEnabled(true)));
    when(serviceInstanceService.all(any(), any())).thenReturn(createOsbServiceInstancePage(USER_PROVIDED_SERVICE_INSTANCE));

    assertThrows(() -> serviceInstances.vetUnshareServiceArgumentsAndGetSharingSpaces(
      "service-instance-name",
      singleton("org1 > space1")),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): Cannot find region 'org1 > space1' for unsharing");
  }

  @Test
  void unshareServiceInstanceShouldSuccessfullyUnshareAnInstanceOnlyFromAUniqueListOfRegionIdsWhereItHadBeenShared() {
    CloudFoundrySpace space0 = CloudFoundrySpace.builder()
      .id("space-guid-0")
      .name("some-space-0")
      .organization(cloudFoundryOrganization)
      .build();
    CloudFoundrySpace space1 = CloudFoundrySpace.builder()
      .id("space-guid-1")
      .name("some-space-1")
      .organization(cloudFoundryOrganization)
      .build();
    CloudFoundrySpace space2 = CloudFoundrySpace.builder()
      .id("space-guid-2")
      .name("some-space-2")
      .organization(cloudFoundryOrganization)
      .build();

    when(orgs.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findByName(any(), any()))
      .thenReturn(space0)
      .thenReturn(space1)
      .thenReturn(space2);
    when(spaces.getSummaryServiceInstanceByNameAndSpace(any(), eq(space0)))
      .thenReturn(null);
    when(spaces.getSummaryServiceInstanceByNameAndSpace(any(), eq(space1)))
      .thenReturn(new SummaryServiceInstance()
      .setName("service-instance-name")
      .setGuid("service-instance-guid-1"));
    when(spaces.getSummaryServiceInstanceByNameAndSpace(any(), eq(space2)))
      .thenReturn(new SummaryServiceInstance()
        .setName("service-instance-name")
        .setGuid("service-instance-guid-2"));

    when(serviceInstanceService.unshareServiceInstanceFromSpaceId(any(), any()))
      .thenReturn(new Response("url", 202, "reason", Collections.emptyList(), null));
    Set<String> unshareFromRegions = new HashSet<>();
    unshareFromRegions.add("org0 > some-space-0");
    unshareFromRegions.add("org1 > some-space-1");
    unshareFromRegions.add("org2 > some-space-2");
    ServiceInstanceResponse expectedResult = new ServiceInstanceResponse()
      .setServiceInstanceName("service-instance-name")
      .setType(UNSHARE)
      .setState(SUCCEEDED);

    ServiceInstanceResponse result = serviceInstances.unshareServiceInstance("service-instance-name", unshareFromRegions);

    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedResult);
    verify(serviceInstanceService).unshareServiceInstanceFromSpaceId("service-instance-guid-1", "space-guid-1");
    verify(serviceInstanceService).unshareServiceInstanceFromSpaceId("service-instance-guid-2", "space-guid-2");
    verify(spaces).getSummaryServiceInstanceByNameAndSpace(eq("service-instance-name"), eq(space0));
    verify(spaces).getSummaryServiceInstanceByNameAndSpace(eq("service-instance-name"), eq(space1));
    verify(spaces).getSummaryServiceInstanceByNameAndSpace(eq("service-instance-name"), eq(space2));
  }

  @Test
  void getServiceInstanceShouldThrowAnExceptionWhenTheRegionCannotBeFound() {
    when(orgs.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findByName(any(), any())).thenReturn(null);

    assertThrows(() -> serviceInstances.getServiceInstance("org > space", "service-instance-name"),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): Cannot find region 'org > space'");
  }

  @Test
  void getServiceInstanceShouldReturnCloudFoundryOsbServiceInstance() {
    when(orgs.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findByName(any(), any())).thenReturn(cloudFoundrySpace);

    when(serviceInstanceService.all(any(), any())).thenReturn(createOsbServiceInstancePage());

    CloudFoundryServiceInstance results = serviceInstances.getServiceInstance("org > space", "new-service-instance-name");
    CloudFoundryServiceInstance expected = CloudFoundryServiceInstance.builder()
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
    when(orgs.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findByName(any(), any())).thenReturn(cloudFoundrySpace);

    when(serviceInstanceService.all(any(), any())).thenReturn(createEmptyOsbServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), any())).thenReturn(createUserProvidedServiceInstancePage());

    CloudFoundryServiceInstance results = serviceInstances.getServiceInstance("org > space", "up-service-instance-name");
    CloudFoundryServiceInstance expected = CloudFoundryServiceInstance.builder()
      .id("up-service-instance-guid")
      .type(USER_PROVIDED_SERVICE_INSTANCE.toString())
      .serviceInstanceName("up-service-instance-name")
      .status(SUCCEEDED.toString())
      .build();

    assertThat(results).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  void getOsbServiceInstanceShouldReturnAServiceInstanceWhenExactlyOneIsReturnedFromApi() {
    when(serviceInstanceService.all(any(), any())).thenReturn(createOsbServiceInstancePage());

    CloudFoundryServiceInstance service = serviceInstances.getOsbServiceInstance(cloudFoundrySpace, "service-instance-name");
    CloudFoundryServiceInstance expected = CloudFoundryServiceInstance.builder()
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
    when(serviceInstanceService.allUserProvided(any(), any())).thenReturn(createUserProvidedServiceInstancePage());

    CloudFoundryServiceInstance service = serviceInstances.getUserProvidedServiceInstance(cloudFoundrySpace, "up-service-instance-name");

    assertThat(service).isNotNull();
    CloudFoundryServiceInstance expected = CloudFoundryServiceInstance.builder()
      .id("up-service-instance-guid")
      .type(USER_PROVIDED_SERVICE_INSTANCE.toString())
      .serviceInstanceName("up-service-instance-name")
      .status(SUCCEEDED.toString())
      .build();
    assertThat(service).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  void getOsbServiceInstanceShouldThrowAnExceptionWhenMultipleServicesAreReturnedFromApi() {
    when(serviceInstanceService.all(any(), any())).thenReturn(createEmptyOsbServiceInstancePage());

    assertThat(serviceInstances.getOsbServiceInstance(cloudFoundrySpace, "new-service-instance-name")).isNull();
  }

  @Test
  void getOsbServiceInstanceShouldThrowAnExceptionWhenNoServicesAreReturnedFromApi() {
    Page<ServiceInstance> page = new Page<>();
    page.setTotalResults(2);
    page.setTotalPages(1);
    page.setResources(Arrays.asList(createServiceInstanceResource(), createServiceInstanceResource()));
    when(serviceInstanceService.all(any(), any())).thenReturn(page);

    assertThrows(() -> serviceInstances.getOsbServiceInstance(cloudFoundrySpace, "new-service-instance-name"),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): 2 service instances found with name 'new-service-instance-name' in space 'space', but expected only 1");
  }

  @Test
  void getOsbServiceInstanceShouldThrowExceptionWhenServiceNameIsBlank() {
    assertThrows(() -> serviceInstances.getOsbServiceInstance(cloudFoundrySpace, ""),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): Please specify a name for the service being sought");
  }

  @Test
  void destroyServiceInstanceShouldSucceedWhenNoServiceBindingsExist() {
    RetrofitError retrofitErrorNotFound = mock(RetrofitError.class);
    Response notFoundResponse = new Response("someUri", 404, "whynot", Collections.emptyList(), null);
    when(retrofitErrorNotFound.getResponse()).thenReturn(notFoundResponse);

    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(createOsbServiceInstancePage());
    when(serviceInstanceService.getBindingsForServiceInstance("service-instance-guid", null, null)).thenReturn(new Page<>());
    when(serviceInstanceService.destroyServiceInstance(any())).thenReturn(new Response("url", 202, "reason", Collections.emptyList(), null));

    ServiceInstanceResponse response = serviceInstances
      .destroyServiceInstance(cloudFoundrySpace, "new-service-instance-name");

    assertThat(response).isEqualTo(new ServiceInstanceResponse()
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

    when(serviceInstanceService.all(anyInt(), any())).thenReturn(createOsbServiceInstancePage());
    when(serviceInstanceService.getBindingsForServiceInstance(anyString(), anyInt(), any())).thenReturn(serviceBindingPage);
    when(serviceInstanceService.destroyServiceInstance(any())).thenThrow(destroyFailed);

    assertThrows(
      () -> serviceInstances.destroyServiceInstance(cloudFoundrySpace, "service-instance-name"),
      CloudFoundryApiException.class, "Cloud Foundry API returned with error(s): ");

    verify(serviceInstanceService, times(1)).destroyServiceInstance(any());
    verify(serviceInstanceService, never()).allUserProvided(any(), any());
  }

  @Test
  void destroyServiceInstanceShouldReturnSuccessWhenServiceInstanceDoesNotExist() {
    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(createEmptyOsbServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createEmptyUserProvidedServiceInstancePage());

    ServiceInstanceResponse response = serviceInstances.destroyServiceInstance(cloudFoundrySpace, "service-instance-name");

    assertThat(response).isEqualTo(new ServiceInstanceResponse()
      .setServiceInstanceName("service-instance-name")
      .setType(DELETE)
      .setState(LastOperation.State.NOT_FOUND)
    );
    verify(serviceInstanceService, never()).destroyServiceInstance(any());
  }

  @Test
  void destroyServiceInstanceShouldFailIfServiceBindingsExists() {
    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(createOsbServiceInstancePage());
    when(serviceInstanceService.getBindingsForServiceInstance("service-instance-guid", null, null))
      .thenReturn(Page.singleton(new ServiceBinding(), "service-binding-guid"));

    assertThrows(
      () -> serviceInstances.destroyServiceInstance(cloudFoundrySpace, "service-instance-name"),
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
      .destroyServiceInstance(cloudFoundrySpace, "new-service-instance-name");

    assertThat(response).isEqualTo(new ServiceInstanceResponse()
      .setServiceInstanceName("new-service-instance-name")
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
      () -> serviceInstances.destroyServiceInstance(cloudFoundrySpace, "service-instance-name"),
      CloudFoundryApiException.class, "Cloud Foundry API returned with error(s): ");

    verify(serviceInstanceService, times(1)).all(any(), anyListOf(String.class));
    verify(serviceInstanceService, times(1)).allUserProvided(any(), any());
    verify(serviceInstanceService, times(1)).getBindingsForUserProvidedServiceInstance(any(), anyInt(), anyListOf(String.class));
    verify(serviceInstanceService, times(1)).destroyUserProvidedServiceInstance(any());
    verify(serviceInstanceService, never()).destroyServiceInstance(any());
  }

  @Test
  void destroyUserProvidedServiceInstanceShouldFailIfServiceBindingsExists() {
    when(serviceInstanceService.all(any(), anyListOf(String.class))).thenReturn(createEmptyOsbServiceInstancePage());
    when(serviceInstanceService.allUserProvided(any(), anyListOf(String.class))).thenReturn(createUserProvidedServiceInstancePage());
    when(serviceInstanceService.getBindingsForUserProvidedServiceInstance("up-service-instance-guid", null, null))
      .thenReturn(Page.singleton(new ServiceBinding(), "up-service-instance-guid"));

    assertThrows(
      () -> serviceInstances.destroyServiceInstance(cloudFoundrySpace, "service-instance-name"),
      CloudFoundryApiException.class, "Cloud Foundry API returned with error(s): Unable to destroy service instance while 1 service binding(s) exist");

    verify(serviceInstanceService, times(1)).all(any(), anyListOf(String.class));
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

  private Resource<UserProvidedServiceInstance> createUserProvidedServiceInstanceResource() {
    UserProvidedServiceInstance userProvidedServiceInstance = new UserProvidedServiceInstance();
    userProvidedServiceInstance.setName("new-service-instance-name");
    Resource<UserProvidedServiceInstance> userProvidedServiceInstanceResource = new Resource<>();
    userProvidedServiceInstanceResource.setMetadata(new Resource.Metadata().setGuid("up-service-instance-guid"));
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
    serviceInstancePage
      .setTotalResults(0)
      .setTotalPages(1);
    return serviceInstancePage;
  }

  private Page<UserProvidedServiceInstance> createUserProvidedServiceInstancePage() {
    UserProvidedServiceInstance serviceInstance = new UserProvidedServiceInstance();
    serviceInstance
      .setName("up-service-instance-name")
      .setTags(singleton("spinnakerVersion-v000"));
    return Page.singleton(serviceInstance, "up-service-instance-guid");
  }

  private Page<UserProvidedServiceInstance> createEmptyUserProvidedServiceInstancePage() {
    Page<UserProvidedServiceInstance> userProvidedServiceInstancePage = new Page<>();
    userProvidedServiceInstancePage
      .setTotalResults(0)
      .setTotalPages(1);
    return userProvidedServiceInstancePage;
  }
}
