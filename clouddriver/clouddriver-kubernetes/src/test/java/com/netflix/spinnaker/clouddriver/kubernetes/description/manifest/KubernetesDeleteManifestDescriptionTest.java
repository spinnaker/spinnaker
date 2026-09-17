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
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * See spinnaker/spinnaker#5992. Both the single-target ({@code getPointCoordinates()}) and the
 * label-selector/"dynamic" ({@code getAllCoordinates()}) delete paths ultimately invoke kubectl
 * with no {@code --namespace} flag when blank, so both must resolve a namespace-scoped kind's blank
 * namespace before it reaches validation or execution.
 */
final class KubernetesDeleteManifestDescriptionTest {
  @Test
  void getPointCoordinatesDefaultsBlankNamespaceForNamespacedKind() {
    KubernetesDeleteManifestDescription description = new KubernetesDeleteManifestDescription();
    description.setCredentials(namedAccountCredentials(true));
    description.setManifestName("deployment my-deployment");

    KubernetesCoordinates coordinates = description.getPointCoordinates();

    assertThat(coordinates.getNamespace()).isEqualTo(KubernetesCoordinates.DEFAULT_NAMESPACE);
  }

  @Test
  void getAllCoordinatesDefaultsBlankNamespaceForNamespacedKind() {
    KubernetesDeleteManifestDescription description = new KubernetesDeleteManifestDescription();
    description.setCredentials(namedAccountCredentials(true));
    description.setKinds(List.of("deployment"));

    List<KubernetesCoordinates> coordinates = description.getAllCoordinates();

    assertThat(coordinates).hasSize(1);
    assertThat(coordinates.get(0).getNamespace())
        .isEqualTo(KubernetesCoordinates.DEFAULT_NAMESPACE);
  }

  @Test
  void getAllCoordinatesLeavesClusterScopedKindBlank() {
    KubernetesDeleteManifestDescription description = new KubernetesDeleteManifestDescription();
    description.setCredentials(namedAccountCredentials(false));
    description.setKinds(List.of("clusterRole"));

    List<KubernetesCoordinates> coordinates = description.getAllCoordinates();

    assertThat(coordinates).hasSize(1);
    assertThat(coordinates.get(0).getNamespace()).isEmpty();
  }

  @Test
  void getAllCoordinatesLeavesExplicitLocationUnchanged() {
    KubernetesDeleteManifestDescription description = new KubernetesDeleteManifestDescription();
    description.setCredentials(namedAccountCredentials(true));
    description.setKinds(List.of("deployment"));
    description.setLocation("explicit-ns");

    List<KubernetesCoordinates> coordinates = description.getAllCoordinates();

    assertThat(coordinates).hasSize(1);
    assertThat(coordinates.get(0).getNamespace()).isEqualTo("explicit-ns");
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
