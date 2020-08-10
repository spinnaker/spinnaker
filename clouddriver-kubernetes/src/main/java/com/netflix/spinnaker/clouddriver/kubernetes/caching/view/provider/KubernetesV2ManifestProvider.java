/*
 * Copyright 2017 Google, Inc.
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

import static com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.LogicalKind.CLUSTERS;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesV2Manifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.ManifestProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesV2Credentials;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KubernetesV2ManifestProvider implements ManifestProvider<KubernetesV2Manifest> {
  private final KubernetesCacheUtils cacheUtils;
  private final KubernetesAccountResolver accountResolver;

  @Autowired
  public KubernetesV2ManifestProvider(
      KubernetesAccountResolver accountResolver, KubernetesCacheUtils cacheUtils) {
    this.cacheUtils = cacheUtils;
    this.accountResolver = accountResolver;
  }

  @Override
  @Nullable
  public KubernetesV2Manifest getManifest(
      String account, String location, String name, boolean includeEvents) {
    Optional<KubernetesV2Credentials> optionalCredentials = accountResolver.getCredentials(account);
    if (!optionalCredentials.isPresent()) {
      return null;
    }
    KubernetesV2Credentials credentials = optionalCredentials.get();

    Pair<KubernetesKind, String> parsedName;
    try {
      parsedName = KubernetesManifest.fromFullResourceName(name);
    } catch (Exception e) {
      return null;
    }

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
            : ImmutableList.of();

    List<KubernetesPodMetric.ContainerMetric> metrics = ImmutableList.of();
    if (includeEvents && kind.equals(KubernetesKind.POD) && credentials.isMetricsEnabled()) {
      metrics =
          credentials.topPod(namespace, parsedName.getRight()).stream()
              .map(KubernetesPodMetric::getContainerMetrics)
              .flatMap(Collection::stream)
              .collect(Collectors.toList());
    }

    return KubernetesV2ManifestBuilder.buildManifest(credentials, manifest, events, metrics);
  }

  @Override
  public List<KubernetesV2Manifest> getClusterAndSortAscending(
      String account, String location, String kind, String app, String cluster, Sort sort) {
    Optional<KubernetesV2Credentials> optionalCredentials = accountResolver.getCredentials(account);
    if (!optionalCredentials.isPresent()) {
      return null;
    }
    KubernetesV2Credentials credentials = optionalCredentials.get();

    KubernetesResourceProperties properties =
        credentials.getResourcePropertyRegistry().get(KubernetesKind.fromString(kind));

    KubernetesHandler handler = properties.getHandler();

    return cacheUtils
        .getSingleEntry(CLUSTERS.toString(), Keys.ClusterCacheKey.createKey(account, app, cluster))
        .map(
            c ->
                cacheUtils.getRelationships(c, kind).stream()
                    .map(
                        cd ->
                            KubernetesV2ManifestBuilder.buildManifest(
                                credentials,
                                KubernetesCacheDataConverter.getManifest(cd),
                                ImmutableList.of(),
                                ImmutableList.of()))
                    .filter(m -> m.getLocation().equals(location))
                    .sorted(
                        (m1, m2) ->
                            handler.comparatorFor(sort).compare(m1.getManifest(), m2.getManifest()))
                    .collect(Collectors.toList()))
        .orElse(new ArrayList<>());
  }
}
