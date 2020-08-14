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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.ArtifactProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class KubernetesVersionedArtifactConverterTest {
  private static final KubernetesVersionedArtifactConverter converter =
      KubernetesVersionedArtifactConverter.INSTANCE;
  private static final ObjectMapper mapper = new ObjectMapper();

  private static final String ACCOUNT = "my-account";
  private static final String NAMESPACE = "ns";
  private static final String NAME = "name";
  private static final String KIND = "Pod";
  private static final String ARTIFACT_TYPE = "kubernetes/pod";

  @Test
  public void checkArtifactAcrossAccount() {
    ArtifactProvider provider = mock(ArtifactProvider.class);
    when(provider.getArtifacts(ARTIFACT_TYPE, NAME, NAMESPACE))
        .thenReturn(
            ImmutableList.of(
                Artifact.builder().putMetadata("account", "account1").version("v003").build(),
                Artifact.builder().putMetadata("account", "account2").version("v005").build()));

    Artifact artifact = converter.toArtifact(provider, getStubManifest(), "account1");
    assertThat(artifact).isNotNull();
    assertThat(artifact.getVersion()).isEqualTo("v004");
  }

  @Test
  void inferVersionedArtifactProperties() {
    String name =
        converter.getDeployedName(
            Artifact.builder().type("kubernetes/replicaSet").name("my-rs").version("v000").build());
    assertThat(name).isEqualTo("my-rs-v000");
  }

  @Test
  void handlesDashesInName() {
    String name =
        converter.getDeployedName(
            Artifact.builder()
                .type("kubernetes/replicaSet")
                .name("my-other-rs-_-")
                .version("v010")
                .build());
    assertThat(name).isEqualTo("my-other-rs-_--v010");
  }

  @Test
  void findsMatchingVersionByEquality() {
    KubernetesManifest manifest1 = getStubManifest();
    KubernetesManifest manifest2 = getStubManifest();
    // Add some random data so that the two manifests are different.
    manifest1.put("data", ImmutableMap.of("key", 1));
    manifest2.put("data", ImmutableMap.of("key", 3));

    String version1 = "v001";
    String version2 = "v002";

    ArtifactProvider provider = mock(ArtifactProvider.class);
    when(provider.getArtifacts(ARTIFACT_TYPE, NAME, NAMESPACE))
        .thenReturn(
            ImmutableList.of(
                Artifact.builder()
                    .putMetadata("lastAppliedConfiguration", manifest1)
                    .putMetadata("account", ACCOUNT)
                    .version(version1)
                    .build(),
                Artifact.builder()
                    .putMetadata("lastAppliedConfiguration", manifest2)
                    .putMetadata("account", ACCOUNT)
                    .version(version2)
                    .build()));

    Artifact artifact = converter.toArtifact(provider, manifest1, ACCOUNT);
    assertThat(artifact.getVersion()).isEqualTo(version1);
  }

  @ParameterizedTest
  @MethodSource("versionTestCases")
  void correctlyPicksNextVersion(VersionTestCase testCase) {
    ArtifactProvider provider = mock(ArtifactProvider.class);
    when(provider.getArtifacts(ARTIFACT_TYPE, NAME, NAMESPACE))
        .thenReturn(
            testCase.getExistingVersions().stream()
                .map(v -> Artifact.builder().putMetadata("account", ACCOUNT).version(v).build())
                .collect(toImmutableList()));

    Artifact artifact = converter.toArtifact(provider, getStubManifest(), ACCOUNT);
    assertThat(artifact.getVersion()).isEqualTo(testCase.getNextVersion());
  }

  private static Stream<VersionTestCase> versionTestCases() {
    return Stream.of(
        new VersionTestCase(ImmutableList.of("v000", "v001", "v002"), "v003"),
        new VersionTestCase(ImmutableList.of("v000"), "v001"),
        new VersionTestCase(ImmutableList.of(), "v000"),
        new VersionTestCase(ImmutableList.of("v001"), "v002"),
        new VersionTestCase(ImmutableList.of("v001", "v002", "v003"), "v004"),
        new VersionTestCase(ImmutableList.of("v000", "v002", "v003"), "v004"),
        new VersionTestCase(ImmutableList.of("v002", "v000", "v001"), "v003"),
        new VersionTestCase(ImmutableList.of("v000", "v001", "v003"), "v004"),
        new VersionTestCase(ImmutableList.of("v001", "v000", "v003"), "v004"),
        new VersionTestCase(ImmutableList.of("v1000"), "v1001"));
  }

  @RequiredArgsConstructor
  @Value
  private static class VersionTestCase {
    private final ImmutableCollection<String> existingVersions;
    private final String nextVersion;
  }

  private static KubernetesManifest getStubManifest() {
    return mapper.convertValue(
        ImmutableMap.of(
            "kind", KIND, "metadata", ImmutableMap.of("name", NAME, "namespace", NAMESPACE)),
        KubernetesManifest.class);
  }
}
