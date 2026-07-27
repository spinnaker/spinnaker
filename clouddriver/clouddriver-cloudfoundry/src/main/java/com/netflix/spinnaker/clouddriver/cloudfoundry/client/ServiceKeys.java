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

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.collectPages;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.safelyCall;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ServiceKeyService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceKeyResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateServiceCredentialBinding;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.LastOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ServiceCredentialBinding;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ServiceCredentialBindingDetails;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ServiceKeys {
  private final ServiceKeyService api;
  private final Spaces spaces;

  public ServiceKeyResponse createServiceKey(
      CloudFoundrySpace space, String serviceInstanceName, String serviceKeyName) {
    return Optional.ofNullable(spaces.getServiceInstanceByNameAndSpace(serviceInstanceName, space))
        .map(
            ssi ->
                getServiceKey(ssi.getId(), serviceKeyName)
                    .map(this::getServiceKeyCredentials)
                    .orElseGet(
                        () ->
                            safelyCall(
                                    () ->
                                        api.createServiceKey(
                                            CreateServiceCredentialBinding.forServiceKey(
                                                serviceKeyName, ssi.getId())))
                                .map(this::getServiceKeyCredentials)
                                .orElseThrow(
                                    () ->
                                        new CloudFoundryApiException(
                                            "Service key '"
                                                + serviceKeyName
                                                + "' could not be created for service instance '"
                                                + serviceInstanceName
                                                + "' in region '"
                                                + space.getRegion()
                                                + "'"))))
        .map(
            serviceCredentials ->
                (ServiceKeyResponse)
                    new ServiceKeyResponse()
                        .setServiceKeyName(serviceKeyName)
                        .setServiceKey(serviceCredentials)
                        .setType(LastOperation.Type.CREATE_SERVICE_KEY)
                        .setState(LastOperation.State.SUCCEEDED)
                        .setServiceInstanceName(serviceInstanceName))
        .orElseThrow(
            () ->
                new CloudFoundryApiException(
                    "Service instance '"
                        + serviceInstanceName
                        + "' not found in region '"
                        + space.getRegion()
                        + "'"));
  }

  public ServiceKeyResponse deleteServiceKey(
      CloudFoundrySpace space, String serviceInstanceName, String serviceKeyName) {
    return Optional.ofNullable(spaces.getServiceInstanceByNameAndSpace(serviceInstanceName, space))
        .map(
            ssi ->
                getServiceKey(ssi.getId(), serviceKeyName)
                    .map(
                        serviceKeyBinding ->
                            safelyCall(() -> api.deleteServiceKey(serviceKeyBinding.getGuid()))))
        .map(
            _a ->
                (ServiceKeyResponse)
                    new ServiceKeyResponse()
                        .setServiceKeyName(serviceKeyName)
                        .setType(LastOperation.Type.CREATE_SERVICE_KEY)
                        .setState(LastOperation.State.SUCCEEDED)
                        .setServiceInstanceName(serviceInstanceName))
        .orElseThrow(
            () ->
                new CloudFoundryApiException(
                    "Cannot find service '"
                        + serviceInstanceName
                        + "' in region '"
                        + space.getRegion()
                        + "'"));
  }

  Optional<ServiceCredentialBinding> getServiceKey(
      String serviceInstanceId, String serviceKeyName) {
    return collectPages(
            "service key by service instance id and service key name",
            pg -> api.getServiceKey(pg, serviceInstanceId, serviceKeyName, "key"))
        .stream()
        .findFirst();
  }

  private Map<String, Object> getServiceKeyCredentials(ServiceCredentialBinding binding) {
    return safelyCall(() -> api.getServiceKeyDetails(binding.getGuid()))
        .map(ServiceCredentialBindingDetails::getCredentials)
        .orElse(null);
  }
}
