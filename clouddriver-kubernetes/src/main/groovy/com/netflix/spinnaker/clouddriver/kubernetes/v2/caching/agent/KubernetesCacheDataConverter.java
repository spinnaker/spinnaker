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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind.POD;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind.SERVICE;
import static java.lang.Math.toIntExact;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesCachingProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.names.KubernetesManifestNamer;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import io.kubernetes.client.JSON;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class KubernetesCacheDataConverter {
  private static ObjectMapper mapper = new ObjectMapper();
  private static final JSON json = new JSON();
  // TODO(lwander): make configurable
  @Getter private static final int logicalTtlSeconds = toIntExact(TimeUnit.MINUTES.toSeconds(10));
  @Getter private static final int infrastructureTtlSeconds = -1;
  // These are kinds which are are frequently added/removed from other resources, and can sometimes
  // persist in the cache when no relationships are found.
  // todo(lwander) investigate if this can cause flapping in UI for on demand updates -- no
  // consensus on this yet.
  @Getter private static final List<KubernetesKind> stickyKinds = Arrays.asList(SERVICE, POD);

  public static Optional<Keys.CacheKey> convertAsArtifact(
      KubernetesCacheData kubernetesCacheData, String account, KubernetesManifest manifest) {
    String namespace = manifest.getNamespace();
    Optional<Artifact> optional = KubernetesManifestAnnotater.getArtifact(manifest);
    if (!optional.isPresent()) {
      return Optional.empty();
    }

    Artifact artifact = optional.get();

    try {
      KubernetesManifest lastAppliedConfiguration =
          KubernetesManifestAnnotater.getLastAppliedConfiguration(manifest);
      if (artifact.getMetadata() == null) {
        artifact.setMetadata(new HashMap<>());
      }
      artifact.getMetadata().put("lastAppliedConfiguration", lastAppliedConfiguration);
      artifact.getMetadata().put("account", account);
    } catch (Exception e) {
      log.warn("Unable to get last applied configuration from {}: ", manifest, e);
    }

    if (artifact.getType() == null) {
      log.debug(
          "No assigned artifact type for resource "
              + namespace
              + ":"
              + manifest.getFullResourceName());
      return Optional.empty();
    }

    Map<String, Object> attributes =
        new ImmutableMap.Builder<String, Object>()
            .put("artifact", artifact)
            .put(
                "creationTimestamp",
                Optional.ofNullable(manifest.getCreationTimestamp()).orElse(""))
            .build();

    Keys.CacheKey key =
        new Keys.ArtifactCacheKey(
            artifact.getType(), artifact.getName(), artifact.getLocation(), artifact.getVersion());

    kubernetesCacheData.addItem(key, attributes);
    return Optional.of(key);
  }

  public static Collection<CacheData> dedupCacheData(Collection<CacheData> input) {
    Map<String, CacheData> cacheDataById = new HashMap<>();
    for (CacheData cd : input) {
      String id = cd.getId();
      if (cacheDataById.containsKey(id)) {
        CacheData other = cacheDataById.get(id);
        cd = mergeCacheData(cd, other);
      }

      cacheDataById.put(id, cd);
    }

    return cacheDataById.values();
  }

  public static CacheData mergeCacheData(CacheData current, CacheData added) {
    String id = current.getId();
    Map<String, Object> attributes = new HashMap<>(current.getAttributes());
    attributes.putAll(added.getAttributes());
    // Behavior is: if no ttl is set on either, the merged key won't expire
    int ttl = Math.min(current.getTtlSeconds(), added.getTtlSeconds());
    Map<String, Collection<String>> relationships = new HashMap<>(current.getRelationships());

    added
        .getRelationships()
        .entrySet()
        .forEach(
            entry ->
                relationships.merge(
                    entry.getKey(),
                    entry.getValue(),
                    (a, b) -> {
                      Collection<String> res = new HashSet<>(Math.max(a.size(), b.size()));
                      res.addAll(a);
                      res.addAll(b);
                      return res;
                    }));

    return defaultCacheData(id, ttl, attributes, relationships);
  }

  public static CacheData convertPodMetric(
      String account, String namespace, KubernetesPodMetric podMetric) {
    String podName = podMetric.getPodName();
    Map<String, Object> attributes =
        new ImmutableMap.Builder<String, Object>()
            .put("name", podName)
            .put("namespace", namespace)
            .put("metrics", podMetric.getContainerMetrics())
            .build();

    Map<String, Collection<String>> relationships =
        new HashMap<>(
            new ImmutableMap.Builder<String, Collection<String>>()
                .put(
                    POD.toString(),
                    Collections.singletonList(
                        Keys.InfrastructureCacheKey.createKey(POD, account, namespace, podName)))
                .build());

    String id = Keys.MetricCacheKey.createKey(POD, account, namespace, podName);

    return defaultCacheData(id, infrastructureTtlSeconds, attributes, relationships);
  }

  public static void convertAsResource(
      KubernetesCacheData kubernetesCacheData,
      String account,
      KubernetesManifest manifest,
      List<KubernetesManifest> resourceRelationships,
      boolean onlySpinnakerManaged) {
    KubernetesCachingProperties cachingProperties =
        KubernetesManifestAnnotater.getCachingProperties(manifest);
    if (cachingProperties.isIgnore()) {
      return;
    }

    if (onlySpinnakerManaged && StringUtils.isEmpty(cachingProperties.getApplication())) {
      return;
    }

    logMalformedManifest(() -> "Converting " + manifest + " to a cached resource", manifest);

    KubernetesKind kind = manifest.getKind();

    KubernetesApiVersion apiVersion = manifest.getApiVersion();
    String name = manifest.getName();
    String namespace = manifest.getNamespace();
    Namer<KubernetesManifest> namer =
        account == null
            ? new KubernetesManifestNamer()
            : NamerRegistry.lookup()
                .withProvider(KubernetesCloudProvider.getID())
                .withAccount(account)
                .withResource(KubernetesManifest.class);
    Moniker moniker = namer.deriveMoniker(manifest);

    Map<String, Object> attributes =
        new ImmutableMap.Builder<String, Object>()
            .put("kind", kind)
            .put("apiVersion", apiVersion)
            .put("name", name)
            .put("namespace", namespace)
            .put("fullResourceName", manifest.getFullResourceName())
            .put("manifest", manifest)
            .put("moniker", moniker)
            .put("application", cachingProperties.getApplication())
            .build();

    Keys.CacheKey key = new Keys.InfrastructureCacheKey(kind, account, namespace, name);
    kubernetesCacheData.addItem(key, attributes);

    String application = moniker.getApp();
    if (StringUtils.isEmpty(application)) {
      log.debug(
          "Encountered not-spinnaker-owned resource "
              + namespace
              + ":"
              + manifest.getFullResourceName());
    } else {
      if (kind != null && kind.hasClusterRelationship()) {
        addLogicalRelationships(kubernetesCacheData, key, account, moniker);
      }
    }

    kubernetesCacheData.addRelationships(
        key, ownerReferenceRelationships(account, namespace, manifest.getOwnerReferences()));
    kubernetesCacheData.addRelationships(
        key, implicitRelationships(manifest, account, resourceRelationships));

    KubernetesCacheDataConverter.convertAsArtifact(kubernetesCacheData, account, manifest)
        .ifPresent(artifactKey -> kubernetesCacheData.addRelationship(key, artifactKey));
  }

  public static List<KubernetesPodMetric.ContainerMetric> getMetrics(CacheData cacheData) {
    return mapper.convertValue(
        cacheData.getAttributes().get("metrics"),
        new TypeReference<List<KubernetesPodMetric.ContainerMetric>>() {});
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

  private static CacheData defaultCacheData(
      String id,
      int ttlSeconds,
      Map<String, Object> attributes,
      Map<String, Collection<String>> relationships) {
    // when no relationship exists, and `null` is written in place of a value, the old value of the
    // relationship
    // (whatever was picked up the prior cache cycle) is persisted, leaving sticky relationship data
    // in the cache.
    // we don't zero out all non existing relationships because it winds up causing far more writes
    // to redis.
    stickyKinds.forEach(k -> relationships.computeIfAbsent(k.toString(), (s) -> new ArrayList<>()));
    return new DefaultCacheData(id, ttlSeconds, attributes, relationships);
  }

  private static void addLogicalRelationships(
      KubernetesCacheData kubernetesCacheData,
      Keys.CacheKey infrastructureKey,
      String account,
      Moniker moniker) {
    String application = moniker.getApp();
    Keys.CacheKey applicationKey = new Keys.ApplicationCacheKey(application);
    kubernetesCacheData.addRelationship(infrastructureKey, applicationKey);

    String cluster = moniker.getCluster();
    if (StringUtils.isNotEmpty(cluster)) {
      Keys.CacheKey clusterKey = new Keys.ClusterCacheKey(account, application, cluster);
      kubernetesCacheData.addRelationship(infrastructureKey, clusterKey);
      kubernetesCacheData.addRelationship(applicationKey, clusterKey);
    }
  }

  private static Set<Keys.CacheKey> implicitRelationships(
      KubernetesManifest source, String account, List<KubernetesManifest> manifests) {
    String namespace = source.getNamespace();
    manifests = manifests == null ? new ArrayList<>() : manifests;
    return manifests.stream()
        .map(m -> new Keys.InfrastructureCacheKey(m.getKind(), account, namespace, m.getName()))
        .collect(Collectors.toSet());
  }

  static Set<Keys.CacheKey> ownerReferenceRelationships(
      String account, String namespace, List<KubernetesManifest.OwnerReference> references) {
    references = references == null ? new ArrayList<>() : references;

    return references.stream()
        .map(r -> new Keys.InfrastructureCacheKey(r.getKind(), account, namespace, r.getName()))
        .collect(Collectors.toSet());
  }

  /**
   * To ensure the entire relationship graph is bidirectional, invert any relationship entries here
   * to point back at the resource being cached (key).
   */
  static List<CacheData> invertRelationships(List<CacheData> resourceData) {
    Map<String, Set<String>> inverted = new HashMap<>();
    resourceData.forEach(
        cacheData ->
            cacheData.getRelationships().values().stream()
                .flatMap(Collection::stream)
                .forEach(
                    r -> inverted.computeIfAbsent(r, k -> new HashSet<>()).add(cacheData.getId())));

    return inverted.entrySet().stream()
        .map(e -> KubernetesCacheDataConverter.buildInverseRelationship(e.getKey(), e.getValue()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  private static Optional<CacheData> buildInverseRelationship(
      String key, Set<String> relationshipKeys) {
    Map<String, Collection<String>> relationships = new HashMap<>();
    for (String relationshipKey : relationshipKeys) {
      Keys.CacheKey parsedKey =
          Keys.parseKey(relationshipKey)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Cache data produced with illegal key format " + relationshipKey));
      relationships
          .computeIfAbsent(parsedKey.getGroup(), k -> new HashSet<>())
          .add(relationshipKey);
    }

    /*
     * Worth noting the strange behavior here. If we are inverting a relationship to create a cache data for
     * either a cluster or an application we need to insert attributes to ensure the cache data gets entered into
     * the cache. If we are caching anything else, we don't want competing agents to overwrite attributes, so
     * we leave them blank.
     */
    return Keys.parseKey(key)
        .map(
            k -> {
              Map<String, Object> attributes;
              int ttl;
              if (Keys.LogicalKind.isLogicalGroup(k.getGroup())) {
                ttl = logicalTtlSeconds;
                attributes =
                    new ImmutableMap.Builder<String, Object>().put("name", k.getName()).build();
              } else {
                ttl = infrastructureTtlSeconds;
                attributes = new HashMap<>();
              }
              return defaultCacheData(key, ttl, attributes, relationships);
            });
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

  static void logMalformedManifest(Supplier<String> contextMessage, KubernetesManifest manifest) {
    if (manifest == null) {
      log.warn("{}: manifest may not be null", contextMessage.get());
      return;
    }

    if (manifest.getKind() == null) {
      log.warn("{}: manifest kind may not be null, {}", contextMessage.get(), manifest);
    }

    if (StringUtils.isEmpty(manifest.getName())) {
      log.warn("{}: manifest name may not be null, {}", contextMessage.get(), manifest);
    }

    if (StringUtils.isEmpty(manifest.getNamespace()) && manifest.getKind().isNamespaced()) {
      log.warn("{}: manifest namespace may not be null, {}", contextMessage.get(), manifest);
    }
  }

  static int relationshipCount(Collection<CacheData> data) {
    return data.stream().map(d -> relationshipCount(d)).reduce(0, (a, b) -> a + b);
  }

  static int relationshipCount(CacheData data) {
    return data.getRelationships().values().stream()
        .map(Collection::size)
        .reduce(0, (a, b) -> a + b);
  }

  @Builder
  private static class CacheDataKeyPair {
    Keys.CacheKey key;
    CacheData cacheData;
  }

  static Map<String, Collection<CacheData>> stratifyCacheDataByGroup(
      Collection<CacheData> ungroupedCacheData) {
    return ungroupedCacheData.stream()
        .map(
            cd ->
                CacheDataKeyPair.builder()
                    .cacheData(cd)
                    .key(
                        Keys.parseKey(cd.getId())
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "Cache data produced with illegal key format "
                                            + cd.getId())))
                    .build())
        .filter(
            kp -> {
              // given that we now have large caching agents that are authoritative for huge chunks
              // of the cache,
              // it's possible that some resources (like events) still point to deleted resources.
              // these won't have
              // any attributes, but if we add a cache entry here, the deleted item will still be
              // cached
              if (kp.key instanceof Keys.InfrastructureCacheKey) {
                return !(kp.cacheData.getAttributes() == null
                    || kp.cacheData.getAttributes().isEmpty());
              } else {
                return true;
              }
            })
        .collect(
            Collectors.groupingBy(
                kp -> kp.key.getGroup(),
                Collectors.mapping(kp -> kp.cacheData, Collectors.toCollection(ArrayList::new))));
  }
}
