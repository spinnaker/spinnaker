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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

public class KubernetesKindRegistry {
  private final Map<KubernetesKind.ScopedKind, KubernetesKind> nameMap = new ConcurrentHashMap<>();
  private final Map<KubernetesKind.ScopedKind, KubernetesKind> aliasMap = new ConcurrentHashMap<>();

  /** Registers a given {@link KubernetesKind} into the registry and returns the kind */
  @Nonnull
  public synchronized KubernetesKind registerKind(@Nonnull KubernetesKind kind) {
    nameMap.put(kind.getScopedKind(), kind);
    if (kind.getAlias() != null) {
      aliasMap.put(
          new KubernetesKind.ScopedKind(kind.getAlias(), kind.getScopedKind().getApiGroup()), kind);
    }
    return kind;
  }

  /**
   * Searches the registry for a {@link KubernetesKind} with the supplied name and apiGroup. If a
   * kind is found, it is returned. If no kind is found, the provided {@link
   * Supplier<KubernetesKind>} is invoked and the resulting kind is registered.
   *
   * <p>This method is guaranteed to atomically check and register the kind.
   */
  @Nonnull
  public synchronized KubernetesKind getOrRegisterKind(
      @Nonnull final String name,
      @Nullable final KubernetesApiGroup apiGroup,
      @Nonnull Supplier<KubernetesKind> supplier) {
    return getRegisteredKind(name, apiGroup).orElseGet(() -> registerKind(supplier.get()));
  }

  /**
   * Searches the registry for a {@link KubernetesKind} with the supplied name and apiGroup. Returns
   * an {@link Optional<KubernetesKind>} containing the kind, or an empty {@link Optional} if no
   * kind is found.
   *
   * <p>Kinds whose API groups are different but are both is a native API groups (see {@link
   * KubernetesApiGroup#isNativeGroup()}) are considered to match.
   */
  @Nonnull
  public Optional<KubernetesKind> getRegisteredKind(
      @Nonnull final String name, @Nullable final KubernetesApiGroup apiGroup) {
    if (StringUtils.isEmpty(name)) {
      return Optional.of(KubernetesKind.NONE);
    }

    if (name.equalsIgnoreCase(KubernetesKind.NONE.toString())) {
      throw new IllegalArgumentException("The 'NONE' kind cannot be read.");
    }

    KubernetesKind.ScopedKind searchKey = new KubernetesKind.ScopedKind(name, apiGroup);
    KubernetesKind result = nameMap.get(searchKey);
    if (result != null) {
      return Optional.of(result);
    }

    result = aliasMap.get(searchKey);
    if (result != null) {
      return Optional.of(result);
    }

    return Optional.empty();
  }

  /** Returns a list of all registered kinds */
  @Nonnull
  public List<KubernetesKind> getRegisteredKinds() {
    return new ArrayList<>(nameMap.values());
  }
}
