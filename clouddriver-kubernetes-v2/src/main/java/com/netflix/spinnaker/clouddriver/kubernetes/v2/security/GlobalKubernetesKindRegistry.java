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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindProperties;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A class representing the Kubernetes kind properties that are built into Spinnaker. By design,
 * this class does not support updating any of the properties in the registry; by making instances
 * immutable, they can be shared across threads without need for further synchronization.
 */
@Component
@NonnullByDefault
final class GlobalKubernetesKindRegistry {
  private final ImmutableMap<KubernetesKind, KubernetesKindProperties> nameMap;

  /**
   * Creates a {@link GlobalKubernetesKindRegistry} populated with default {@link
   * KubernetesKindProperties}.
   */
  @Autowired
  GlobalKubernetesKindRegistry() {
    this(KubernetesKindProperties.getGlobalKindProperties());
  }

  /**
   * Creates a {@link GlobalKubernetesKindRegistry} populated with the supplied {@link
   * KubernetesKindProperties}.
   */
  GlobalKubernetesKindRegistry(Iterable<KubernetesKindProperties> kubernetesKindProperties) {
    this.nameMap =
        StreamSupport.stream(kubernetesKindProperties.spliterator(), false)
            .collect(toImmutableMap(KubernetesKindProperties::getKubernetesKind, p -> p));
  }

  /**
   * Searches the registry for a {@link KubernetesKindProperties} with the supplied {@link
   * KubernetesKind}. If the kind has been registered, returns the {@link KubernetesKindProperties}
   * that were registered for the kind; otherwise, returns an empty {@link Optional}.
   */
  Optional<KubernetesKindProperties> getKindProperties(KubernetesKind kind) {
    return Optional.ofNullable(nameMap.get(kind));
  }

  /** Returns a list of all registered kinds */
  ImmutableSet<KubernetesKind> getRegisteredKinds() {
    return nameMap.keySet();
  }
}
