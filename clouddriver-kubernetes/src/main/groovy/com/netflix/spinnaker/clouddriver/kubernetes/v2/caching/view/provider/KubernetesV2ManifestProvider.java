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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider;

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2Manifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.model.ManifestProvider;
import com.netflix.spinnaker.moniker.Moniker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.CLUSTERS;

@Component
@Slf4j
public class KubernetesV2ManifestProvider implements ManifestProvider<KubernetesV2Manifest> {
  private final KubernetesResourcePropertyRegistry registry;
  private final KubernetesCacheUtils cacheUtils;

  @Autowired
  public KubernetesV2ManifestProvider(KubernetesResourcePropertyRegistry registry, KubernetesCacheUtils cacheUtils) {
    this.registry = registry;
    this.cacheUtils = cacheUtils;
  }

  @Override
  public KubernetesV2Manifest getManifest(String account, String location, String name) {
    Pair<KubernetesKind, String> parsedName;
    try {
      parsedName = KubernetesManifest.fromFullResourceName(name);
    } catch (Exception e) {
      return null;
    }

    KubernetesKind kind = parsedName.getLeft();
    if (!kind.isNamespaced() && StringUtils.isNotEmpty(location)) {
      log.warn("Kind {} is not namespaced, but namespace {} was provided (ignoring)", kind, location);
      location = "";
    }

    String key = Keys.infrastructure(
        kind,
        account,
        location,
        parsedName.getRight()
    );

    Optional<CacheData> dataOptional = cacheUtils.getSingleEntry(kind.toString(), key);
    if (!dataOptional.isPresent()) {
      return null;
    }

    CacheData data = dataOptional.get();

    return fromCacheData(data, account);
  }

  @Override
  public List<KubernetesV2Manifest> getClusterAndSortAscending(String account, String location, String kind, String app, String cluster, Sort sort) {
    KubernetesResourceProperties properties = registry.get(account, KubernetesKind.fromString(kind));
    if (properties == null) {
      return null;
    }

    KubernetesHandler handler = properties.getHandler();

    return cacheUtils.getSingleEntry(CLUSTERS.toString(), Keys.cluster(account, app, cluster))
        .map(c -> cacheUtils.loadRelationshipsFromCache(c, kind).stream()
            .map(cd -> fromCacheData(cd, account)) // todo(lwander) perf improvement by checking namespace before converting
            .filter(Objects::nonNull)
            .filter(m -> m.getLocation().equals(location))
            .sorted((m1, m2) -> handler.comparatorFor(sort).compare(m1.getManifest(), m2.getManifest()))
            .collect(Collectors.toList()))
        .orElse(new ArrayList<>());
  }

  private KubernetesV2Manifest fromCacheData(CacheData data, String account) {
    KubernetesManifest manifest = KubernetesCacheDataConverter.getManifest(data);
    String namespace = manifest.getNamespace();
    KubernetesKind kind = manifest.getKind();
    String key = data.getId();

    KubernetesResourceProperties properties = registry.get(account, kind);
    if (properties == null) {
      return null;
    }

    Function<KubernetesManifest, String> lastEventTimestamp = (m) -> (String) m.getOrDefault("lastTimestamp", m.getOrDefault("firstTimestamp", "n/a"));

    List<KubernetesManifest> events = cacheUtils.getTransitiveRelationship(kind.toString(), Collections.singletonList(key), KubernetesKind.EVENT.toString())
        .stream()
        .map(KubernetesCacheDataConverter::getManifest)
        .sorted(Comparator.comparing(lastEventTimestamp))
        .collect(Collectors.toList());

    Moniker moniker = KubernetesCacheDataConverter.getMoniker(data);

    String metricKey = Keys.metric(kind, account, namespace, manifest.getName());
    List<Map> metrics = cacheUtils.getSingleEntry(Keys.Kind.KUBERNETES_METRIC.toString(), metricKey)
        .map(KubernetesCacheDataConverter::getMetrics)
        .orElse(Collections.emptyList());

    KubernetesHandler handler = properties.getHandler();

    return new KubernetesV2Manifest().builder()
        .account(account)
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
