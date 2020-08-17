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
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KubernetesManifestProvider {
  private final KubernetesCacheUtils cacheUtils;
  private final KubernetesAccountResolver accountResolver;

  @Autowired
  public KubernetesManifestProvider(
      KubernetesAccountResolver accountResolver, KubernetesCacheUtils cacheUtils) {
    this.cacheUtils = cacheUtils;
    this.accountResolver = accountResolver;
  }

  @Nullable
  public KubernetesV2Manifest getManifest(
      String account, String location, String name, boolean includeEvents) {
    Optional<KubernetesCredentials> optionalCredentials = accountResolver.getCredentials(account);
    if (!optionalCredentials.isPresent()) {
      return null;
    }
    KubernetesCredentials credentials = optionalCredentials.get();

    KubernetesCoordinates coords;
    try {
      coords = KubernetesCoordinates.builder().namespace(location).fullResourceName(name).build();
    } catch (IllegalArgumentException e) {
      return null;
    }

    KubernetesManifest manifest = credentials.get(coords);
    if (manifest == null) {
      return null;
    }

    List<KubernetesManifest> events =
        includeEvents ? credentials.eventsFor(coords) : ImmutableList.of();

    List<KubernetesPodMetric.ContainerMetric> metrics = ImmutableList.of();
    if (includeEvents
        && coords.getKind().equals(KubernetesKind.POD)
        && credentials.isMetricsEnabled()) {
      metrics =
          credentials.topPod(coords).stream()
              .map(KubernetesPodMetric::getContainerMetrics)
              .flatMap(Collection::stream)
              .collect(Collectors.toList());
    }

    return KubernetesV2ManifestBuilder.buildManifest(credentials, manifest, events, metrics);
  }

  @Nullable
  public List<KubernetesV2Manifest> getClusterAndSortAscending(
      String account, String location, String kind, String app, String cluster, Sort sort) {
    Optional<KubernetesCredentials> optionalCredentials = accountResolver.getCredentials(account);
    if (!optionalCredentials.isPresent()) {
      return null;
    }
    KubernetesCredentials credentials = optionalCredentials.get();

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

  public enum Sort {
    AGE,
    SIZE
  }
}
