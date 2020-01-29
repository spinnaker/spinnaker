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

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.CLUSTERS;

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2Manifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.model.ManifestProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
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
  public KubernetesV2Manifest getManifest(
      String account, String location, String name, boolean includeEvents) {
    Optional<KubernetesV2Credentials> optionalCredentials = accountResolver.getCredentials(account);
    if (!optionalCredentials.isPresent()) {
      return null;
    }
    KubernetesV2Credentials credentials = optionalCredentials.get();

    if (credentials.isLiveManifestCalls()) {
      return null;
    }

    Pair<KubernetesKind, String> parsedName;
    try {
      parsedName = KubernetesManifest.fromFullResourceName(name);
    } catch (Exception e) {
      return null;
    }

    KubernetesKind kind = parsedName.getLeft();
    if (!credentials.getKindProperties(kind).isNamespaced() && StringUtils.isNotEmpty(location)) {
      log.warn(
          "Kind {} is not namespaced, but namespace {} was provided (ignoring)", kind, location);
      location = "";
    }

    String key =
        Keys.InfrastructureCacheKey.createKey(kind, account, location, parsedName.getRight());

    Optional<CacheData> dataOptional = cacheUtils.getSingleEntry(kind.toString(), key);
    if (!dataOptional.isPresent()) {
      return null;
    }

    CacheData data = dataOptional.get();

    return fromCacheData(data, credentials, includeEvents);
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
                cacheUtils.loadRelationshipsFromCache(c, kind).stream()
                    .map(cd -> fromCacheData(cd, credentials, false))
                    .filter(Objects::nonNull)
                    .filter(m -> m.getLocation().equals(location))
                    .sorted(
                        (m1, m2) ->
                            handler.comparatorFor(sort).compare(m1.getManifest(), m2.getManifest()))
                    .collect(Collectors.toList()))
        .orElse(new ArrayList<>());
  }

  private KubernetesV2Manifest fromCacheData(
      CacheData data, KubernetesV2Credentials credentials, boolean includeEvents) {
    KubernetesManifest manifest = KubernetesCacheDataConverter.getManifest(data);
    String namespace = manifest.getNamespace();
    KubernetesKind kind = manifest.getKind();
    String key = data.getId();

    List<KubernetesManifest> events =
        includeEvents
            ? cacheUtils
                .getTransitiveRelationship(
                    kind.toString(),
                    Collections.singletonList(key),
                    KubernetesKind.EVENT.toString())
                .stream()
                .map(KubernetesCacheDataConverter::getManifest)
                .collect(Collectors.toList())
            : Collections.emptyList();

    String metricKey =
        Keys.MetricCacheKey.createKey(
            kind, credentials.getAccountName(), namespace, manifest.getName());
    List<KubernetesPodMetric.ContainerMetric> metrics =
        cacheUtils
            .getSingleEntry(Keys.Kind.KUBERNETES_METRIC.toString(), metricKey)
            .map(KubernetesCacheDataConverter::getMetrics)
            .orElse(Collections.emptyList());

    return KubernetesV2ManifestBuilder.buildManifest(credentials, manifest, events, metrics);
  }
}
