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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

public class KubernetesKindRegistry {
  private final List<KubernetesKind> values = Collections.synchronizedList(new ArrayList<>());

  /** Registers a given {@link KubernetesKind} into the registry and returns the kind */
  @Nonnull
  public KubernetesKind registerKind(@Nonnull KubernetesKind kind) {
    values.add(kind);
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

    Predicate<KubernetesKind> groupMatches =
        kind -> {
          // Exact match
          if (Objects.equals(kind.getApiGroup(), apiGroup)) {
            return true;
          }

          // If we have not specified an API group, default to finding a native kind that matches
          if (apiGroup == null || apiGroup.isNativeGroup()) {
            return kind.getApiGroup().isNativeGroup();
          }

          return false;
        };

    return values.stream()
        .filter(
            v ->
                v.getName().equalsIgnoreCase(name)
                    || (v.getAlias() != null && v.getAlias().equalsIgnoreCase(name)))
        .filter(groupMatches)
        .findAny();
  }

  /** Returns a list of all registered kinds */
  @Nonnull
  public List<KubernetesKind> getRegisteredKinds() {
    return values;
  }
}
