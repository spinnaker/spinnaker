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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ServiceKeyService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceKeyResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import lombok.RequiredArgsConstructor;

import java.util.*;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.collectPageResources;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.safelyCall;

@RequiredArgsConstructor
public class ServiceKeys {
  private final ServiceKeyService api;
  private final Spaces spaces;

  public ServiceKeyResponse createServiceKey(CloudFoundrySpace space, String serviceInstanceName, String serviceKeyName) {
    return Optional.ofNullable(spaces.getServiceInstanceByNameAndSpace(serviceInstanceName, space))
      .map(ssi -> getServiceKey(ssi.getId(), serviceKeyName)
        .map(Resource::getEntity)
        .map(ServiceKey::getCredentials)
        .orElseGet(() -> safelyCall(() ->
            api.createServiceKey(new CreateServiceKey().setName(serviceKeyName).setServiceInstanceGuid(ssi.getId()))
          )
            .map(Resource::getEntity)
            .map(ServiceCredentials::getCredentials)
            .orElseThrow(() -> new CloudFoundryApiException("Service key '" + serviceKeyName +
              "' could not be created for service instance '" + serviceInstanceName + "' in region '" +
              space.getRegion() + "'"))
        ))
      .map(serviceCredentials -> (ServiceKeyResponse) new ServiceKeyResponse()
        .setServiceKeyName(serviceKeyName)
        .setServiceKey(serviceCredentials)
        .setType(LastOperation.Type.CREATE_SERVICE_KEY)
        .setState(LastOperation.State.SUCCEEDED)
        .setServiceInstanceName(serviceInstanceName)
      )
      .orElseThrow(() -> new CloudFoundryApiException("Service instance '" + serviceInstanceName +
        "' not found in region '" + space.getRegion() + "'"));
  }

  public ServiceKeyResponse deleteServiceKey(CloudFoundrySpace space, String serviceInstanceName, String serviceKeyName) {
    return Optional.ofNullable(spaces.getServiceInstanceByNameAndSpace(serviceInstanceName, space))
      .map(ssi ->
        getServiceKey(ssi.getId(), serviceKeyName)
          .map(serviceKeyResource ->
            safelyCall(() ->
              api.deleteServiceKey(serviceKeyResource.getMetadata().getGuid())
            )))
      .map(_a -> (ServiceKeyResponse) new ServiceKeyResponse()
        .setServiceKeyName(serviceKeyName)
        .setType(LastOperation.Type.CREATE_SERVICE_KEY)
        .setState(LastOperation.State.SUCCEEDED)
        .setServiceInstanceName(serviceInstanceName)
      )
      .orElseThrow(() -> new CloudFoundryApiException("Cannot find service '" + serviceInstanceName + "' in region '" + space.getRegion() + "'"));
  }

  Optional<Resource<ServiceKey>> getServiceKey(String serviceInstanceId, String serviceKeyName) {
    List<String> queryParams = Arrays.asList("service_instance_guid:" + serviceInstanceId, "name:" + serviceKeyName);
    return collectPageResources("service key by service instance id and service key name", pg -> api.getServiceKey(pg, queryParams))
      .stream()
      .findFirst();
  }
}
