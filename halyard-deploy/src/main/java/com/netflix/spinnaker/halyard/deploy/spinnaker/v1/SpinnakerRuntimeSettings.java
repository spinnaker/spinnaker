/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings.SlimServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class SpinnakerRuntimeSettings {
  @JsonPropertyOrder(alphabetic = true)
  protected Map<Type, ServiceSettings> services = new HashMap<>();

  public SpinnakerRuntimeSettings newServiceOverrides(List<Type> overrideServiceEndpoints) {
    SpinnakerRuntimeSettings serviceOverrides = new SpinnakerRuntimeSettings();
    for (Type type : overrideServiceEndpoints) {
      if (this.serviceIsEnabled(type)) {
        serviceOverrides.setServiceSettings(
            type.getBaseType(), this.getServiceSettings(type).withOnlyBaseUrl());
      }
    }
    return serviceOverrides;
  }

  @JsonIgnore
  public Map<Type, ServiceSettings> getAllServiceSettings() {
    return Collections.unmodifiableMap(services);
  }

  public void setServiceSettings(Type type, ServiceSettings settings) {
    services.put(type, settings);
  }

  public ServiceSettings getServiceSettings(SpinnakerService service) {
    return getServiceSettings(service.getType());
  }

  public ServiceSettings getServiceSettings(Type type) {
    return services.get(type);
  }

  public boolean serviceIsEnabled(Type type) {
    return services.containsKey(type) && services.get(type).getEnabled();
  }

  public SlimRuntimeSettings slim() {
    SlimRuntimeSettings srs = new SlimRuntimeSettings();
    services.forEach((t, service) -> srs.services.put(t, service.slim()));
    return srs;
  }

  @Data
  public static class SlimRuntimeSettings {
    @JsonPropertyOrder(alphabetic = true)
    private Map<Type, SlimServiceSettings> services = new HashMap<>();
  }
}
