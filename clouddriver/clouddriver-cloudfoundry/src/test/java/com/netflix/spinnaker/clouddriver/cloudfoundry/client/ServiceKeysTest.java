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

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.LastOperation.State.SUCCEEDED;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.LastOperation.Type.CREATE_SERVICE_KEY;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.LastOperation.Type.DELETE_SERVICE_KEY;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.utils.TestUtils.assertThrows;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ServiceInstanceService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ServiceKeyService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceKeyResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateServiceCredentialBinding;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Pagination;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Relationship;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ServiceCredentialBinding;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ServiceCredentialBindingDetails;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ToOneRelationship;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import io.vavr.collection.HashMap;
import java.util.Map;
import java.util.Optional;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import retrofit2.Response;
import retrofit2.mock.Calls;

class ServiceKeysTest {
  private String serviceInstanceName = "service-instance";
  private String serviceInstanceId = "service-instance-guid";
  private String serviceKeyName = "service-key";
  private String serviceKeyId = "service-key-guid";
  private CloudFoundryOrganization cloudFoundryOrganization =
      CloudFoundryOrganization.builder().id("org-guid").name("org").build();
  private CloudFoundrySpace cloudFoundrySpace =
      CloudFoundrySpace.builder()
          .id("space-guid")
          .name("space")
          .organization(cloudFoundryOrganization)
          .build();
  private ServiceInstanceService serviceInstanceService = mock(ServiceInstanceService.class);
  private ServiceKeyService serviceKeyService = mock(ServiceKeyService.class);
  private Spaces spaces = mock(Spaces.class);
  private ServiceKeys serviceKeys = new ServiceKeys(serviceKeyService, spaces);
  private Map<String, Object> credentials =
      HashMap.of(
              "username", "name1",
              "password", "xxwer3",
              "details", singleton("detail"))
          .toJavaMap();

  @Test
  void createServiceKeyShouldReturnSuccessWhenServiceKeyIsCreated() {
    when(spaces.getServiceInstanceByNameAndSpace(any(), any()))
        .thenReturn(
            CloudFoundryServiceInstance.builder()
                .name(serviceKeyName)
                .id(serviceInstanceId)
                .build());
    ServiceCredentialBinding binding = new ServiceCredentialBinding();
    binding.setGuid(serviceKeyId);
    binding.setName(serviceKeyName);
    binding.setType("key");
    when(serviceKeyService.createServiceKey(any()))
        .thenReturn(Calls.response(Response.success(binding)));
    when(serviceKeyService.getServiceKeyDetails(serviceKeyId))
        .thenReturn(
            Calls.response(
                Response.success(
                    new ServiceCredentialBindingDetails().setCredentials(credentials))));
    CreateServiceCredentialBinding requestBody =
        CreateServiceCredentialBinding.forServiceKey(serviceKeyName, serviceInstanceId);

    ServiceKeyResponse expectedResults = new ServiceKeyResponse();
    expectedResults.setServiceKey(credentials);
    expectedResults.setType(CREATE_SERVICE_KEY);
    expectedResults.setState(SUCCEEDED);
    expectedResults.setServiceInstanceName(serviceInstanceName);
    expectedResults.setServiceKeyName(serviceKeyName);
    when(serviceKeyService.getServiceKey(any(), any(), any(), any()))
        .thenReturn(Calls.response(Response.success(createEmptyServiceKeyPagination())));

    ServiceKeyResponse results =
        serviceKeys.createServiceKey(cloudFoundrySpace, serviceInstanceName, serviceKeyName);

    assertThat(results).usingRecursiveComparison().isEqualTo(expectedResults);
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
            + serviceInstanceName
            + "' not found in region '"
            + cloudFoundrySpace.getRegion()
            + "'");
    verify(spaces).getServiceInstanceByNameAndSpace(eq(serviceInstanceName), eq(cloudFoundrySpace));
    verify(serviceKeyService, never()).createServiceKey(any());
  }

  @Test
  void createServiceKeyShouldThrowExceptionWhenServiceKeyReturnsNotFound() {
    when(spaces.getServiceInstanceByNameAndSpace(any(), any()))
        .thenReturn(
            CloudFoundryServiceInstance.builder()
                .name(serviceKeyName)
                .id(serviceInstanceId)
                .build());
    CreateServiceCredentialBinding requestBody =
        CreateServiceCredentialBinding.forServiceKey(serviceKeyName, serviceInstanceId);
    when(serviceKeyService.getServiceKey(any(), any(), any(), any()))
        .thenReturn(Calls.response(Response.success(createEmptyServiceKeyPagination())));

    when(serviceKeyService.createServiceKey(any()))
        .thenReturn(
            Calls.response(
                Response.error(404, ResponseBody.create(MediaType.get("application/json"), "{}"))));

    assertThrows(
        () -> serviceKeys.createServiceKey(cloudFoundrySpace, serviceInstanceName, serviceKeyName),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): Service key '"
            + serviceKeyName
            + "' could not be created for service instance '"
            + serviceInstanceName
            + "' in region '"
            + cloudFoundrySpace.getRegion()
            + "'");
    verify(spaces).getServiceInstanceByNameAndSpace(eq(serviceInstanceName), eq(cloudFoundrySpace));
    verify(serviceKeyService).createServiceKey(requestBody);
  }

  @Test
  void createServiceKeyShouldSucceedWhenServiceKeyAlreadyExists() {
    when(spaces.getServiceInstanceByNameAndSpace(any(), any()))
        .thenReturn(
            CloudFoundryServiceInstance.builder()
                .name(serviceKeyName)
                .id(serviceInstanceId)
                .build());
    when(serviceKeyService.getServiceKey(any(), any(), any(), any()))
        .thenReturn(
            Calls.response(
                Response.success(createServiceKeyPagination(serviceKeyName, serviceKeyId))));
    when(serviceKeyService.getServiceKeyDetails(serviceKeyId))
        .thenReturn(
            Calls.response(
                Response.success(
                    new ServiceCredentialBindingDetails().setCredentials(credentials))));

    ServiceKeyResponse expectedResults = new ServiceKeyResponse();
    expectedResults.setServiceKey(credentials);
    expectedResults.setType(CREATE_SERVICE_KEY);
    expectedResults.setState(SUCCEEDED);
    expectedResults.setServiceInstanceName(serviceInstanceName);
    expectedResults.setServiceKeyName(serviceKeyName);

    ServiceKeyResponse serviceKeyResponse =
        serviceKeys.createServiceKey(cloudFoundrySpace, serviceInstanceName, serviceKeyName);

    assertThat(serviceKeyResponse).isEqualTo(expectedResults);
    verify(serviceKeyService, never()).createServiceKey(any());
  }

  @Test
  void getServiceKeyShouldSucceed() {
    String serviceKeyGuid = "service-key-guid";
    ServiceCredentialBinding expectedBinding =
        createServiceKeyBinding("service-key", serviceKeyGuid);
    when(serviceKeyService.getServiceKey(any(), any(), any(), any()))
        .thenReturn(Calls.response(Response.success(singletonPagination(expectedBinding))));

    Optional<ServiceCredentialBinding> serviceKeyResults =
        serviceKeys.getServiceKey("service-instance-guid", "service-key");

    assertThat(serviceKeyResults.isPresent()).isTrue();
    assertThat(serviceKeyResults.get()).isEqualTo(expectedBinding);
    verify(serviceKeyService)
        .getServiceKey(any(), eq("service-instance-guid"), eq("service-key"), eq("key"));
  }

  @Test
  void getServiceKeyShouldReturnEmptyOptionalWhenNotPresent() {
    when(serviceKeyService.getServiceKey(any(), any(), any(), any()))
        .thenReturn(Calls.response(Response.success(createEmptyServiceKeyPagination())));

    Optional<ServiceCredentialBinding> serviceKeyResults =
        serviceKeys.getServiceKey("service-instance-guid", "service-key");

    assertThat(serviceKeyResults.isPresent()).isFalse();
    verify(serviceKeyService)
        .getServiceKey(any(), eq("service-instance-guid"), eq("service-key"), eq("key"));
  }

  @Test
  void deleteServiceKeyShouldSucceedWhenServiceKeyIsDeleted() {
    when(spaces.getServiceInstanceByNameAndSpace(any(), any()))
        .thenReturn(
            CloudFoundryServiceInstance.builder()
                .name(serviceInstanceName)
                .id(serviceInstanceId)
                .build());
    when(serviceKeyService.getServiceKey(any(), any(), any(), any()))
        .thenReturn(
            Calls.response(
                Response.success(createServiceKeyPagination(serviceKeyName, serviceKeyId))));
    when(serviceKeyService.deleteServiceKey(any()))
        .thenReturn(Calls.response(Response.success(202, null)));
    ServiceKeyResponse expectedResponse =
        (ServiceKeyResponse)
            new ServiceKeyResponse()
                .setServiceKeyName(serviceKeyName)
                .setType(DELETE_SERVICE_KEY)
                .setState(SUCCEEDED)
                .setServiceInstanceName(serviceInstanceName);

    ServiceKeyResponse response =
        serviceKeys.deleteServiceKey(cloudFoundrySpace, serviceInstanceName, serviceKeyName);

    assertThat(response).isEqualTo(expectedResponse);
    verify(spaces).getServiceInstanceByNameAndSpace(eq(serviceInstanceName), eq(cloudFoundrySpace));
    verify(serviceKeyService)
        .getServiceKey(any(), eq(serviceInstanceId), eq(serviceKeyName), eq("key"));
    verify(serviceKeyService).deleteServiceKey(serviceKeyId);
  }

  @Test
  void deleteServiceKeyShouldSucceedWhenServiceKeyDoesNotExist() {
    when(spaces.getServiceInstanceByNameAndSpace(any(), any()))
        .thenReturn(
            CloudFoundryServiceInstance.builder()
                .name(serviceInstanceName)
                .id(serviceInstanceId)
                .build());
    when(serviceKeyService.getServiceKey(any(), any(), any(), any()))
        .thenReturn(Calls.response(Response.success(createEmptyServiceKeyPagination())));
    ServiceKeyResponse expectedResponse =
        (ServiceKeyResponse)
            new ServiceKeyResponse()
                .setServiceKeyName(serviceKeyName)
                .setType(DELETE_SERVICE_KEY)
                .setState(SUCCEEDED)
                .setServiceInstanceName(serviceInstanceName);

    ServiceKeyResponse response =
        serviceKeys.deleteServiceKey(cloudFoundrySpace, serviceInstanceName, serviceKeyName);

    assertThat(response).isEqualTo(expectedResponse);
    verify(spaces).getServiceInstanceByNameAndSpace(eq(serviceInstanceName), eq(cloudFoundrySpace));
    verify(serviceKeyService)
        .getServiceKey(any(), eq(serviceInstanceId), eq(serviceKeyName), eq("key"));
    verify(serviceKeyService, never()).deleteServiceKey(any());
  }

  @Test
  void deleteServiceKeyShouldThrowExceptionWhenServiceDoesNotExistInSpace() {
    when(spaces.getServiceInstanceByNameAndSpace(any(), any())).thenReturn(null);

    assertThrows(
        () -> serviceKeys.deleteServiceKey(cloudFoundrySpace, serviceInstanceName, serviceKeyName),
        CloudFoundryApiException.class,
        "Cloud Foundry API returned with error(s): Cannot find service 'service-instance' in region 'org > space'");
    verify(spaces).getServiceInstanceByNameAndSpace(eq(serviceInstanceName), eq(cloudFoundrySpace));
    verify(serviceKeyService, never()).getServiceKey(any(), any(), any(), any());
    verify(serviceKeyService, never()).deleteServiceKey(any());
  }

  private ServiceCredentialBinding createServiceKeyBinding(String name, String guid) {
    ServiceCredentialBinding binding = new ServiceCredentialBinding();
    binding.setGuid(guid);
    binding.setName(name);
    binding.setType("key");
    binding.setRelationships(
        Map.of("service_instance", new ToOneRelationship(new Relationship(serviceInstanceId))));
    return binding;
  }

  private Pagination<ServiceCredentialBinding> createServiceKeyPagination(
      String serviceKeyName, String serviceKeyId) {
    return singletonPagination(createServiceKeyBinding(serviceKeyName, serviceKeyId));
  }

  private Pagination<ServiceCredentialBinding> singletonPagination(
      ServiceCredentialBinding binding) {
    Pagination<ServiceCredentialBinding> pagination = new Pagination<>();
    pagination.setPagination(new Pagination.Details().setTotalPages(1));
    pagination.setResources(java.util.Collections.singletonList(binding));
    return pagination;
  }

  private Pagination<ServiceCredentialBinding> createEmptyServiceKeyPagination() {
    Pagination<ServiceCredentialBinding> pagination = new Pagination<>();
    pagination.setPagination(new Pagination.Details().setTotalPages(1));
    pagination.setResources(java.util.Collections.emptyList());
    return pagination;
  }
}
