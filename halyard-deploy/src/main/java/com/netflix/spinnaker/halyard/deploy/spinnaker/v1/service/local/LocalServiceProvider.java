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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local;

import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerServiceProvider;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class LocalServiceProvider extends SpinnakerServiceProvider<DeploymentDetails> {
  public LocalService getLocalService(SpinnakerService.Type type) {
    return getLocalService(type, Object.class);
  }

  public <S> LocalService<S> getLocalService(SpinnakerService.Type type, Class<S> clazz) {
    Field serviceField = getField(type.getCanonicalName() + "service");
    if (serviceField == null) {
      return null;
    }

    serviceField.setAccessible(true);
    try {
      return (LocalService<S>) serviceField.get(this);
    } catch (IllegalAccessException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Can't access service field for " + type + ": " + e.getMessage());
    } finally {
      serviceField.setAccessible(false);
    }
  }

  // TODO(lwander) move from string to something like RemoteAction
  public abstract String getInstallCommand(
      DeploymentDetails deploymentDetails,
      GenerateService.ResolvedConfiguration resolvedConfiguration,
      Map<String, String> installCommands);

  public String getPrepCommand(DeploymentDetails deploymentDetails, List<String> prepCommands) {
    return "";
  }

  public List<LocalService> getLocalServices(List<SpinnakerService.Type> serviceTypes) {
    return getFieldsOfType(LocalService.class).stream()
        .filter(s -> s != null && serviceTypes.contains(s.getService().getType()))
        .collect(Collectors.toList());
  }
}
