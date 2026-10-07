/*
 * Copyright 2020 Google, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKindProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class KubernetesCoordinatesTest {
  @Test
  void withDefaultedNamespaceDefaultsBlankNamespaceForNamespacedKind() {
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    when(credentials.getKindProperties(KubernetesKind.DEPLOYMENT))
        .thenReturn(KubernetesKindProperties.create(KubernetesKind.DEPLOYMENT, true));
    KubernetesCoordinates coordinates =
        KubernetesCoordinates.builder()
            .kind(KubernetesKind.DEPLOYMENT)
            .namespace("")
            .name("abc")
            .build();

    KubernetesCoordinates resolved = coordinates.withDefaultedNamespace(credentials);

    assertThat(resolved.getNamespace()).isEqualTo(KubernetesCoordinates.DEFAULT_NAMESPACE);
  }

  @Test
  void withDefaultedNamespaceLeavesClusterScopedKindBlank() {
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    when(credentials.getKindProperties(KubernetesKind.CLUSTER_ROLE))
        .thenReturn(KubernetesKindProperties.create(KubernetesKind.CLUSTER_ROLE, false));
    KubernetesCoordinates coordinates =
        KubernetesCoordinates.builder()
            .kind(KubernetesKind.CLUSTER_ROLE)
            .namespace("")
            .name("abc")
            .build();

    KubernetesCoordinates resolved = coordinates.withDefaultedNamespace(credentials);

    assertThat(resolved.getNamespace()).isEmpty();
  }

  @Test
  void withDefaultedNamespaceLeavesExplicitNamespaceUnchanged() {
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    KubernetesCoordinates coordinates =
        KubernetesCoordinates.builder()
            .kind(KubernetesKind.DEPLOYMENT)
            .namespace("explicit-ns")
            .name("abc")
            .build();

    KubernetesCoordinates resolved = coordinates.withDefaultedNamespace(credentials);

    assertThat(resolved.getNamespace()).isEqualTo("explicit-ns");
    // An explicit namespace doesn't need the kind's scope resolved.
    verifyNoInteractions(credentials);
  }

  @ParameterizedTest
  @MethodSource("parseNameCases")
  void parseName(ParseTestCase testCase) {
    KubernetesCoordinates coordinates =
        KubernetesCoordinates.builder().fullResourceName(testCase.fullResourceName).build();

    assertThat(coordinates.getKind()).isEqualTo(testCase.expectedKind);
    assertThat(coordinates.getName()).isEqualTo(testCase.expectedName);
  }

  static Stream<ParseTestCase> parseNameCases() {
    return Stream.of(
        new ParseTestCase("replicaSet abc", KubernetesKind.REPLICA_SET, "abc"),
        new ParseTestCase("replicaSet abc", KubernetesKind.REPLICA_SET, "abc"),
        new ParseTestCase("rs abc", KubernetesKind.REPLICA_SET, "abc"),
        new ParseTestCase("service abc", KubernetesKind.SERVICE, "abc"),
        new ParseTestCase("SERVICE abc", KubernetesKind.SERVICE, "abc"),
        new ParseTestCase("ingress abc", KubernetesKind.INGRESS, "abc"));
  }

  @RequiredArgsConstructor
  private static class ParseTestCase {
    final String fullResourceName;
    final KubernetesKind expectedKind;
    final String expectedName;
  }
}
