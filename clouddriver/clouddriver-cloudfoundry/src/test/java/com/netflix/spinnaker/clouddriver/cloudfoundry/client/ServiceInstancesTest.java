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

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.LastOperation.State.*;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.LastOperation.Type.*;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.VcapServiceInstance.Type.MANAGED_SERVICE_INSTANCE;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.VcapServiceInstance.Type.USER_PROVIDED_SERVICE_INSTANCE;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.utils.TestUtils.assertThrows;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ConfigService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ServiceInstanceService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceInstanceResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateSharedServiceInstances;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.FeatureFlag;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.LastOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Pagination;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Relationship;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ServiceCredentialBinding;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ServiceOffering;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ServicePlan;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.SharedTo;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ToOneRelationship;
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
    when(serviceInstanceService.findServiceOfferings(any(), any()))
        .thenReturn(
            Calls.response(
                Response.success(singletonPagination(offering("service1", "service-guid")))));

    when(serviceInstanceService.findServicePlans(any(), any(), any()))
        .thenReturn(
            Calls.response(
                Response.success(
                    singletonPagination(
                        servicePlan("ServicePlan1", "plan-guid", "service-guid")))));
  }

  @Test
  void shouldCreateServiceBindingWhenServiceExists() {
    CloudFoundryServerGroup cloudFoundryServerGroup =
        CloudFoundryServerGroup.builder()
            .account("some-account")
            .id("servergroup-id")
            .space(cloudFoundrySpace)
            .build();
    when(serviceInstanceService.createServiceBinding(any()))
        .thenAnswer(invocation -> Calls.response(Response.success(202, null)));

    serviceInstances.createServiceBinding(
        "service-instance-guid", cloudFoundryServerGroup.getId(), "service-name", emptyMap());

    verify(serviceInstanceService).createServiceBinding(any());
  }

  @Test
  void shouldCreateServiceBindingWhenUserProvidedServiceExists() {
    CloudFoundryServerGroup cloudFoundryServerGroup =
        CloudFoundryServerGroup.builder()
            .account("some-account")
            .id("servergroup-id")
            .space(cloudFoundrySpace)
            .build();
    when(serviceInstanceService.createServiceBinding(any()))
        .thenAnswer(invocation -> Calls.response(Response.success(202, null)));

    serviceInstances.createServiceBinding(
        "service-instance-guid", cloudFoundryServerGroup.getId(), "service-name", emptyMap());

    verify(serviceInstanceService).createServiceBinding(any());
  }

  @Test
  void shouldSucceedServiceBindingWhenServiceBindingExists() {
    CloudFoundryServerGroup cloudFoundryServerGroup =
        CloudFoundryServerGroup.builder()
            .account("some-account")
            .id("servergroup-id")
            .space(cloudFoundrySpace)
            .build();
    when(serviceInstanceService.createServiceBinding(any()))
        .thenReturn(
            Calls.response(
                Response.error(
                    500,
                    ResponseBody.create(
                        MediaType.get("application/json"),
                        "{\"error_code\": \"CF-ServiceBindingAppServiceTaken\", \"description\":\"already bound\"}"))));

    serviceInstances.createServiceBinding(
        "service-instance-guid", cloudFoundryServerGroup.getId(), "service-name", emptyMap());

    verify(serviceInstanceService).createServiceBinding(any());
  }

  @Test
  void shouldSuccessfullyCreateService() {
    when(serviceInstanceService.all(any(), any(), any(), eq("managed")))
        .thenAnswer(invocation -> Calls.response(Response.success(emptyPagination())));
    when(serviceInstanceService.createServiceInstance(any()))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        ResponseBody.create(MediaType.get("application/json"), "{}"))));

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
    when(serviceInstanceService.all(any(), any(), any(), eq("managed")))
        .thenAnswer(invocation -> Calls.response(Response.success(emptyPagination())));
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
    when(serviceInstanceService.findServicePlans(any(), any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(emptyPagination())));

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
    when(serviceInstanceService.all(any(), any(), any(), eq("managed")))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        singletonPagination(
                            managedServiceInstance(
                                "new-service-instance-name",
                                "service-instance-guid",
                                "plan-guid")))));
    when(serviceInstanceService.updateServiceInstance(any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(202, null)));

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
    when(serviceInstanceService.all(any(), any(), any(), eq("managed")))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        singletonPagination(
                            managedServiceInstance(
                                "new-service-instance-name",
                                "service-instance-guid",
                                "plan-guid")))));

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
    when(serviceInstanceService.all(any(), any(), any(), eq("managed")))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        singletonPagination(
                            managedServiceInstance(
                                "new-service-instance-name",
                                "service-instance-guid",
                                "plan-guid")))));
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
    ServiceInstance serviceInstance =
        managedServiceInstance("new-service-instance-name", "service-instance-guid", "plan-guid");

    when(serviceInstanceService.all(any(), any(), any(), eq("managed")))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(pagination(Arrays.asList(serviceInstance, serviceInstance)))));

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
    when(serviceInstanceService.all(any(), any(), any(), eq("user-provided")))
        .thenAnswer(invocation -> Calls.response(Response.success(emptyPagination())));
    when(serviceInstanceService.createUserProvidedServiceInstance(any()))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        userProvidedServiceInstance(
                            "new-up-service-instance-name", "up-service-instance-guid"))));

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
    when(serviceInstanceService.all(any(), any(), any(), eq("user-provided")))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        singletonPagination(
                            userProvidedServiceInstance(
                                "up-service-instance-name", "up-service-instance-guid")))));
    when(serviceInstanceService.updateUserProvidedServiceInstance(any(), any()))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        userProvidedServiceInstance(
                            "new-up-service-instance-name", "up-service-instance-guid"))));

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
    when(serviceInstanceService.all(any(), any(), any(), eq("user-provided")))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        singletonPagination(
                            userProvidedServiceInstance(
                                "up-service-instance-name", "up-service-instance-guid")))));

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
    when(configService.getFeatureFlag("service_instance_sharing"))
        .thenReturn(Calls.response(Response.success(enabledFeatureFlag())));

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
    when(serviceInstanceService.all(any(), any(), any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(emptyPagination())));

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
    when(serviceInstanceService.all(any(), any(), any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(emptyPagination())));

    assertThrows(
        () ->
            serviceInstances.getOsbServiceInstanceByRegion("org > space", "service-instance-name"),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): Cannot find region 'org > space'");
  }

  @Test
  void checkServiceShareableShouldThrowExceptionWhenManagedServiceSharingFlagIsNotPresent() {
    when(configService.getFeatureFlag("service_instance_sharing"))
        .thenAnswer(invocation -> Calls.response(Response.success(null)));

    assertThrows(
        () -> serviceInstances.checkServiceShareable("service-instance-name", null),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): 'service_instance_sharing' flag must be enabled in order to share services");
  }

  @Test
  void checkServiceShareableShouldThrowExceptionWhenManagedServiceSharingFlagIsSetToFalse() {
    when(configService.getFeatureFlag("service_instance_sharing"))
        .thenReturn(Calls.response(Response.success(disabledFeatureFlag())));

    assertThrows(
        () -> serviceInstances.checkServiceShareable("service-instance-name", null),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): 'service_instance_sharing' flag must be enabled in order to share services");
  }

  @Test
  void checkServiceShareableShouldThrowExceptionIfServicePlanNotFound() {
    when(configService.getFeatureFlag("service_instance_sharing"))
        .thenReturn(Calls.response(Response.success(enabledFeatureFlag())));
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
    when(configService.getFeatureFlag("service_instance_sharing"))
        .thenReturn(Calls.response(Response.success(enabledFeatureFlag())));
    when(serviceInstanceService.findServicePlanByServicePlanId(any()))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(servicePlan("plan1", "some-plan", "service-guid"))));
    when(serviceInstanceService.findServiceOfferingByGuid(any()))
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
    when(configService.getFeatureFlag("service_instance_sharing"))
        .thenReturn(Calls.response(Response.success(enabledFeatureFlag())));
    when(serviceInstanceService.findServicePlanByServicePlanId(any()))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(servicePlan("plan1", "some-plan", "service-guid"))));
    ServiceOffering unshareableOffering = offering("service1", "service-guid");
    unshareableOffering.setShareable(false);
    when(serviceInstanceService.findServiceOfferingByGuid(any()))
        .thenAnswer(invocation -> Calls.response(Response.success(unshareableOffering)));

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
    when(serviceInstanceService.all(any(), any(), any(), eq(null)))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        singletonPagination(
                            userProvidedServiceInstance(
                                "service-instance-name", "service-instance-guid")))));
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
    assertThat(shareToCaptor.getValue()).usingRecursiveComparison().isEqualTo(expectedBody);
    assertThat(result).usingRecursiveComparison().isEqualTo(expectedResult);
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
    when(configService.getFeatureFlag("service_instance_sharing"))
        .thenReturn(Calls.response(Response.success(enabledFeatureFlag())));
    when(serviceInstanceService.all(any(), any(), any(), any()))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        singletonPagination(
                            managedServiceInstance(
                                "service-instance-name", "service-instance-guid", "plan-guid")))));
    Set<Map<String, String>> alreadySharedTo = new HashSet<>();
    alreadySharedTo.add(Collections.singletonMap("guid", "space-guid-1"));
    alreadySharedTo.add(Collections.singletonMap("guid", "space-guid-3"));
    when(serviceInstanceService.getShareServiceInstanceSpaceIdsByServiceInstanceId(any()))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(new SharedTo().setData(alreadySharedTo))));
    ArgumentCaptor<String> servicePlanIdCaptor = ArgumentCaptor.forClass(String.class);
    when(serviceInstanceService.findServicePlanByServicePlanId(any()))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(servicePlan("plan1", "plan-guid", "service-guid"))));
    ArgumentCaptor<String> serviceIdCaptor = ArgumentCaptor.forClass(String.class);
    when(serviceInstanceService.findServiceOfferingByGuid(any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(offering("service1", "service-guid"))));
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
    verify(serviceInstanceService).findServiceOfferingByGuid(serviceIdCaptor.capture());
    assertThat(serviceIdCaptor.getValue()).isEqualTo("service-guid");
    verify(serviceInstanceService)
        .shareServiceInstanceToSpaceIds(serviceInstanceIdCaptor.capture(), shareToCaptor.capture());
    assertThat(serviceInstanceIdCaptor.getValue()).isEqualTo("service-instance-guid");
    assertThat(shareToCaptor.getValue()).usingRecursiveComparison().isEqualTo(expectedBody);
    assertThat(result).usingRecursiveComparison().isEqualTo(expectedResult);
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
    when(configService.getFeatureFlag("service_instance_sharing"))
        .thenReturn(Calls.response(Response.success(enabledFeatureFlag())));
    when(serviceInstanceService.all(any(), any(), any(), any()))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        singletonPagination(
                            managedServiceInstance(
                                "service-instance-name", "service-instance-guid", "plan-guid")))));
    Set<Map<String, String>> alreadySharedTo = new HashSet<>();
    alreadySharedTo.add(Collections.singletonMap("guid", "space-guid-1"));
    alreadySharedTo.add(Collections.singletonMap("guid", "space-guid-2"));
    when(serviceInstanceService.getShareServiceInstanceSpaceIdsByServiceInstanceId(any()))
        .thenAnswer(
            invocation ->
                Calls.response(Response.success(new SharedTo().setData(alreadySharedTo))));
    when(serviceInstanceService.findServicePlanByServicePlanId(any()))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(servicePlan("plan1", "plan-guid", "service-guid"))));
    when(serviceInstanceService.findServiceOfferingByGuid(any()))
        .thenAnswer(
            invocation -> Calls.response(Response.success(offering("service1", "service-guid"))));
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
    assertThat(result).usingRecursiveComparison().isEqualTo(expectedResult);
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
    when(configService.getFeatureFlag("service_instance_sharing"))
        .thenReturn(Calls.response(Response.success(enabledFeatureFlag())));
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

    assertThat(result).usingRecursiveComparison().isEqualTo(expectedResult);
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

    when(serviceInstanceService.all(any(), any(), any(), eq("managed")))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        singletonPagination(
                            managedServiceInstance(
                                "new-service-instance-name",
                                "service-instance-guid",
                                "plan-guid")))));

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

    assertThat(results).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void getServiceInstanceShouldReturnCloudFoundryUserProvidedServiceInstance() {
    when(organizations.findByName(any())).thenReturn(Optional.ofNullable(cloudFoundryOrganization));
    when(spaces.findSpaceByRegion(any())).thenReturn(Optional.of(cloudFoundrySpace));

    when(serviceInstanceService.all(any(), any(), any(), eq("managed")))
        .thenAnswer(invocation -> Calls.response(Response.success(emptyPagination())));
    when(serviceInstanceService.all(any(), any(), any(), eq("user-provided")))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        singletonPagination(
                            userProvidedServiceInstance(
                                "up-service-instance-name", "up-service-instance-guid")))));

    CloudFoundryServiceInstance results =
        serviceInstances.getServiceInstance("org > space", "up-service-instance-name");
    CloudFoundryServiceInstance expected =
        CloudFoundryServiceInstance.builder()
            .id("up-service-instance-guid")
            .type(USER_PROVIDED_SERVICE_INSTANCE.toString())
            .serviceInstanceName("up-service-instance-name")
            .status(SUCCEEDED.toString())
            .build();

    assertThat(results).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void getOsbServiceInstanceShouldReturnAServiceInstanceWhenExactlyOneIsReturnedFromApi() {
    when(serviceInstanceService.all(any(), any(), any(), eq("managed")))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        singletonPagination(
                            managedServiceInstance(
                                "new-service-instance-name",
                                "service-instance-guid",
                                "plan-guid")))));

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
    assertThat(service).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void getUserProvidedServiceInstanceShouldReturnAServiceInstanceWhenExactlyOneIsReturnedFromApi() {
    when(serviceInstanceService.all(any(), any(), any(), eq("user-provided")))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        singletonPagination(
                            userProvidedServiceInstance(
                                "up-service-instance-name", "up-service-instance-guid")))));

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
    assertThat(service).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void getServiceInstanceShouldReturnAServiceInstanceWithStatusWhenExactlyOneIsReturnedFromApi() {
    ServiceInstance failedInstance =
        managedServiceInstance("up-service-instance-name", "up-service-instance-guid", "plan-guid");
    failedInstance.getLastOperation().setState("FAILED");
    failedInstance.getLastOperation().setDescription("Custom description");
    when(serviceInstanceService.all(any(), any(), any(), eq("managed")))
        .thenAnswer(
            invocation -> Calls.response(Response.success(singletonPagination(failedInstance))));

    CloudFoundryServiceInstance service =
        serviceInstances.getOsbServiceInstance(cloudFoundrySpace, "up-service-instance-name");

    assertThat(service).isNotNull();
    CloudFoundryServiceInstance expected =
        CloudFoundryServiceInstance.builder()
            .id("up-service-instance-guid")
            .planId("plan-guid")
            .type(MANAGED_SERVICE_INSTANCE.toString())
            .serviceInstanceName("up-service-instance-name")
            .status(FAILED.toString())
            .lastOperationDescription("Custom description")
            .build();
    assertThat(service).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void getOsbServiceInstanceShouldThrowAnExceptionWhenMultipleServicesAreReturnedFromApi() {
    when(serviceInstanceService.all(any(), any(), any(), eq("managed")))
        .thenAnswer(invocation -> Calls.response(Response.success(emptyPagination())));

    assertThat(
            serviceInstances.getOsbServiceInstance(cloudFoundrySpace, "new-service-instance-name"))
        .isNull();
  }

  @Test
  void getOsbServiceInstanceShouldThrowAnExceptionWhenNoServicesAreReturnedFromApi() {
    ServiceInstance serviceInstance =
        managedServiceInstance("new-service-instance-name", "service-instance-guid", "plan-guid");
    when(serviceInstanceService.all(any(), any(), any(), eq("managed")))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(pagination(Arrays.asList(serviceInstance, serviceInstance)))));

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
    when(serviceInstanceService.all(any(), any(), any(), eq(null)))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        singletonPagination(
                            managedServiceInstance(
                                "new-service-instance-name",
                                "service-instance-guid",
                                "plan-guid")))));
    when(serviceInstanceService.getServiceBindings(
            any(), eq("service-instance-guid"), any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(emptyPagination())));
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
    verify(serviceInstanceService, times(1)).all(any(), any(), any(), eq(null));
    verify(serviceInstanceService, times(1)).destroyServiceInstance(any());
  }

  @Test
  void destroyServiceInstanceShouldThrowExceptionWhenDeleteServiceInstanceFails() {
    when(serviceInstanceService.all(any(), any(), any(), eq(null)))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        singletonPagination(
                            managedServiceInstance(
                                "service-instance-name", "service-instance-guid", "plan-guid")))));
    when(serviceInstanceService.getServiceBindings(any(), any(), any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(emptyPagination())));
    when(serviceInstanceService.destroyServiceInstance(any()))
        .thenReturn(
            Calls.response(
                Response.error(500, ResponseBody.create(MediaType.get("application/json"), "{}"))));

    assertThrows(
        () -> serviceInstances.destroyServiceInstance(cloudFoundrySpace, "service-instance-name"),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): ");

    verify(serviceInstanceService, times(1)).destroyServiceInstance(any());
  }

  @Test
  void destroyServiceInstanceShouldReturnSuccessWhenServiceInstanceDoesNotExist() {
    when(serviceInstanceService.all(any(), any(), any(), eq(null)))
        .thenAnswer(invocation -> Calls.response(Response.success(emptyPagination())));

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
    when(serviceInstanceService.all(any(), any(), any(), eq(null)))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        singletonPagination(
                            managedServiceInstance(
                                "service-instance-name", "service-instance-guid", "plan-guid")))));
    when(serviceInstanceService.getServiceBindings(
            any(), eq("service-instance-guid"), any(), any()))
        .thenReturn(
            Calls.response(
                Response.success(
                    singletonPagination(serviceCredentialBinding("service-binding-guid")))));

    assertThrows(
        () -> serviceInstances.destroyServiceInstance(cloudFoundrySpace, "service-instance-name"),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): Unable to destroy service instance while 1 service binding(s) exist");

    verify(serviceInstanceService, never()).destroyServiceInstance(any());
  }

  // Note: in v3, destroy is unified for managed and user-provided instances behind a single
  // 'all(..., type=null)' + 'destroyServiceInstance(guid)' code path, so this covers the same
  // destroy logic exercised above but against a user-provided instance.
  @Test
  void destroyServiceInstanceShouldSucceedForUserProvidedInstanceWithNoServiceBindings() {
    when(serviceInstanceService.all(any(), any(), any(), eq(null)))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Response.success(
                        singletonPagination(
                            userProvidedServiceInstance(
                                "new-service-instance-name", "up-service-instance-guid")))));
    when(serviceInstanceService.getServiceBindings(
            any(), eq("up-service-instance-guid"), any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(emptyPagination())));
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
    verify(serviceInstanceService, times(1)).destroyServiceInstance(any());
  }

  private ServiceOffering offering(String name, String guid) {
    ServiceOffering offering = new ServiceOffering();
    offering.setGuid(guid);
    offering.setName(name);
    offering.setShareable(true);
    return offering;
  }

  private ServicePlan servicePlan(String name, String guid, String serviceOfferingGuid) {
    ServicePlan plan = new ServicePlan();
    plan.setGuid(guid);
    plan.setName(name);
    plan.setRelationships(
        Collections.singletonMap(
            "service_offering", new ToOneRelationship(new Relationship(serviceOfferingGuid))));
    return plan;
  }

  private ServiceInstance managedServiceInstance(String name, String guid, String planGuid) {
    ServiceInstance instance = new ServiceInstance();
    instance.setGuid(guid);
    instance.setName(name);
    instance.setType("managed");
    instance.setTags(Collections.singletonList("spinnakerVersion-v001"));
    ServiceInstance.LastOperation lastOperation = new ServiceInstance.LastOperation();
    lastOperation.setType("create");
    lastOperation.setState("SUCCEEDED");
    instance.setLastOperation(lastOperation);
    instance.setRelationships(
        Collections.singletonMap(
            "service_plan", new ToOneRelationship(new Relationship(planGuid))));
    return instance;
  }

  private ServiceInstance userProvidedServiceInstance(String name, String guid) {
    ServiceInstance instance = new ServiceInstance();
    instance.setGuid(guid);
    instance.setName(name);
    instance.setType("user-provided");
    instance.setTags(Collections.singletonList("spinnakerVersion-v000"));
    return instance;
  }

  private ServiceCredentialBinding serviceCredentialBinding(String guid) {
    ServiceCredentialBinding binding = new ServiceCredentialBinding();
    binding.setGuid(guid);
    binding.setType("app");
    return binding;
  }

  private <R> Pagination<R> pagination(List<R> resources) {
    Pagination<R> pagination = new Pagination<>();
    Pagination.Details details = new Pagination.Details();
    details.setTotalPages(1);
    pagination.setPagination(details);
    pagination.setResources(resources);
    return pagination;
  }

  private <R> Pagination<R> singletonPagination(R resource) {
    return pagination(Collections.singletonList(resource));
  }

  private <R> Pagination<R> emptyPagination() {
    return pagination(Collections.emptyList());
  }

  private FeatureFlag enabledFeatureFlag() {
    FeatureFlag flag = new FeatureFlag();
    flag.setName("service_instance_sharing");
    flag.setEnabled(true);
    return flag;
  }

  private FeatureFlag disabledFeatureFlag() {
    FeatureFlag flag = new FeatureFlag();
    flag.setName("service_instance_sharing");
    flag.setEnabled(false);
    return flag;
  }
}
