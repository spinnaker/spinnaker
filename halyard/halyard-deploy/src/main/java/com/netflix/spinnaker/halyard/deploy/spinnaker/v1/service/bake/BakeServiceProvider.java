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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.bake;

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

public abstract class BakeServiceProvider extends SpinnakerServiceProvider<DeploymentDetails> {
  public BakeService getBakeService(SpinnakerService.Type type) {
    return getBakeService(type, Object.class);
  }

  public <S> BakeService<S> getBakeService(SpinnakerService.Type type, Class<S> clazz) {
    Field serviceField = getField(type.getCanonicalName() + "service");
    if (serviceField == null) {
      return null;
    }

    serviceField.setAccessible(true);
    try {
      return (BakeService<S>) serviceField.get(this);
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
      Map<String, String> installCommands,
      String startupCommand);

  public List<BakeService> getPrioritizedBakeableServices(
      List<SpinnakerService.Type> serviceTypes) {
    List<BakeService> result =
        getFieldsOfType(BakeService.class).stream()
            .filter(s -> serviceTypes.contains(s.getService().getType()))
            .collect(Collectors.toList());

    result.sort((a, b) -> b.getPriority().compareTo(a.getPriority()));
    return result;
  }
}
