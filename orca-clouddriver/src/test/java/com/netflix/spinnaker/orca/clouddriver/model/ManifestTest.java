/*
 * Copyright 2020 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.clouddriver.model;

import static com.netflix.spinnaker.orca.TestUtils.getResource;
import static org.assertj.core.api.Java6Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class ManifestTest {
  private static final ObjectMapper objectMapper = OrcaObjectMapper.newInstance();

  @Test
  void deserializesMetadata() throws IOException {
    String resource = getResource("clouddriver/model/manifests/stable.json");
    Manifest manifest = objectMapper.readValue(resource, Manifest.class);
    assertThat(manifest.getManifest()).containsKey("metadata");
    assertThat((Map<String, Object>) manifest.getManifest().get("metadata"))
        .containsEntry("generation", 1);
  }

  @Test
  void deserializesArtifacts() throws IOException {
    String resource = getResource("clouddriver/model/manifests/stable.json");
    Manifest manifest = objectMapper.readValue(resource, Manifest.class);
    assertThat(manifest.getArtifacts())
        .containsExactly(
            Artifact.builder()
                .name("gcr.io/test-repo/test-image")
                .reference(
                    "gcr.io/test-repo/test-image@sha256:17016d5124f95c63aeccdb3a1eeffa135ac6e34215273bc46f57a8e346cabf97")
                .type("docker/image")
                .build());
  }

  @Test
  void deserializesName() throws IOException {
    String resource = getResource("clouddriver/model/manifests/stable.json");
    Manifest manifest = objectMapper.readValue(resource, Manifest.class);
    assertThat(manifest.getName()).isEqualTo("replicaSet test-rs-v016");
  }

  @Test
  void deserializesWarnings() throws IOException {
    String resource = getResource("clouddriver/model/manifests/stable.json");
    Manifest manifest = objectMapper.readValue(resource, Manifest.class);
    assertThat(manifest.getWarnings()).isEmpty();
  }

  @Test
  void defaultsFields() throws IOException {
    String resource = getResource("clouddriver/model/manifests/empty.json");
    Manifest manifest = objectMapper.readValue(resource, Manifest.class);
    assertThat(manifest.getManifest()).isNotNull();
    assertThat(manifest.getArtifacts()).isEmpty();
    assertThat(manifest.getStatus()).isEqualTo(Manifest.Status.builder().build());
    assertThat(manifest.getName()).isEmpty();
    assertThat(manifest.getWarnings()).isEmpty();
  }

  @Test
  public void stableStatus() throws IOException {
    String resource = getResource("clouddriver/model/manifests/stable.json");
    Manifest manifest = objectMapper.readValue(resource, Manifest.class);
    assertThat(manifest.getStatus()).isNotNull();
    assertThat(manifest.getStatus().getStable()).isNotNull();
    assertThat(manifest.getStatus().getStable().isState()).isTrue();
    assertThat(manifest.getStatus().getFailed()).isNotNull();
    assertThat(manifest.getStatus().getFailed().isState()).isFalse();
  }

  @Test
  public void unstableStatus() throws IOException {
    String resource = getResource("clouddriver/model/manifests/unstable.json");
    Manifest manifest = objectMapper.readValue(resource, Manifest.class);
    assertThat(manifest.getStatus()).isNotNull();
    assertThat(manifest.getStatus().getStable()).isNotNull();
    assertThat(manifest.getStatus().getStable().isState()).isFalse();
    assertThat(manifest.getStatus().getStable().getMessage()).isEqualTo("Manifest is not stable");
    assertThat(manifest.getStatus().getFailed()).isNotNull();
    assertThat(manifest.getStatus().getFailed().isState()).isFalse();
  }

  @Test
  public void failedStatus() throws IOException {
    String resource = getResource("clouddriver/model/manifests/failed.json");
    Manifest manifest = objectMapper.readValue(resource, Manifest.class);
    assertThat(manifest.getStatus()).isNotNull();
    assertThat(manifest.getStatus().getStable()).isNotNull();
    assertThat(manifest.getStatus().getStable().isState()).isFalse();
    assertThat(manifest.getStatus().getFailed()).isNotNull();
    assertThat(manifest.getStatus().getFailed().isState()).isTrue();
    assertThat(manifest.getStatus().getFailed().getMessage()).isEqualTo("Manifest failed");
  }

  @Test
  public void unknownStatus() throws IOException {
    String resource = getResource("clouddriver/model/manifests/unknown.json");
    Manifest manifest = objectMapper.readValue(resource, Manifest.class);
    assertThat(manifest.getStatus()).isNotNull();
    assertThat(manifest.getStatus().getStable()).isEqualTo(Manifest.Condition.emptyFalse());
    assertThat(manifest.getStatus().getFailed()).isEqualTo(Manifest.Condition.emptyFalse());
  }

  @Test
  public void explicitNullStatus() throws IOException {
    String resource = getResource("clouddriver/model/manifests/explicit-null.json");
    Manifest manifest = objectMapper.readValue(resource, Manifest.class);
    assertThat(manifest.getStatus()).isNotNull();
    assertThat(manifest.getStatus().getStable()).isEqualTo(Manifest.Condition.emptyFalse());
    assertThat(manifest.getStatus().getFailed()).isEqualTo(Manifest.Condition.emptyFalse());
  }
}
