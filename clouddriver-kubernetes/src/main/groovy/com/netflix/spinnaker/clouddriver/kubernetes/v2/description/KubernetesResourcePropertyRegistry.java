/*
 * Copyright 2017 Google, Inc.
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

import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.KubernetesUnversionedArtifactConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.KubernetesVersionedArtifactConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KubernetesResourcePropertyRegistry {
  @Autowired
  public KubernetesResourcePropertyRegistry(
      List<KubernetesHandler> handlers, KubernetesSpinnakerKindMap kindMap) {
    for (KubernetesHandler handler : handlers) {
      KubernetesResourceProperties properties =
          KubernetesResourceProperties.builder()
              .handler(handler)
              .versioned(handler.versioned())
              .versionedConverter(new KubernetesVersionedArtifactConverter())
              .unversionedConverter(new KubernetesUnversionedArtifactConverter())
              .build();

      kindMap.addRelationship(handler.spinnakerKind(), handler.kind());
      put(handler.kind(), properties);
    }
  }

  public KubernetesResourceProperties get(String account, KubernetesKind kind) {
    ConcurrentHashMap<KubernetesKind, KubernetesResourceProperties> propertyMap =
        accountProperties.get(account);
    KubernetesResourceProperties properties = null;

    if (!kind.isRegistered()) {
      return globalProperties.get(KubernetesKind.NONE);
    }

    if (propertyMap != null) {
      // account-level properties take precedence
      properties = propertyMap.get(kind);
    }

    if (properties == null) {
      properties = globalProperties.get(kind);
    }

    if (properties == null) {
      log.warn(
          "Unable to find kind in either account properties ({}) or global properties ({})",
          propertyMap,
          globalProperties);
    }

    return properties;
  }

  private void put(KubernetesKind kind, KubernetesResourceProperties properties) {
    globalProperties.put(kind, properties);
  }

  public synchronized void registerAccountProperty(
      String account, KubernetesResourceProperties properties) {
    ConcurrentHashMap<KubernetesKind, KubernetesResourceProperties> propertyMap =
        accountProperties.get(account);
    if (propertyMap == null) {
      propertyMap = new ConcurrentHashMap<>();
    }

    propertyMap.put(properties.getHandler().kind(), properties);

    accountProperties.put(account, propertyMap);
  }

  public Collection<KubernetesResourceProperties> values() {
    Collection<KubernetesResourceProperties> result = new ArrayList<>(globalProperties.values());
    result.addAll(
        accountProperties.values().stream()
            .map(ConcurrentHashMap::values)
            .flatMap(Collection::stream)
            .collect(Collectors.toList()));

    return result;
  }

  private final ConcurrentHashMap<KubernetesKind, KubernetesResourceProperties> globalProperties =
      new ConcurrentHashMap<>();

  private final ConcurrentHashMap<
          String, ConcurrentHashMap<KubernetesKind, KubernetesResourceProperties>>
      accountProperties = new ConcurrentHashMap<>();
}
