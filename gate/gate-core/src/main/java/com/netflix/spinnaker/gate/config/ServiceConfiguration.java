/*
 * Copyright 2015 Netflix, Inc.
 * Copyright 2023 Apple, Inc.
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
 */

package com.netflix.spinnaker.gate.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import retrofit.Endpoint;
import retrofit.Endpoints;

@Getter
@Setter
@Component
@ConfigurationProperties
public class ServiceConfiguration {
  private static final String DYNAMIC_ENDPOINTS = "dynamicEndpoints";

  private List<String> healthCheckableServices = new ArrayList<>();
  private Map<String, Service> services = new LinkedHashMap<>();
  private Map<String, Service> integrations = new LinkedHashMap<>();

  @PostConstruct
  void postConstruct() {
    // this check is done in a @PostConstruct to avoid Spring's list merging in
    // @ConfigurationProperties (vs. overriding)
    if (CollectionUtils.isEmpty(healthCheckableServices)) {
      healthCheckableServices =
          List.of("orca", "clouddriver", "echo", "igor", "flex", "front50", "mahe", "mine", "keel");
    }
  }

  @Nullable
  public Service getService(@Nonnull String name) {
    if (services.containsKey(name)) {
      return services.get(name);
    }
    return integrations.get(name);
  }

  @Nonnull
  public Endpoint getServiceEndpoint(@Nonnull String serviceName) {
    return getServiceEndpoint(serviceName, null);
  }

  @Nonnull
  public Endpoint getServiceEndpoint(@Nonnull String serviceName, @Nullable String dynamicName) {
    Service service = getService(serviceName);
    if (service == null) {
      throw new IllegalArgumentException("Unknown service " + serviceName);
    }
    if (dynamicName == null) {
      String serviceBaseUrl = service.getBaseUrl();
      return Endpoints.newFixedEndpoint(serviceBaseUrl);
    }
    Map<String, Object> config = service.getConfig();
    if (!config.containsKey(DYNAMIC_ENDPOINTS)) {
      throw new IllegalArgumentException(
          String.format("Unknown dynamicEndpoint %s for service %s", dynamicName, serviceName));
    }
    @SuppressWarnings("unchecked")
    Map<String, String> dynamicEndpoints = (Map<String, String>) config.get(DYNAMIC_ENDPOINTS);
    String dynamicEndpoint = dynamicEndpoints.get(dynamicName);
    return Endpoints.newFixedEndpoint(dynamicEndpoint);
  }
}
