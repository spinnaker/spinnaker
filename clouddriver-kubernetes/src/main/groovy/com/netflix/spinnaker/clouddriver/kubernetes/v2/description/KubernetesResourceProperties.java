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

import com.netflix.spinnaker.clouddriver.kubernetes.config.CustomKubernetesResource;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.KubernetesUnversionedArtifactConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.KubernetesVersionedArtifactConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.CustomKubernetesHandlerFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.StringUtils;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler.DeployPriority.WORKLOAD_CONTROLLER_PRIORITY;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KubernetesResourceProperties {
  KubernetesHandler handler;
  boolean versioned;
  KubernetesVersionedArtifactConverter versionedConverter;
  KubernetesUnversionedArtifactConverter unversionedConverter;

  public static KubernetesResourceProperties fromCustomResource(CustomKubernetesResource customResource) {
    String deployPriority = customResource.getDeployPriority();
    int deployPriorityValue;
    if (StringUtils.isEmpty(deployPriority)) {
      deployPriorityValue = WORKLOAD_CONTROLLER_PRIORITY.getValue();
    } else {
      try {
        deployPriorityValue = Integer.valueOf(deployPriority);
      } catch (NumberFormatException e) {
        deployPriorityValue = KubernetesHandler.DeployPriority.fromString(deployPriority).getValue();
      }
    }

    KubernetesHandler handler = CustomKubernetesHandlerFactory.create(
        KubernetesKind.fromString(customResource.getKubernetesKind(), true, customResource.isNamespaced()),
        KubernetesSpinnakerKindMap.SpinnakerKind.fromString(customResource.getSpinnakerKind()),
        customResource.isVersioned(),
        deployPriorityValue
    );

    return KubernetesResourceProperties.builder()
        .handler(handler)
        .versioned(customResource.isVersioned())
        .versionedConverter(new KubernetesVersionedArtifactConverter())
        .unversionedConverter(new KubernetesUnversionedArtifactConverter())
        .build();
  }
}
