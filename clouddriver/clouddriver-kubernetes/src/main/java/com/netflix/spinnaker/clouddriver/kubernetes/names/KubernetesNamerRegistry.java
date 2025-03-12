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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.names.NamingStrategy;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This class handles registering any naming strategies for kubernetes manifests that are on the
 * classpath, and supports looking these up by name. It is in principle possible for users to add
 * additional namers in a custom build, but it is not clear how often this is used. The only namer
 * that exists upstream is {@link KubernetesManifestNamer}.
 */
@Component
@NonnullByDefault
public class KubernetesNamerRegistry {
  private final ImmutableMap<String, NamingStrategy<KubernetesManifest>> strategies;

  @Autowired
  public KubernetesNamerRegistry(List<NamingStrategy<KubernetesManifest>> strategies) {
    this.strategies =
        strategies.stream().collect(toImmutableMap(s -> s.getName().toLowerCase(), s -> s));
  }

  /**
   * Returns a registered strategy with the supplied name (ignoring case); throws an
   * IllegalArgumentException if there is no strategy with that name.
   */
  public NamingStrategy<KubernetesManifest> get(String name) {
    return Optional.ofNullable(strategies.get(name.toLowerCase()))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format("Could not find naming strategy '%s'", name)));
  }
}
