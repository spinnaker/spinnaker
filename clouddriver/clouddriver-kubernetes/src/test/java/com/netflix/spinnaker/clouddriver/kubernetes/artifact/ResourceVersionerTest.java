/*
 * Copyright 2020 Armory
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.artifact;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.ArtifactProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.OptionalInt;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class ResourceVersionerTest {
  private static final ObjectMapper mapper = new ObjectMapper();

  private static final String ACCOUNT = "my-account";
  private static final String NAMESPACE = "ns";
  private static final String NAME = "name";
  private static final String KIND = "Pod";

  @Mock private KubernetesCredentials mockCredentials;
  @Mock private ArtifactProvider artifactProvider;
  private ResourceVersioner versioner;

  @BeforeEach
  void setUp() {
    versioner = new ResourceVersioner(artifactProvider);
  }

  @Test
  void findsMatchingVersionByEquality() {
    KubernetesManifest manifest1 = getStubManifest();
    KubernetesManifest manifest2 = getStubManifest();
    // Add some random data so that the two manifests are different.
    manifest1.put("data", ImmutableMap.of("key", 1));
    manifest2.put("data", ImmutableMap.of("key", 3));

    when(artifactProvider.getArtifacts(
            KubernetesKind.fromString(KIND), NAME, NAMESPACE, mockCredentials))
        .thenReturn(
            ImmutableList.of(
                Artifact.builder()
                    .putMetadata("lastAppliedConfiguration", manifest1)
                    .putMetadata("account", ACCOUNT)
                    .version("v001")
                    .build(),
                Artifact.builder()
                    .putMetadata("lastAppliedConfiguration", manifest2)
                    .putMetadata("account", ACCOUNT)
                    .version("v002")
                    .build()));

    OptionalInt version = versioner.getVersion(manifest1, mockCredentials);
    assertThat(version).hasValue(1);
  }

  @ParameterizedTest
  @MethodSource("versionTestCases")
  void correctlyPicksNextVersion(VersionTestCase testCase) {
    when(artifactProvider.getArtifacts(
            KubernetesKind.fromString(KIND), NAME, NAMESPACE, mockCredentials))
        .thenReturn(
            testCase.getExistingVersions().stream()
                .map(v -> Artifact.builder().putMetadata("account", ACCOUNT).version(v).build())
                .collect(toImmutableList()));

    OptionalInt version = versioner.getVersion(getStubManifest(), mockCredentials);
    assertThat(version).hasValue(testCase.getNextVersion());
  }

  // Called by @MethodSource which error-prone does not detect.
  @SuppressWarnings("unused")
  private static Stream<VersionTestCase> versionTestCases() {
    return Stream.of(
        new VersionTestCase(ImmutableList.of("v000", "v001", "v002"), 3),
        new VersionTestCase(ImmutableList.of("v000"), 1),
        new VersionTestCase(ImmutableList.of(), 0),
        new VersionTestCase(ImmutableList.of("v001"), 2),
        // Unparseable version should be ignored
        new VersionTestCase(ImmutableList.of("v0abcde", "v000"), 1),
        // Version that somehow ended up negative should be ignored
        new VersionTestCase(ImmutableList.of("v-20", "v000"), 1),
        new VersionTestCase(ImmutableList.of("abc", "", "v001"), 2),
        new VersionTestCase(ImmutableList.of("v001", "v002", "v003"), 4),
        new VersionTestCase(ImmutableList.of("v000", "v002", "v003"), 4),
        new VersionTestCase(ImmutableList.of("v002", "v000", "v001"), 3),
        new VersionTestCase(ImmutableList.of("v000", "v001", "v003"), 4),
        new VersionTestCase(ImmutableList.of("v001", "v000", "v003"), 4),
        new VersionTestCase(ImmutableList.of("v999"), 1000),
        new VersionTestCase(ImmutableList.of("v1000"), 1001),
        new VersionTestCase(ImmutableList.of("v12345", "v98765"), 98766));
  }

  @RequiredArgsConstructor
  @Value
  private static class VersionTestCase {
    private final ImmutableCollection<String> existingVersions;
    private final int nextVersion;
  }

  private static KubernetesManifest getStubManifest() {
    return mapper.convertValue(
        ImmutableMap.of(
            "kind", KIND, "metadata", ImmutableMap.of("name", NAME, "namespace", NAMESPACE)),
        KubernetesManifest.class);
  }
}
