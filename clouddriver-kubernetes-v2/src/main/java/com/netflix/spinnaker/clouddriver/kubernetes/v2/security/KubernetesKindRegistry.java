/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.security;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindProperties;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@NonnullByDefault
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
final class KubernetesKindRegistry {
  private final Map<KubernetesKind, KubernetesKindProperties> kindMap = new ConcurrentHashMap<>();
  private final GlobalKubernetesKindRegistry globalKindRegistry;
  private final Function<KubernetesKind, Optional<KubernetesKindProperties>> crdLookup;

  private KubernetesKindRegistry(
      GlobalKubernetesKindRegistry globalKindRegistry,
      Function<KubernetesKind, Optional<KubernetesKindProperties>> crdLookup,
      Iterable<KubernetesKindProperties> customProperties) {
    this.globalKindRegistry = globalKindRegistry;
    this.crdLookup = crdLookup;
    customProperties.forEach(this::registerKind);
  }

  /** Registers a given {@link KubernetesKindProperties} into the registry */
  private KubernetesKindProperties registerKind(KubernetesKindProperties kindProperties) {
    return kindMap.computeIfAbsent(
        kindProperties.getKubernetesKind(),
        k -> {
          log.info(
              "Dynamically registering {}, (namespaced: {})",
              kindProperties.getKubernetesKind().toString(),
              kindProperties.isNamespaced());
          return kindProperties;
        });
  }

  /**
   * Searches the registry for a {@link KubernetesKindProperties} with the supplied {@link
   * KubernetesKind}. If the kind has been registered, returns the {@link KubernetesKindProperties}
   * that were registered for the kind. If the kind is not registered, tries to look up the
   * properties using the registry's CRD lookup function. If the lookup returns properties,
   * registers them for this kind and returns them; otherwise returns a {@link
   * KubernetesKindProperties} with default properties.
   */
  KubernetesKindProperties getKindPropertiesOrDefault(KubernetesKind kind) {
    return getKindProperties(kind)
        .orElseGet(() -> KubernetesKindProperties.withDefaultProperties(kind));
  }

  private Optional<KubernetesKindProperties> getKindProperties(KubernetesKind kind) {
    Optional<KubernetesKindProperties> globalResult = globalKindRegistry.getKindProperties(kind);
    if (globalResult.isPresent()) {
      return globalResult;
    }

    KubernetesKindProperties result = kindMap.get(kind);
    if (result != null) {
      return Optional.of(result);
    }

    return crdLookup.apply(kind).map(this::registerKind);
  }

  /**
   * Returns true if the supplied {@link KubernetesKind} is registered. If the kind is not
   * registered, tries register the kind properties using the registry's CRD lookup function, and
   * returns true if the kind was successfully registered.
   *
   * @param kind The kind whose registration status will be queried
   * @return true if the kind was registered or was successfully registered using the CRD lookup
   */
  boolean isKindRegistered(KubernetesKind kind) {
    return getKindProperties(kind).isPresent();
  }

  /** Returns a list of all global kinds */
  ImmutableSet<KubernetesKind> getGlobalKinds() {
    return globalKindRegistry.getRegisteredKinds();
  }

  @Component
  public static class Factory {
    private final GlobalKubernetesKindRegistry globalKindRegistry;

    Factory(GlobalKubernetesKindRegistry globalKindRegistry) {
      this.globalKindRegistry = globalKindRegistry;
    }

    KubernetesKindRegistry create(
        Function<KubernetesKind, Optional<KubernetesKindProperties>> crdLookup,
        Iterable<KubernetesKindProperties> customProperties) {
      return new KubernetesKindRegistry(globalKindRegistry, crdLookup, customProperties);
    }

    KubernetesKindRegistry create() {
      return new KubernetesKindRegistry(
          globalKindRegistry, k -> Optional.empty(), ImmutableList.of());
    }
  }
}
