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
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindProperties;
import java.util.Collection;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * A class representing the Kubernetes kind properties that are built into Spinnaker. By design,
 * this class does not support updating any of the properties in the registry; by making instances
 * immutable, they can be shared across threads without need for further synchronization.
 */
public class GlobalKubernetesKindRegistry {
  private final ImmutableMap<KubernetesKind, KubernetesKindProperties> nameMap;

  /**
   * Creates a {@link GlobalKubernetesKindRegistry} populated with the supplied {@link
   * KubernetesKindProperties}.
   */
  public GlobalKubernetesKindRegistry(
      @Nonnull Collection<KubernetesKindProperties> kubernetesKindProperties) {
    ImmutableMap.Builder<KubernetesKind, KubernetesKindProperties> mapBuilder =
        new ImmutableMap.Builder<>();
    kubernetesKindProperties.forEach(kp -> mapBuilder.put(kp.getKubernetesKind(), kp));
    this.nameMap = mapBuilder.build();
  }

  /**
   * Searches the registry for a {@link KubernetesKindProperties} with the supplied {@link
   * KubernetesKind}. If the kind has been registered, returns the {@link KubernetesKindProperties}
   * that were registered for the kind; otherwise, returns an empty {@link Optional}.
   */
  @Nonnull
  public Optional<KubernetesKindProperties> getKindProperties(@Nonnull KubernetesKind kind) {
    return Optional.ofNullable(nameMap.get(kind));
  }

  /** Returns a list of all registered kinds */
  @Nonnull
  public ImmutableCollection<KubernetesKindProperties> getRegisteredKinds() {
    return nameMap.values();
  }
}
