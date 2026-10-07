/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.clouddriver.kubernetes.description.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import org.junit.jupiter.api.Test;

/**
 * This class backs the coordinate resolution for scale, enable/disable, rollout pause/resume/undo,
 * and rolling-restart manifest operations, so its {@code getPointCoordinates()} wiring is
 * security-relevant: see spinnaker/spinnaker#5992.
 */
final class KubernetesManifestOperationDescriptionTest {
  @Test
  void getPointCoordinatesDefaultsBlankNamespaceForNamespacedKind() {
    KubernetesManifestOperationDescription description =
        new KubernetesManifestOperationDescription();
    description.setCredentials(namedAccountCredentials(true));
    description.setManifestName("deployment my-deployment");

    KubernetesCoordinates coordinates = description.getPointCoordinates();

    assertThat(coordinates.getNamespace()).isEqualTo(KubernetesCoordinates.DEFAULT_NAMESPACE);
  }

  @Test
  void getPointCoordinatesLeavesClusterScopedKindBlank() {
    KubernetesManifestOperationDescription description =
        new KubernetesManifestOperationDescription();
    description.setCredentials(namedAccountCredentials(false));
    description.setManifestName("clusterRole my-cluster-role");

    KubernetesCoordinates coordinates = description.getPointCoordinates();

    assertThat(coordinates.getNamespace()).isEmpty();
  }

  @Test
  void getPointCoordinatesLeavesExplicitLocationUnchanged() {
    KubernetesManifestOperationDescription description =
        new KubernetesManifestOperationDescription();
    description.setCredentials(namedAccountCredentials(true));
    description.setManifestName("deployment my-deployment");
    description.setLocation("explicit-ns");

    KubernetesCoordinates coordinates = description.getPointCoordinates();

    assertThat(coordinates.getNamespace()).isEqualTo("explicit-ns");
  }

  private static KubernetesNamedAccountCredentials namedAccountCredentials(boolean isNamespaced) {
    KubernetesKindProperties kindProperties = mock(KubernetesKindProperties.class);
    when(kindProperties.isNamespaced()).thenReturn(isNamespaced);
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    when(credentials.getKindProperties(any())).thenReturn(kindProperties);
    KubernetesNamedAccountCredentials accountCredentials =
        mock(KubernetesNamedAccountCredentials.class);
    when(accountCredentials.getCredentials()).thenReturn(credentials);
    return accountCredentials;
  }
}
