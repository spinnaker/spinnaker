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

package com.netflix.spinnaker.clouddriver.kubernetes.names;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.names.NamingStrategy;
import com.netflix.spinnaker.moniker.Moniker;
import org.junit.jupiter.api.Test;

final class KubernetesNamerRegistryTest {
  private static final NamingStrategy<KubernetesManifest> DEFAULT_NAMER =
      new KubernetesManifestNamer();
  private static final NamingStrategy<KubernetesManifest> CUSTOM_NAMER = new CustomNamer();

  private static final KubernetesNamerRegistry registry =
      new KubernetesNamerRegistry(ImmutableList.of(DEFAULT_NAMER, CUSTOM_NAMER));

  @Test
  void returnsDefaultNamer() {
    assertThat(registry.get("kubernetesAnnotations")).isSameAs(DEFAULT_NAMER);
  }

  @Test
  void returnsDefaultNamerCaseInsensitive() {
    assertThat(registry.get("KubeRneteSannotaTions")).isSameAs(DEFAULT_NAMER);
  }

  @Test
  void throwsOnMissingNamer() {
    assertThatThrownBy(() -> registry.get("missing")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void returnsCustomNamer() {
    assertThat(registry.get("customNamer")).isSameAs(CUSTOM_NAMER);
  }

  @Test
  void returnsCustomNamerCaseInsensitive() {
    assertThat(registry.get("CUSTOMNAmeR")).isSameAs(CUSTOM_NAMER);
  }

  private static class CustomNamer implements NamingStrategy<KubernetesManifest> {
    @Override
    public String getName() {
      return "customNamer";
    }

    @Override
    public void applyMoniker(KubernetesManifest obj, Moniker moniker) {}

    @Override
    public Moniker deriveMoniker(KubernetesManifest obj) {
      return null;
    }
  }
}
