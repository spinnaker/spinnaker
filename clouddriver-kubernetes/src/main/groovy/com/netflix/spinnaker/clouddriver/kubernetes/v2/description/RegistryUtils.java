/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RegistryUtils {
  private static Optional<KubernetesHandler> lookupHandler(
      KubernetesResourcePropertyRegistry propertyRegistry, String account, KubernetesKind kind) {
    if (kind == null) {
      return Optional.empty();
    }

    KubernetesResourceProperties properties = propertyRegistry.get(account, kind);
    if (properties == null) {
      return Optional.empty();
    }

    KubernetesHandler handler = properties.getHandler();

    if (handler == null) {
      return Optional.empty();
    }

    return Optional.of(handler);
  }

  public static void removeSensitiveKeys(
      KubernetesResourcePropertyRegistry propertyRegistry,
      String account,
      KubernetesManifest manifest) {
    lookupHandler(propertyRegistry, account, manifest.getKind())
        .ifPresent(h -> h.removeSensitiveKeys(manifest));
  }

  public static void addRelationships(
      KubernetesResourcePropertyRegistry propertyRegistry,
      String account,
      KubernetesKind kind,
      Map<KubernetesKind, List<KubernetesManifest>> allResources,
      Map<KubernetesManifest, List<KubernetesManifest>> relationshipMap) {
    lookupHandler(propertyRegistry, account, kind)
        .ifPresent(h -> h.addRelationships(allResources, relationshipMap));
  }
}
