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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider;

import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesManifestContainer;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@NonnullByDefault
final class KubernetesManifestContainerBuilder {
  static KubernetesManifestContainer buildManifest(
      KubernetesCredentials credentials,
      KubernetesManifest manifest,
      List<KubernetesManifest> events,
      List<KubernetesPodMetric.ContainerMetric> metrics) {
    String namespace = manifest.getNamespace();
    KubernetesKind kind = manifest.getKind();

    KubernetesResourceProperties properties = credentials.getResourcePropertyRegistry().get(kind);

    Function<KubernetesManifest, String> lastEventTimestamp =
        (m) -> (String) m.getOrDefault("lastTimestamp", m.getOrDefault("firstTimestamp", "n/a"));

    events =
        events.stream()
            .sorted(Comparator.comparing(lastEventTimestamp))
            .collect(Collectors.toList());

    Moniker moniker = KubernetesManifestAnnotater.getMoniker(manifest);

    KubernetesHandler handler = properties.getHandler();

    return KubernetesManifestContainer.builder()
        .account(credentials.getAccountName())
        .name(manifest.getFullResourceName())
        .location(namespace)
        .manifest(manifest)
        .moniker(moniker)
        .status(handler.status(manifest))
        .artifacts(handler.listArtifacts(manifest))
        .events(events)
        .warnings(handler.listWarnings(manifest))
        .metrics(metrics)
        .build();
  }
}
