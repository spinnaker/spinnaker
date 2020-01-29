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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiGroup;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindProperties;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class KubernetesKindRegistryTest {
  private static final Function<KubernetesKind, Optional<KubernetesKindProperties>>
      NOOP_CRD_LOOKUP = k -> Optional.empty();
  private static final KubernetesApiGroup CUSTOM_API_GROUP = KubernetesApiGroup.fromString("test");
  private static final KubernetesKind CUSTOM_KIND =
      KubernetesKind.from("customKind", CUSTOM_API_GROUP);
  private static final KubernetesKindProperties CUSTOM_KIND_PROPERTIES =
      KubernetesKindProperties.create(CUSTOM_KIND, true);
  private static final KubernetesKindProperties REPLICA_SET_PROPERTIES =
      KubernetesKindProperties.create(KubernetesKind.REPLICA_SET, true);

  private KubernetesKindRegistry.Factory getFactory(
      Collection<KubernetesKindProperties> globalKinds) {
    return new KubernetesKindRegistry.Factory(new GlobalKubernetesKindRegistry(globalKinds));
  }

  @Test
  void getKindProperties() {
    KubernetesKindRegistry kindRegistry = getFactory(ImmutableList.of()).create();
    assertThat(kindRegistry.getKindPropertiesOrDefault(CUSTOM_KIND))
        .isEqualTo(CUSTOM_KIND_PROPERTIES);
  }

  @Test
  void getKindPropertiesFallsBackToGlobal() {
    KubernetesKindRegistry kindRegistry =
        getFactory(ImmutableList.of(CUSTOM_KIND_PROPERTIES)).create();
    assertThat(kindRegistry.getKindPropertiesOrDefault(CUSTOM_KIND))
        .isEqualTo(CUSTOM_KIND_PROPERTIES);
  }

  @Test
  void getKindPropertiesFallsBackToDefault() {
    KubernetesKindRegistry kindRegistry = getFactory(ImmutableList.of()).create();
    assertThat(kindRegistry.getKindPropertiesOrDefault(CUSTOM_KIND))
        .isEqualTo(KubernetesKindProperties.withDefaultProperties(CUSTOM_KIND));
  }

  @Test
  void getKindPropertiesLooksUpCrd() {
    KubernetesKindProperties customProperties = KubernetesKindProperties.create(CUSTOM_KIND, false);
    KubernetesKindRegistry kindRegistry =
        getFactory(ImmutableList.of())
            .create(k -> Optional.of(customProperties), ImmutableList.of());
    assertThat(kindRegistry.getKindPropertiesOrDefault(CUSTOM_KIND)).isEqualTo(customProperties);
  }

  @Test
  void emptyCRDLookupFallsBackToDefault() {
    KubernetesKindRegistry kindRegistry =
        getFactory(ImmutableList.of()).create(k -> Optional.empty(), ImmutableList.of());
    assertThat(kindRegistry.getKindPropertiesOrDefault(CUSTOM_KIND))
        .isEqualTo(KubernetesKindProperties.withDefaultProperties(CUSTOM_KIND));
  }

  @Test
  void isKindRegisteredFalseForUnregisteredKind() {
    KubernetesKindRegistry kindRegistry =
        getFactory(ImmutableList.of()).create(k -> Optional.empty(), ImmutableList.of());
    assertThat(kindRegistry.isKindRegistered(CUSTOM_KIND)).isFalse();
  }

  @Test
  void isKindRegisteredTrueForGlobalKind() {
    KubernetesKindRegistry kindRegistry =
        getFactory(ImmutableList.of(REPLICA_SET_PROPERTIES))
            .create(k -> Optional.empty(), ImmutableList.of());
    assertThat(kindRegistry.isKindRegistered(KubernetesKind.REPLICA_SET)).isTrue();
  }

  @Test
  void isKindRegisteredTrueForRegisteredKind() {
    KubernetesKindRegistry kindRegistry =
        getFactory(ImmutableList.of())
            .create(k -> Optional.empty(), ImmutableList.of(CUSTOM_KIND_PROPERTIES));
    assertThat(kindRegistry.isKindRegistered(CUSTOM_KIND)).isTrue();
  }

  @Test
  void isKindRegisteredTrueForSuccessfulCRDLookup() {
    KubernetesKindRegistry kindRegistry =
        getFactory(ImmutableList.of()).create(k -> Optional.empty(), ImmutableList.of());
    assertThat(kindRegistry.isKindRegistered(CUSTOM_KIND)).isFalse();
  }

  @Test
  void getGlobalKinds() {
    KubernetesKindProperties customProperties = KubernetesKindProperties.create(CUSTOM_KIND, false);
    KubernetesKindRegistry kindRegistry =
        getFactory(ImmutableList.of())
            .create(k -> Optional.of(customProperties), ImmutableList.of());
    assertThat(kindRegistry.isKindRegistered(CUSTOM_KIND)).isTrue();
  }
}
