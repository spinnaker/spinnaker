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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.LogicalKind.CLUSTERS;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesV2Manifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric.ContainerMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KubernetesManifestProvider {
  private static final Logger log = LoggerFactory.getLogger(KubernetesManifestProvider.class);
  private final KubernetesCacheUtils cacheUtils;
  private final KubernetesAccountResolver accountResolver;
  private final ExecutorService executorService =
      Executors.newCachedThreadPool(
          new ThreadFactoryBuilder().setNameFormat(getClass().getSimpleName() + "-%d").build());

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

    Future<List<KubernetesManifest>> events =
        includeEvents
            ? executorService.submit(() -> credentials.eventsFor(coords))
            : Futures.immediateFuture(ImmutableList.of());

    Future<List<ContainerMetric>> metrics =
        includeEvents
                && coords.getKind().equals(KubernetesKind.POD)
                && credentials.isMetricsEnabled()
            ? executorService.submit(() -> getPodMetrics(credentials, coords))
            : Futures.immediateFuture(ImmutableList.of());

    KubernetesManifest manifest = credentials.get(coords);
    if (manifest == null) {
      events.cancel(true);
      metrics.cancel(true);
      return null;
    }

    try {
      return KubernetesV2ManifestBuilder.buildManifest(
          credentials, manifest, events.get(), metrics.get());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      events.cancel(true);
      metrics.cancel(true);
      log.warn("Interrupted while fetching manifest: {}", coords);
      return null;
    } catch (ExecutionException e) {
      throw new RuntimeException(e.getCause());
    }
  }

  private ImmutableList<ContainerMetric> getPodMetrics(
      KubernetesCredentials credentials, KubernetesCoordinates coords) {
    return credentials.topPod(coords).stream()
        .map(KubernetesPodMetric::getContainerMetrics)
        .flatMap(Collection::stream)
        .collect(toImmutableList());
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
