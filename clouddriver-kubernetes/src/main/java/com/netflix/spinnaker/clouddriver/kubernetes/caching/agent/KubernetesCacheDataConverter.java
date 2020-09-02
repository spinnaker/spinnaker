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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.SECURITY_GROUPS;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.SERVER_GROUPS;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.SERVER_GROUP_MANAGERS;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind.POD;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind.SERVICE;
import static java.lang.Math.toIntExact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.CacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.ClusterCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest.OwnerReference;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import io.kubernetes.client.openapi.JSON;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesCacheDataConverter {
  private static final Logger log = LoggerFactory.getLogger(KubernetesCacheDataConverter.class);
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final JSON json = new JSON();
  // TODO(lwander): make configurable
  @Getter private static final int logicalTtlSeconds = toIntExact(TimeUnit.MINUTES.toSeconds(10));
  @Getter private static final int infrastructureTtlSeconds = -1;
  // These are kinds which are are frequently added/removed from other resources, and can sometimes
  // persist in the cache when no relationships are found.
  // todo(lwander) investigate if this can cause flapping in UI for on demand updates -- no
  // consensus on this yet.
  @Getter private static final List<KubernetesKind> stickyKinds = Arrays.asList(SERVICE, POD);
  private static final ImmutableSet<SpinnakerKind> logicalRelationshipKinds =
      ImmutableSet.of(LOAD_BALANCERS, SECURITY_GROUPS, SERVER_GROUPS, SERVER_GROUP_MANAGERS);
  private static final ImmutableSet<SpinnakerKind> clusterRelationshipKinds =
      ImmutableSet.of(SERVER_GROUPS, SERVER_GROUP_MANAGERS);

  @NonnullByDefault
  public static CacheData mergeCacheData(CacheData current, CacheData added) {
    String id = current.getId();
    Map<String, Object> attributes = new HashMap<>(current.getAttributes());
    attributes.putAll(added.getAttributes());
    // Behavior is: if no ttl is set on either, the merged key won't expire
    int ttl = Math.min(current.getTtlSeconds(), added.getTtlSeconds());
    Map<String, Collection<String>> relationships = new HashMap<>(current.getRelationships());

    added
        .getRelationships()
        .forEach(
            (key, value) ->
                relationships.merge(
                    key,
                    value,
                    (a, b) -> {
                      Collection<String> res = new HashSet<>(Math.max(a.size(), b.size()));
                      res.addAll(a);
                      res.addAll(b);
                      return res;
                    }));

    // when no relationship exists, and `null` is written in place of a value, the old value of the
    // relationship (whatever was picked up the prior cache cycle) is persisted, leaving sticky
    // relationship data in the cache. we don't zero out all non existing relationships because it
    // winds up causing far more writes to redis.
    stickyKinds.forEach(k -> relationships.computeIfAbsent(k.toString(), s -> new ArrayList<>()));
    return new DefaultCacheData(id, ttl, attributes, relationships);
  }

  @ParametersAreNonnullByDefault
  public static void convertAsResource(
      KubernetesCacheData kubernetesCacheData,
      String account,
      KubernetesSpinnakerKindMap kindMap,
      Namer<KubernetesManifest> namer,
      KubernetesManifest manifest,
      List<KubernetesManifest> resourceRelationships) {
    KubernetesKind kind = manifest.getKind();
    String name = manifest.getName();
    String namespace = manifest.getNamespace();
    Moniker moniker = namer.deriveMoniker(manifest);

    Map<String, Object> attributes =
        new ImmutableMap.Builder<String, Object>()
            .put("kind", kind)
            .put("apiVersion", manifest.getApiVersion())
            .put("name", name)
            .put("namespace", namespace)
            .put("fullResourceName", manifest.getFullResourceName())
            .put("manifest", manifest)
            .put("moniker", moniker)
            .build();

    Keys.CacheKey key = new Keys.InfrastructureCacheKey(kind, account, namespace, name);
    kubernetesCacheData.addItem(key, attributes);

    SpinnakerKind spinnakerKind = kindMap.translateKubernetesKind(kind);
    if (logicalRelationshipKinds.contains(spinnakerKind)
        && !Strings.isNullOrEmpty(moniker.getApp())) {
      addLogicalRelationships(
          kubernetesCacheData,
          key,
          account,
          moniker,
          clusterRelationshipKinds.contains(spinnakerKind));
    }
    kubernetesCacheData.addRelationships(
        key, ownerReferenceRelationships(account, namespace, manifest.getOwnerReferences()));
    kubernetesCacheData.addRelationships(
        key, implicitRelationships(manifest, account, resourceRelationships));
  }

  public static KubernetesManifest getManifest(CacheData cacheData) {
    return mapper.convertValue(cacheData.getAttributes().get("manifest"), KubernetesManifest.class);
  }

  public static Moniker getMoniker(CacheData cacheData) {
    return mapper.convertValue(cacheData.getAttributes().get("moniker"), Moniker.class);
  }

  public static KubernetesManifest convertToManifest(Object o) {
    return mapper.convertValue(o, KubernetesManifest.class);
  }

  public static <T> T getResource(Object manifest, Class<T> clazz) {
    // A little hacky, but the only way to deserialize any timestamps using string constructors
    return json.deserialize(json.serialize(manifest), clazz);
  }

  private static void addLogicalRelationships(
      KubernetesCacheData kubernetesCacheData,
      Keys.CacheKey infrastructureKey,
      String account,
      Moniker moniker,
      boolean hasClusterRelationship) {
    String application = moniker.getApp();
    Keys.CacheKey applicationKey = new Keys.ApplicationCacheKey(application);
    kubernetesCacheData.addRelationship(infrastructureKey, applicationKey);

    String cluster = moniker.getCluster();
    if (hasClusterRelationship && !Strings.isNullOrEmpty(cluster)) {
      CacheKey clusterKey = new ClusterCacheKey(account, application, cluster);
      kubernetesCacheData.addRelationship(infrastructureKey, clusterKey);
      kubernetesCacheData.addRelationship(applicationKey, clusterKey);
    }
  }

  @NonnullByDefault
  private static ImmutableSet<CacheKey> implicitRelationships(
      KubernetesManifest source, String account, List<KubernetesManifest> manifests) {
    return manifests.stream()
        .map(
            m ->
                new Keys.InfrastructureCacheKey(
                    m.getKind(), account, source.getNamespace(), m.getName()))
        .collect(toImmutableSet());
  }

  @NonnullByDefault
  static ImmutableSet<CacheKey> ownerReferenceRelationships(
      String account, String namespace, List<OwnerReference> references) {
    return references.stream()
        .map(r -> new Keys.InfrastructureCacheKey(r.getKind(), account, namespace, r.getName()))
        .collect(toImmutableSet());
  }

  static void logStratifiedCacheData(
      String agentType, Map<String, Collection<CacheData>> stratifiedCacheData) {
    for (Map.Entry<String, Collection<CacheData>> entry : stratifiedCacheData.entrySet()) {
      log.info(
          agentType
              + ": grouping "
              + entry.getKey()
              + " has "
              + entry.getValue().size()
              + " entries and "
              + relationshipCount(entry.getValue())
              + " relationships");
    }
  }

  private static int relationshipCount(Collection<CacheData> data) {
    return data.stream().mapToInt(KubernetesCacheDataConverter::relationshipCount).sum();
  }

  private static int relationshipCount(CacheData data) {
    return data.getRelationships().values().stream().mapToInt(Collection::size).sum();
  }
}
