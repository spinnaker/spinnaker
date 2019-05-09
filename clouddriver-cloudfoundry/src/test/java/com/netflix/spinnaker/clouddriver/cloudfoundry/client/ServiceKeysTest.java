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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ServiceInstanceService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ServiceKeyService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceKeyResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import io.vavr.collection.HashMap;
import org.junit.jupiter.api.Test;
import retrofit.RetrofitError;
import retrofit.client.Response;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.SUCCEEDED;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.CREATE_SERVICE_KEY;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.DELETE_SERVICE_KEY;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.utils.TestUtils.assertThrows;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

class ServiceKeysTest {
  private String serviceInstanceName = "service-instance";
  private String serviceInstanceId = "service-instance-guid";
  private String serviceKeyName = "service-key";
  private String serviceKeyId = "service-key-guid";
  private CloudFoundryOrganization cloudFoundryOrganization = CloudFoundryOrganization.builder()
    .id("org-guid")
    .name("org")
    .build();
  private CloudFoundrySpace cloudFoundrySpace = CloudFoundrySpace.builder()
    .id("space-guid")
    .name("space")
    .organization(cloudFoundryOrganization)
    .build();
  private ServiceInstanceService serviceInstanceService = mock(ServiceInstanceService.class);
  private ServiceKeyService serviceKeyService = mock(ServiceKeyService.class);
  private Spaces spaces = mock(Spaces.class);
  private ServiceKeys serviceKeys = new ServiceKeys(serviceKeyService, spaces);
  private Map<String, Object> credentials = HashMap.of(
    "username", "name1",
    "password", "xxwer3",
    "details", singleton("detail")
  ).toJavaMap();

  {
    when(serviceInstanceService.findService(any(), anyListOf(String.class)))
      .thenReturn(Page.singleton(new Service().setLabel("service1"), "service-guid"));

    when(serviceInstanceService.findServicePlans(any(), anyListOf(String.class)))
      .thenReturn(Page.singleton(new ServicePlan().setName("ServicePlan1"), "plan-guid"));
  }

  @Test
  void createServiceKeyShouldReturnSuccessWhenServiceKeyIsCreated() {
    when(spaces.getServiceInstanceByNameAndSpace(any(), any())).thenReturn(CloudFoundryServiceInstance.builder()
      .name(serviceKeyName)
      .id(serviceInstanceId)
      .build());
    ServiceCredentials serviceCredentials = new ServiceCredentials().setCredentials(credentials);
    Resource<ServiceCredentials> resource = new Resource<>();
    resource.setEntity(serviceCredentials);
    when(serviceKeyService.createServiceKey(any())).thenReturn(resource);
    CreateServiceKey requestBody = new CreateServiceKey()
      .setName(serviceKeyName)
      .setServiceInstanceGuid(serviceInstanceId);

    ServiceKeyResponse expectedResults = new ServiceKeyResponse();
    expectedResults.setServiceKey(credentials);
    expectedResults.setType(CREATE_SERVICE_KEY);
    expectedResults.setState(SUCCEEDED);
    expectedResults.setServiceInstanceName(serviceInstanceName);
    expectedResults.setServiceKeyName(serviceKeyName);
    when(serviceKeyService.getServiceKey(any(), any())).thenReturn(createEmptyServiceKeyPage());

    ServiceKeyResponse results = serviceKeys.createServiceKey(cloudFoundrySpace, serviceInstanceName, serviceKeyName);

    assertThat(results).isEqualToComparingFieldByFieldRecursively(expectedResults);
    verify(spaces).getServiceInstanceByNameAndSpace(eq(serviceInstanceName), eq(cloudFoundrySpace));
    verify(serviceKeyService).createServiceKey(eq(requestBody));
  }

  @Test
  void createServiceKeyShouldThrowExceptionWhenServiceNameDoesNotExistInSpace() {
    when(spaces.getServiceInstanceByNameAndSpace(any(), any())).thenReturn(null);

    assertThrows(
      () -> serviceKeys.createServiceKey(cloudFoundrySpace, serviceInstanceName, serviceKeyName),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): Service instance '"
        + serviceInstanceName + "' not found in region '" + cloudFoundrySpace.getRegion() + "'");
    verify(spaces).getServiceInstanceByNameAndSpace(eq(serviceInstanceName), eq(cloudFoundrySpace));
    verify(serviceKeyService, never()).createServiceKey(any());
  }

  @Test
  void createServiceKeyShouldThrowExceptionWhenServiceKeyReturnsNotFound() {
    when(spaces.getServiceInstanceByNameAndSpace(any(), any())).thenReturn(CloudFoundryServiceInstance.builder()
      .name(serviceKeyName)
      .id(serviceInstanceId)
      .build());
    CreateServiceKey requestBody = new CreateServiceKey()
      .setName(serviceKeyName)
      .setServiceInstanceGuid(serviceInstanceId);
    when(serviceKeyService.getServiceKey(any(), any())).thenReturn(createEmptyServiceKeyPage());
    RetrofitError retrofitErrorNotFound = mock(RetrofitError.class);
    Response notFoundResponse = new Response("someUri", 404, "whynot", Collections.emptyList(), null);
    when(retrofitErrorNotFound.getResponse()).thenReturn(notFoundResponse);
    when(serviceKeyService.createServiceKey(any())).thenThrow(retrofitErrorNotFound);

    assertThrows(
      () -> serviceKeys.createServiceKey(cloudFoundrySpace, serviceInstanceName, serviceKeyName),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): Service key '" + serviceKeyName + "' could not be created for service instance '"
        + serviceInstanceName + "' in region '" + cloudFoundrySpace.getRegion() + "'");
    verify(spaces).getServiceInstanceByNameAndSpace(eq(serviceInstanceName), eq(cloudFoundrySpace));
    verify(serviceKeyService).createServiceKey(requestBody);
  }

  @Test
  void createServiceKeyShouldSucceedWhenServiceKeyAlreadyExists() {
    when(spaces.getServiceInstanceByNameAndSpace(any(), any())).thenReturn(CloudFoundryServiceInstance.builder()
      .name(serviceKeyName)
      .id(serviceInstanceId)
      .build());
    when(serviceKeyService.getServiceKey(any(), any())).thenReturn(createServiceKeyPage(serviceKeyName, serviceKeyId));

    ServiceKeyResponse expectedResults = new ServiceKeyResponse();
    expectedResults.setServiceKey(credentials);
    expectedResults.setType(CREATE_SERVICE_KEY);
    expectedResults.setState(SUCCEEDED);
    expectedResults.setServiceInstanceName(serviceInstanceName);
    expectedResults.setServiceKeyName(serviceKeyName);

    ServiceKeyResponse serviceKeyResponse = serviceKeys.createServiceKey(cloudFoundrySpace, serviceInstanceName, serviceKeyName);

    assertThat(serviceKeyResponse).isEqualTo(expectedResults);
    verify(serviceKeyService, never()).createServiceKey(any());
  }

  @Test
  void getServiceKeyShouldSucceed() {
    ServiceKey serviceKey = new ServiceKey()
      .setName("service-key")
      .setCredentials(singletonMap("username", "user1"))
      .setServiceInstanceGuid("service-instance-guid");
    String serviceKeyGuid = "service-key-guid";
    Page<ServiceKey> page = Page.singleton(serviceKey, serviceKeyGuid);
    when(serviceKeyService.getServiceKey(any(), any())).thenReturn(page);
    Resource<ServiceKey> expectedResource = new Resource<ServiceKey>()
      .setEntity(serviceKey)
      .setMetadata(new Resource.Metadata().setGuid(serviceKeyGuid));

    Optional<Resource<ServiceKey>> serviceKeyResults = serviceKeys.getServiceKey("service-instance-guid", "service-key");

    assertThat(serviceKeyResults.isPresent()).isTrue();
    assertThat(serviceKeyResults.get()).isEqualTo(expectedResource);
    verify(serviceKeyService).getServiceKey(any(), eq(Arrays.asList("service_instance_guid:service-instance-guid", "name:service-key")));
  }

  @Test
  void getServiceKeyShouldReturnEmptyOptionalWhenNotPresent() {
    Page<ServiceKey> page = new Page<ServiceKey>()
      .setTotalResults(0)
      .setTotalPages(1);
    when(serviceKeyService.getServiceKey(any(), any())).thenReturn(page);

    Optional<Resource<ServiceKey>> serviceKeyResults = serviceKeys.getServiceKey("service-instance-guid", "service-key");

    assertThat(serviceKeyResults.isPresent()).isFalse();
    verify(serviceKeyService).getServiceKey(any(), eq(Arrays.asList("service_instance_guid:service-instance-guid", "name:service-key")));
  }

  @Test
  void deleteServiceKeyShouldSucceedWhenServiceKeyIsDeleted() {
    when(spaces.getServiceInstanceByNameAndSpace(any(), any())).thenReturn(CloudFoundryServiceInstance.builder()
      .name(serviceInstanceName)
      .id(serviceInstanceId)
      .build());
    when(serviceKeyService.getServiceKey(any(), any())).thenReturn(createServiceKeyPage(serviceKeyName, serviceKeyId));
    when(serviceKeyService.deleteServiceKey(any())).thenReturn(new Response("url", 202, "reason", Collections.emptyList(), null));
    ServiceKeyResponse expectedResponse = (ServiceKeyResponse) new ServiceKeyResponse()
      .setServiceKeyName(serviceKeyName)
      .setType(DELETE_SERVICE_KEY)
      .setState(SUCCEEDED)
      .setServiceInstanceName(serviceInstanceName);

    ServiceKeyResponse response = serviceKeys.deleteServiceKey(cloudFoundrySpace, serviceInstanceName, serviceKeyName);

    assertThat(response).isEqualTo(expectedResponse);
    verify(spaces).getServiceInstanceByNameAndSpace(eq(serviceInstanceName), eq(cloudFoundrySpace));
    verify(serviceKeyService).getServiceKey(any(), eq(Arrays.asList("service_instance_guid:service-instance-guid", "name:service-key")));
    verify(serviceKeyService).deleteServiceKey(serviceKeyId);
  }

  @Test
  void deleteServiceKeyShouldSucceedWhenServiceKeyDoesNotExist() {
    when(spaces.getServiceInstanceByNameAndSpace(any(), any())).thenReturn(CloudFoundryServiceInstance.builder()
      .name(serviceInstanceName)
      .id(serviceInstanceId)
      .build());
    when(serviceKeyService.getServiceKey(any(), any())).thenReturn(createEmptyServiceKeyPage());
    ServiceKeyResponse expectedResponse = (ServiceKeyResponse) new ServiceKeyResponse()
      .setServiceKeyName(serviceKeyName)
      .setType(DELETE_SERVICE_KEY)
      .setState(SUCCEEDED)
      .setServiceInstanceName(serviceInstanceName);

    ServiceKeyResponse response = serviceKeys.deleteServiceKey(cloudFoundrySpace, serviceInstanceName, serviceKeyName);

    assertThat(response).isEqualTo(expectedResponse);
    verify(spaces).getServiceInstanceByNameAndSpace(eq(serviceInstanceName), eq(cloudFoundrySpace));
    verify(serviceKeyService).getServiceKey(any(), eq(Arrays.asList("service_instance_guid:service-instance-guid", "name:service-key")));
    verify(serviceKeyService, never()).deleteServiceKey(any());
  }

  @Test
  void deleteServiceKeyShouldThrowExceptionWhenServiceDoesNotExistInSpace() {
    when(spaces.getServiceInstanceByNameAndSpace(any(), any())).thenReturn(null);

    assertThrows(() -> serviceKeys.deleteServiceKey(cloudFoundrySpace, serviceInstanceName, serviceKeyName),
      CloudFoundryApiException.class,
      "Cloud Foundry API returned with error(s): Cannot find service 'service-instance' in region 'org > space'");
    verify(spaces).getServiceInstanceByNameAndSpace(eq(serviceInstanceName), eq(cloudFoundrySpace));
    verify(serviceKeyService, never()).getServiceKey(any(), any());
    verify(serviceKeyService, never()).deleteServiceKey(any());
  }

  private Page<ServiceKey> createServiceKeyPage(String serviceKeyName, String serviceKeyId) {
    return Page.singleton(new ServiceKey().setName(serviceKeyName).setCredentials(credentials), serviceKeyId);
  }

  private Page<ServiceKey> createEmptyServiceKeyPage() {
    return new Page<ServiceKey>()
      .setTotalPages(1)
      .setTotalResults(0);
  }
}
