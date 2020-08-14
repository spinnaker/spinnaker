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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.artifact;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class KubernetesUnversionedArtifactConverterTest {
  private static final KubernetesUnversionedArtifactConverter converter =
      KubernetesUnversionedArtifactConverter.INSTANCE;

  @Test
  void infersUnversionedArtifactProperties() {
    String name =
        converter.getDeployedName(
            Artifact.builder().type("kubernetes/deployment").name("my-deploy").build());
    assertThat(name).isEqualTo("my-deploy");
  }

  @Test
  void handlesDashesInName() {
    String name =
        converter.getDeployedName(
            Artifact.builder()
                .type("kubernetes/service")
                .name("my-other-rs-_-")
                .version("v010")
                .build());
    assertThat(name).isEqualTo("my-other-rs-_-");
  }
}
