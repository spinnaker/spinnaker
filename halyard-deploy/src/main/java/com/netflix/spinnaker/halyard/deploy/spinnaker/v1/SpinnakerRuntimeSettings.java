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

import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import lombok.Data;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Data
public class SpinnakerRuntimeSettings {
  protected Services services = new Services();

  // For serialization
  public SpinnakerRuntimeSettings() {}

  @Data
  public class Services {
    ServiceSettings clouddriver;
    ServiceSettings clouddriverBootstrap;
    ServiceSettings consulClient;
    ServiceSettings deck;
    ServiceSettings echo;
    ServiceSettings fiat;
    ServiceSettings front50;
    ServiceSettings gate;
    ServiceSettings igor;
    ServiceSettings orca;
    ServiceSettings orcaBootstrap;
    ServiceSettings rosco;
    ServiceSettings redis;
    ServiceSettings redisBootstrap;
    ServiceSettings monitoringDaemon;
    ServiceSettings vaultClient;
  }

  public Map<SpinnakerService.Type, ServiceSettings> getAllServiceSettings() {
    return Arrays.stream(Services.class.getDeclaredFields()).reduce(
        new HashMap<>(),
        (map, field) -> {
          if (!ServiceSettings.class.isAssignableFrom(field.getType())) {
            return map;
          }

          SpinnakerService.Type type = SpinnakerService.Type.fromCanonicalName(field.getName());
          ServiceSettings settings;
          try {
            settings = (ServiceSettings) field.get(services);
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          }

          if (settings != null) {
            map.put(type, settings);
          }

          return map;
        },
        (map1, map2) -> {
          map1.putAll(map2);
          return map1;
        }
    );
  }

  public void setServiceSettings(SpinnakerService.Type type, ServiceSettings settings) {
    Field serviceField = getServiceField(type.getCanonicalName());
    serviceField.setAccessible(true);
    try {
      serviceField.set(services, settings);
    } catch (IllegalAccessException e) {
      throw new HalException(Problem.Severity.FATAL, "Can't access service field for " + type.toString() + ": " + e.getMessage());
    } finally {
      serviceField.setAccessible(false);
    }
  }

  public ServiceSettings getServiceSettings(SpinnakerService service) {
    return getServiceSettings(service.getCanonicalName());
  }

  private ServiceSettings getServiceSettings(String name) {
    Field serviceField = getServiceField(name);
    serviceField.setAccessible(true);
    try {
      return (ServiceSettings) serviceField.get(services);
    } catch (IllegalAccessException e) {
      throw new HalException(Problem.Severity.FATAL, "Can't access service field for " + name + ": " + e.getMessage());
    } finally {
      serviceField.setAccessible(false);
    }
  }

  private Field getServiceField(String name) {
    String reducedName = name.replace("-", "").replace("_", "");

    Optional<Field> matchingField = Arrays.stream(Services.class.getDeclaredFields())
        .filter(f -> f.getName().equalsIgnoreCase(reducedName))
        .findFirst();

    return matchingField.orElseThrow(() -> new HalException(Problem.Severity.FATAL, "Unknown service " + reducedName));
  }
}
