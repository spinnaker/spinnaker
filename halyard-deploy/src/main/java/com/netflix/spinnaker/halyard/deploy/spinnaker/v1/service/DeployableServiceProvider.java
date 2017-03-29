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

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;

import java.lang.reflect.Field;
import java.util.List;

abstract public class DeployableServiceProvider<T extends Account> extends SpinnakerServiceProvider {
  public DeployableService getDeployableService(SpinnakerService.Type type) {
    return getDeployableService(type, Object.class);
  }

  public <S> DeployableService<S, T> getDeployableService(SpinnakerService.Type type, Class<S> clazz) {
    Field serviceField = getField(type.getCanonicalName() + "service");
    if (serviceField == null) {
      return null;
    }

    serviceField.setAccessible(true);
    try {
      return (DeployableService<S, T>) serviceField.get(this);
    } catch (IllegalAccessException e) {
      throw new HalException(Problem.Severity.FATAL, "Can't access service field for " + type + ": " + e.getMessage());
    } finally {
      serviceField.setAccessible(false);
    }
  }

  /**
   * @return the highest priority services first.
   */
  public List<DeployableService> getPrioritizedDeployableServices() {
    List<DeployableService> result = getFieldsOfType(DeployableService.class);
    result.sort((d1, d2) -> d2.getDeployPriority().compareTo(d1.getDeployPriority()));
    return result;
  }
}
