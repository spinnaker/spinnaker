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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest;

import com.google.common.collect.ImmutableCollection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

public class KubernetesKindRegistry {
  private final Map<KubernetesKind, KubernetesKindProperties> kindMap = new ConcurrentHashMap<>();
  private final GlobalKubernetesKindRegistry globalKindRegistry;

  private KubernetesKindRegistry(GlobalKubernetesKindRegistry globalKindRegistry) {
    this.globalKindRegistry = globalKindRegistry;
  }

  /** Registers a given {@link KubernetesKindProperties} into the registry */
  public void registerKind(@Nonnull KubernetesKindProperties kind) {
    kindMap.put(kind.getKubernetesKind(), kind);
  }

  /**
   * Searches the registry for a {@link KubernetesKindProperties} with the supplied {@link
   * KubernetesKind}. If the kind has been registered, returns the {@link KubernetesKindProperties}
   * that were registered for the kind; otherwise, calls the provided {@link Supplier} and registers
   * the resulting {@link KubernetesKindProperties}.
   */
  @Nonnull
  public KubernetesKindProperties getOrRegisterKind(
      @Nonnull KubernetesKind kind, @Nonnull Supplier<KubernetesKindProperties> supplier) {
    return kindMap.computeIfAbsent(kind, k -> supplier.get());
  }

  /**
   * Searches the registry for a {@link KubernetesKindProperties} with the supplied {@link
   * KubernetesKind}. If the kind has been registered, returns the {@link KubernetesKindProperties}
   * that were registered for the kind; otherwise, looks for the kind in the {@link
   * GlobalKubernetesKindRegistry} and returns the properties found there. If the kind is not
   * registered either globally or in the account, returns a {@link KubernetesKindProperties} with
   * default properties.
   */
  @Nonnull
  public KubernetesKindProperties getRegisteredKind(@Nonnull KubernetesKind kind) {
    KubernetesKindProperties result = kindMap.get(kind);
    if (result != null) {
      return result;
    }

    return globalKindRegistry
        .getRegisteredKind(kind)
        .orElse(KubernetesKindProperties.withDefaultProperties(kind));
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
    public KubernetesKindRegistry create() {
      return new KubernetesKindRegistry(globalKindRegistry);
    }
  }
}
