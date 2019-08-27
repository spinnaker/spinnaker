/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler.DeployPriority.WORKLOAD_CONTROLLER_PRIORITY;

import com.netflix.spinnaker.clouddriver.kubernetes.config.CustomKubernetesResource;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.KubernetesUnversionedArtifactConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.KubernetesVersionedArtifactConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.CustomKubernetesHandlerFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

@Getter
@Slf4j
public class KubernetesResourceProperties {
  private final KubernetesHandler handler;
  private final boolean versioned;
  private final KubernetesVersionedArtifactConverter versionedConverter =
      new KubernetesVersionedArtifactConverter();
  private final KubernetesUnversionedArtifactConverter unversionedConverter =
      new KubernetesUnversionedArtifactConverter();

  public KubernetesResourceProperties(KubernetesHandler handler, boolean versioned) {
    this.handler = handler;
    this.versioned = versioned;
  }

  public static KubernetesResourceProperties fromCustomResource(
      CustomKubernetesResource customResource, KubernetesKindRegistry kindRegistry) {
    String deployPriority = customResource.getDeployPriority();
    int deployPriorityValue;
    if (StringUtils.isEmpty(deployPriority)) {
      deployPriorityValue = WORKLOAD_CONTROLLER_PRIORITY.getValue();
    } else {
      try {
        deployPriorityValue = Integer.valueOf(deployPriority);
      } catch (NumberFormatException e) {
        deployPriorityValue =
            KubernetesHandler.DeployPriority.fromString(deployPriority).getValue();
      }
    }

    KubernetesKind kubernetesKind = KubernetesKind.fromString(customResource.getKubernetesKind());
    kindRegistry.getOrRegisterKind(
        kubernetesKind,
        () -> {
          log.info(
              "Dynamically registering {}, (namespaced: {})",
              kubernetesKind.toString(),
              customResource.isNamespaced());
          return new KubernetesKindProperties(
              kubernetesKind, customResource.isNamespaced(), false, true);
        });

    KubernetesHandler handler =
        CustomKubernetesHandlerFactory.create(
            kubernetesKind,
            KubernetesSpinnakerKindMap.SpinnakerKind.fromString(customResource.getSpinnakerKind()),
            customResource.isVersioned(),
            deployPriorityValue);

    return new KubernetesResourceProperties(handler, customResource.isVersioned());
  }
}
