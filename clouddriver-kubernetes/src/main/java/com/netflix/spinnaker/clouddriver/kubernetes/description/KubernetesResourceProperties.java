/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.description;

import static com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler.DeployPriority.WORKLOAD_CONTROLLER_PRIORITY;

import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.kubernetes.config.CustomKubernetesResource;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.CustomKubernetesHandlerFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class KubernetesResourceProperties {
  private final KubernetesHandler handler;
  private final boolean versioned;

  public KubernetesResourceProperties(KubernetesHandler handler, boolean versioned) {
    this.handler = handler;
    this.versioned = versioned;
  }

  public static KubernetesResourceProperties fromCustomResource(
      CustomKubernetesResource customResource) {
    String deployPriority = customResource.getDeployPriority();
    int deployPriorityValue;
    if (Strings.isNullOrEmpty(deployPriority)) {
      deployPriorityValue = WORKLOAD_CONTROLLER_PRIORITY.getValue();
    } else {
      try {
        deployPriorityValue = Integer.parseInt(deployPriority);
      } catch (NumberFormatException e) {
        deployPriorityValue =
            KubernetesHandler.DeployPriority.fromString(deployPriority).getValue();
      }
    }

    KubernetesHandler handler =
        CustomKubernetesHandlerFactory.create(
            KubernetesKind.fromString(customResource.getKubernetesKind()),
            SpinnakerKind.fromString(customResource.getSpinnakerKind()),
            customResource.isVersioned(),
            deployPriorityValue);

    return new KubernetesResourceProperties(handler, customResource.isVersioned());
  }
}
