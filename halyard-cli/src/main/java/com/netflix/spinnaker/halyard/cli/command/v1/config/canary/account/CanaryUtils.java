/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary.account;

import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;

public class CanaryUtils {

  public static AbstractCanaryServiceIntegration getServiceIntegrationByClass(
      Canary canary, Class<? extends AbstractCanaryServiceIntegration> serviceIntegrationClass) {
    return canary.getServiceIntegrations().stream()
        .filter(s -> serviceIntegrationClass.isAssignableFrom(s.getClass()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Canary service integration of type "
                        + serviceIntegrationClass.getSimpleName()
                        + " not found."));
  }

  public static AbstractCanaryServiceIntegration getServiceIntegrationByName(
      Canary canary, String currentDeployment, String serviceIntegrationName, boolean noValidate) {
    if (canary == null) {
      canary =
          new OperationHandler<Canary>()
              .setFailureMesssage("Failed to get canary.")
              .setOperation(Daemon.getCanary(currentDeployment, !noValidate))
              .get();
    }

    return canary.getServiceIntegrations().stream()
        .filter(s -> s.getName().equals(serviceIntegrationName.toLowerCase()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Canary service integration " + serviceIntegrationName + " not found."));
  }
}
