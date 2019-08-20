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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GlobalResourcePropertyRegistry implements ResourcePropertyRegistry {
  private final ConcurrentHashMap<KubernetesKind, KubernetesResourceProperties> globalProperties =
      new ConcurrentHashMap<>();
  private final KubernetesSpinnakerKindMap kindMap;

  @Autowired
  public GlobalResourcePropertyRegistry(
      List<KubernetesHandler> handlers, KubernetesSpinnakerKindMap kindMap) {
    this.kindMap = kindMap;
    registerHandlers(handlers);
  }

  private void registerHandlers(List<KubernetesHandler> handlers) {
    for (KubernetesHandler handler : handlers) {
      KubernetesResourceProperties properties =
          new KubernetesResourceProperties(handler, handler.versioned());
      register(properties);
    }
  }

  @Nonnull
  public KubernetesResourceProperties get(KubernetesKind kind) {
    KubernetesResourceProperties globalResult = globalProperties.get(kind);
    if (globalResult != null) {
      return globalResult;
    }

    return globalProperties.get(KubernetesKind.NONE);
  }

  public Collection<KubernetesResourceProperties> values() {
    return globalProperties.values();
  }

  public void register(KubernetesResourceProperties properties) {
    KubernetesHandler handler = properties.getHandler();
    kindMap.addRelationship(handler.spinnakerKind(), handler.kind());
    globalProperties.put(handler.kind(), properties);
  }
}
