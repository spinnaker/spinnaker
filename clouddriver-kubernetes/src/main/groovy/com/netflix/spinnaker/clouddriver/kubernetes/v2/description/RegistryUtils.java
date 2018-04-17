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
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class RegistryUtils {
  static public Optional<KubernetesHandler> lookupHandler(KubernetesResourcePropertyRegistry propertyRegistry, String account, KubernetesManifest manifest) {
    KubernetesKind kind = manifest.getKind();
    if (kind == null) {
      log.warn("Manifest {} has no kind...", manifest);
      return Optional.empty();
    }

    KubernetesResourceProperties properties = propertyRegistry.get(account, kind);
    if (properties == null) {
      log.warn("Manifest {} has no properties...", manifest);
      return Optional.empty();
    }

    KubernetesHandler handler = properties.getHandler();

    if (handler == null) {
      log.warn("Resource properties for manifest {} has no handler...", manifest);
      return Optional.empty();
    }

    return Optional.of(handler);
  }

  static public void removeSensitiveKeys(KubernetesResourcePropertyRegistry propertyRegistry, String account, KubernetesManifest manifest) {
    lookupHandler(propertyRegistry, account, manifest).ifPresent(h -> h.removeSensitiveKeys(manifest));
  }
}
