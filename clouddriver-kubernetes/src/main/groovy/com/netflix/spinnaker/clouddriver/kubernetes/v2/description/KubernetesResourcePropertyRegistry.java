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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer.KubernetesHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KubernetesResourcePropertyRegistry {
  @Autowired
  public KubernetesResourcePropertyRegistry(List<KubernetesHandler> handlers,
      KubernetesSpinnakerKindMap kindMap,
      KubernetesVersionedArtifactConverter versionedArtifactConverter,
      KubernetesUnversionedArtifactConverter unversionedArtifactConverter) {
    for (KubernetesHandler handler : handlers) {
      KubernetesResourceProperties properties = KubernetesResourceProperties.builder()
          .handler(handler)
          .converter(handler.versioned() ? versionedArtifactConverter : unversionedArtifactConverter)
          .build();

      kindMap.addRelationship(handler.spinnakerKind(), handler.kind());
      put(handler.kind(), properties);
    }
  }

  public KubernetesResourceProperties get(KubernetesKind kind) {
    return map.get(kind);
  }

  public void put(KubernetesKind kind, KubernetesResourceProperties properties) {
    map.put(kind, properties);
  }

  public Collection<KubernetesResourceProperties> values() {
    return map.values();
  }

  private ConcurrentHashMap<KubernetesKind, KubernetesResourceProperties> map = new ConcurrentHashMap<>();
}
