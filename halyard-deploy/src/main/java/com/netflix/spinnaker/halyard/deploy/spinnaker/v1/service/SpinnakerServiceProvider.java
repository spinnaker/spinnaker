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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
abstract public class SpinnakerServiceProvider<D extends DeploymentDetails> {
  public SpinnakerRuntimeSettings buildRuntimeSettings(DeploymentConfiguration deploymentConfiguration) {
    SpinnakerRuntimeSettings endpoints = new SpinnakerRuntimeSettings();
    for (SpinnakerService service : getServices()) {
      if (service != null && service.isInBillOfMaterials(deploymentConfiguration)) {
        log.info("Building service settings entry for " + service.getServiceName());
        ServiceSettings settings = service.getDefaultServiceSettings(deploymentConfiguration);
        settings.mergePreferThis(service.buildServiceSettings(deploymentConfiguration));
        endpoints.setServiceSettings(service.getType(), settings);
      }
    }
    return endpoints;
  }

  public abstract RemoteAction clean(D details, SpinnakerRuntimeSettings runtimeSettings);

  protected Field getField(String name) {
    String reducedName = reduceFieldName(name);

    Optional<Field> matchingField = Arrays.stream(this.getClass().getDeclaredFields())
        .filter(f -> f.getName().equalsIgnoreCase(reducedName))
        .findFirst();

    return matchingField.orElse(null);
  }

  protected <T> List<T> getFieldsOfType(Class<T> clazz) {
    return Arrays.stream(this.getClass().getDeclaredFields())
        .filter(f -> clazz.isAssignableFrom(f.getType()))
        .map(f -> {
          f.setAccessible(true);
          try {
            return (T) f.get(this);
          } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to read service " + f.getName());
          }
        }).collect(Collectors.toList());
  }

  public List<SpinnakerService> getServices() {
    return getFieldsOfType(SpinnakerService.class)
        .stream()
        .filter(s -> s != null)
        .collect(Collectors.toList());
  }

  SpinnakerService getSpinnakerService(SpinnakerService.Type type) {
    Field serviceField = getField(type.getCanonicalName() + "service");
    if (serviceField == null) {
      return null;
    }

    serviceField.setAccessible(true);
    try {
      return (SpinnakerService) serviceField.get(this);
    } catch (IllegalAccessException e) {
      throw new HalException(Problem.Severity.FATAL, "Can't access service field for " + type + ": " + e.getMessage());
    } finally {
      serviceField.setAccessible(false);
    }
  }

  private static String reduceFieldName(String name) {
    return name.replace("-", "").replace("_", "").toLowerCase();
  }
}
