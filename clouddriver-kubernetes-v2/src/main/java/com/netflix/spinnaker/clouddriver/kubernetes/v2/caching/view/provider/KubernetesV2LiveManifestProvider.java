/*
 * Copyright 2018 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2Manifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KubernetesV2LiveManifestProvider extends KubernetesV2AbstractManifestProvider {
  @Autowired
  public KubernetesV2LiveManifestProvider(KubernetesAccountResolver resourcePropertyResolver) {
    super(resourcePropertyResolver);
  }

  @Override
  public KubernetesV2Manifest getManifest(
      String account, String location, String name, boolean includeEvents) {
    if (!isAccountRelevant(account)) {
      return null;
    }

    if (!makesLiveCalls(account)) {
      return null;
    }

    Pair<KubernetesKind, String> parsedName;
    try {
      parsedName = KubernetesManifest.fromFullResourceName(name);
    } catch (Exception e) {
      return null;
    }

    // TODO(lwander): move to debug once early users have validated this
    log.info(
        "Live call to lookup manifest '{}:{}' in namespace '{}' under account '{}'",
        parsedName.getRight(),
        parsedName.getLeft(),
        location,
        account);
    KubernetesV2Credentials credentials =
        getCredentials(account)
            .orElseThrow(
                () -> new IllegalStateException("Already verified that credentials are relevant"));
    KubernetesManifest manifest =
        credentials.get(parsedName.getLeft(), location, parsedName.getRight());
    if (manifest == null) {
      return null;
    }

    String namespace = manifest.getNamespace();
    KubernetesKind kind = manifest.getKind();

    List<KubernetesManifest> events =
        includeEvents
            ? credentials.eventsFor(kind, namespace, parsedName.getRight())
            : Collections.emptyList();

    List<KubernetesPodMetric.ContainerMetric> metrics = Collections.emptyList();
    if (kind.equals(KubernetesKind.POD) && credentials.isMetricsEnabled()) {
      metrics =
          credentials.topPod(namespace, parsedName.getRight()).stream()
              .map(KubernetesPodMetric::getContainerMetrics)
              .flatMap(Collection::stream)
              .collect(Collectors.toList());
    }

    return buildManifest(account, manifest, events, metrics);
  }

  @Override
  public List<KubernetesV2Manifest> getClusterAndSortAscending(
      String account, String location, String kind, String app, String cluster, Sort sort) {
    return Collections.emptyList();
  }
}
