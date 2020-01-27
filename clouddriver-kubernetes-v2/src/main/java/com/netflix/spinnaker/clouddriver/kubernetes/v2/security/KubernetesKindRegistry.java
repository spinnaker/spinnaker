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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindProperties;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@ParametersAreNonnullByDefault
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class KubernetesKindRegistry {
  private final Map<KubernetesKind, KubernetesKindProperties> kindMap = new ConcurrentHashMap<>();
  private final GlobalKubernetesKindRegistry globalKindRegistry;
  private final Function<KubernetesKind, Optional<KubernetesKindProperties>> crdLookup;

  private KubernetesKindRegistry(
      GlobalKubernetesKindRegistry globalKindRegistry,
      Function<KubernetesKind, Optional<KubernetesKindProperties>> crdLookup,
      Collection<KubernetesKindProperties> customProperties) {
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
  @Nonnull
  public KubernetesKindProperties getKindProperties(KubernetesKind kind) {
    Optional<KubernetesKindProperties> globalResult = globalKindRegistry.getKindProperties(kind);
    if (globalResult.isPresent()) {
      return globalResult.get();
    }

    KubernetesKindProperties result = kindMap.get(kind);
    if (result != null) {
      return result;
    }

    return crdLookup
        .apply(kind)
        .map(this::registerKind)
        .orElseGet(() -> KubernetesKindProperties.withDefaultProperties(kind));
  }

  /** Returns a list of all global kinds */
  @Nonnull
  public ImmutableCollection<KubernetesKindProperties> getGlobalKinds() {
    return globalKindRegistry.getRegisteredKinds();
  }

  @Component
  public static class Factory {
    private final GlobalKubernetesKindRegistry globalKindRegistry;

    public Factory(GlobalKubernetesKindRegistry globalKindRegistry) {
      this.globalKindRegistry = globalKindRegistry;
    }

    @Nonnull
    public KubernetesKindRegistry create(
        Function<KubernetesKind, Optional<KubernetesKindProperties>> crdLookup,
        Collection<KubernetesKindProperties> customProperties) {
      return new KubernetesKindRegistry(globalKindRegistry, crdLookup, customProperties);
    }

    @Nonnull
    public KubernetesKindRegistry create() {
      return new KubernetesKindRegistry(
          globalKindRegistry, k -> Optional.empty(), ImmutableList.of());
    }
  }
}
