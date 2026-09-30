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

package com.netflix.spinnaker.clouddriver.kubernetes.description.servergroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKindProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import org.junit.jupiter.api.Test;

/**
 * Backs coordinate resolution for the resize-server-group operation. See spinnaker/spinnaker#5992:
 * {@code getCoordinates()} must resolve a blank namespace before validation, not leave it for
 * kubectl to resolve unchecked.
 */
final class KubernetesServerGroupOperationDescriptionTest {
  @Test
  void getCoordinatesDefaultsBlankNamespaceForNamespacedKind() {
    KubernetesServerGroupOperationDescription description =
        new KubernetesServerGroupOperationDescription();
    description.setCredentials(namedAccountCredentials(true));
    description.setServerGroupName("replicaSet my-replica-set");

    KubernetesCoordinates coordinates = description.getCoordinates();

    assertThat(coordinates.getNamespace()).isEqualTo(KubernetesCoordinates.DEFAULT_NAMESPACE);
  }

  @Test
  void getCoordinatesLeavesExplicitRegionUnchanged() {
    KubernetesServerGroupOperationDescription description =
        new KubernetesServerGroupOperationDescription();
    description.setCredentials(namedAccountCredentials(true));
    description.setServerGroupName("replicaSet my-replica-set");
    description.setRegion("explicit-ns");

    KubernetesCoordinates coordinates = description.getCoordinates();

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
