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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed;

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerServiceProvider;
import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

public abstract class DistributedServiceProvider<T extends Account>
    extends SpinnakerServiceProvider<AccountDeploymentDetails<T>> {
  public DistributedService getDeployableService(SpinnakerService.Type type) {
    return getDeployableService(type, Object.class);
  }

  public <S> DistributedService<S, T> getDeployableService(
      SpinnakerService.Type type, Class<S> clazz) {
    Field serviceField = getField(type.getCanonicalName() + "service");
    if (serviceField == null) {
      return null;
    }

    serviceField.setAccessible(true);
    try {
      return (DistributedService<S, T>) serviceField.get(this);
    } catch (IllegalAccessException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Can't access service field for " + type + ": " + e.getMessage());
    } finally {
      serviceField.setAccessible(false);
    }
  }

  /** @return the highest priority services first. */
  public List<DistributedService> getPrioritizedDistributedServices(
      List<SpinnakerService.Type> serviceTypes) {
    List<DistributedService> result =
        getFieldsOfType(DistributedService.class).stream()
            .filter(d -> serviceTypes.contains(d.getService().getType()))
            .collect(Collectors.toList());

    result.sort((d1, d2) -> d2.getDeployPriority().compareTo(d1.getDeployPriority()));
    return result;
  }
}
