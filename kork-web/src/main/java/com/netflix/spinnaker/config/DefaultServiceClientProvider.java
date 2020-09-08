/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.config;

import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.client.ServiceClientFactory;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import java.util.List;
import org.springframework.stereotype.Component;

/** Provider that returns a suitable service client capable of making http calls. */
@NonnullByDefault
@Component
public class DefaultServiceClientProvider implements ServiceClientProvider {

  private final List<ServiceClientFactory> serviceClientFactories;
  private final ObjectMapper objectMapper;

  public DefaultServiceClientProvider(
      List<ServiceClientFactory> serviceClientFactories, ObjectMapper objectMapper) {
    this.serviceClientFactories = serviceClientFactories;
    this.objectMapper = objectMapper;
  }

  @Override
  public <T> T getService(Class<T> type, ServiceEndpoint serviceEndpoint) {
    ServiceClientFactory serviceClientFactory = findProvider(type, serviceEndpoint);
    return serviceClientFactory.create(type, serviceEndpoint, objectMapper);
  }

  @Override
  public <T> T getService(
      Class<T> type, ServiceEndpoint serviceEndpoint, ObjectMapper objectMapper) {
    ServiceClientFactory serviceClientFactory = findProvider(type, serviceEndpoint);
    return serviceClientFactory.create(type, serviceEndpoint, objectMapper);
  }

  private ServiceClientFactory findProvider(Class<?> type, ServiceEndpoint service) {
    return serviceClientFactories.stream()
        .filter(provider -> provider.supports(type, service))
        .findFirst()
        .orElseThrow(
            () ->
                new SystemException(
                    format("No service client provider found for url (%s)", service.getBaseUrl())));
  }
}
