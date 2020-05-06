/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.description;

import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RegistryUtils {
  private static Optional<KubernetesHandler> lookupHandler(
      ResourcePropertyRegistry propertyRegistry, KubernetesKind kind) {
    if (kind == null) {
      return Optional.empty();
    }

    KubernetesResourceProperties properties = propertyRegistry.get(kind);

    KubernetesHandler handler = properties.getHandler();

    if (handler == null) {
      return Optional.empty();
    }

    return Optional.of(handler);
  }

  public static void removeSensitiveKeys(
      ResourcePropertyRegistry propertyRegistry, KubernetesManifest manifest) {
    lookupHandler(propertyRegistry, manifest.getKind())
        .ifPresent(h -> h.removeSensitiveKeys(manifest));
  }

  public static void addRelationships(
      ResourcePropertyRegistry propertyRegistry,
      KubernetesKind kind,
      Map<KubernetesKind, List<KubernetesManifest>> allResources,
      Map<KubernetesManifest, List<KubernetesManifest>> relationshipMap) {
    lookupHandler(propertyRegistry, kind)
        .ifPresent(h -> h.addRelationships(allResources, relationshipMap));
  }
}
