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

package com.netflix.spinnaker.clouddriver.kubernetes.artifact;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

final class ArtifactConverterTest {
  private static final String ACCOUNT = "my-account";
  private static final String NAMESPACE = "my-namespace";
  private static final String NAME = "my-name";
  private static final String KIND = "Pod";

  private static final ObjectMapper mapper = new ObjectMapper();

  @Test
  void artifactWithoutVersion() {
    KubernetesManifest manifest = getStubManifest();
    Artifact artifact = ArtifactConverter.toArtifact(manifest, ACCOUNT, OptionalInt.empty());

    assertThat(artifact.getType()).isEqualTo("kubernetes/pod");
    assertThat(artifact.getName()).isEqualTo(NAME);
    assertThat(artifact.getLocation()).isEqualTo(NAMESPACE);
    assertThat(artifact.getVersion()).isNullOrEmpty();
    assertThat(artifact.getReference()).isEqualTo(NAME);
    assertThat(artifact.getMetadata("account")).isEqualTo(ACCOUNT);
  }

  private static KubernetesManifest getStubManifest() {
    return mapper.convertValue(
        ImmutableMap.of(
            "kind", KIND, "metadata", ImmutableMap.of("name", NAME, "namespace", NAMESPACE)),
        KubernetesManifest.class);
  }
}
