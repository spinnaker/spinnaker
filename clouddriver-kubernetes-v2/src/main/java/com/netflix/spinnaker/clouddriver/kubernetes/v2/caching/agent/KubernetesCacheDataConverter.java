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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesKindRegistry;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import io.kubernetes.client.openapi.JSON;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class KubernetesCacheDataConverter {
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

  private static Optional<Keys.CacheKey> convertAsArtifact(
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

    return defaultCacheData(id, ttl, attributes, relationships);
  }

  public static void convertPodMetric(
      KubernetesCacheData kubernetesCacheData, String account, KubernetesPodMetric podMetric) {
    String podName = podMetric.getPodName();
    String namespace = podMetric.getNamespace();
    Map<String, Object> attributes =
        new ImmutableMap.Builder<String, Object>()
            .put("name", podName)
            .put("namespace", namespace)
            .put("metrics", podMetric.getContainerMetrics())
            .build();

    Keys.CacheKey key = new Keys.MetricCacheKey(POD, account, namespace, podName);
    kubernetesCacheData.addItem(key, attributes);
    kubernetesCacheData.addRelationship(
        key, new Keys.InfrastructureCacheKey(POD, account, namespace, podName));
  }

  public static void convertAsResource(
      KubernetesCacheData kubernetesCacheData,
      String account,
      KubernetesKindRegistry kindRegistry,
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

    logMalformedManifest(
        () -> "Converting " + manifest + " to a cached resource", manifest, kindRegistry);

    KubernetesKind kind = manifest.getKind();

    KubernetesApiVersion apiVersion = manifest.getApiVersion();
    String name = manifest.getName();
    String namespace = manifest.getNamespace();
    Namer<KubernetesManifest> namer =
        account == null
            ? new KubernetesManifestNamer()
            : NamerRegistry.lookup()
                .withProvider(KubernetesCloudProvider.ID)
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
      if (kindRegistry.getKindProperties(kind).hasClusterRelationship()) {
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

  private static void logMalformedManifest(
      Supplier<String> contextMessage,
      KubernetesManifest manifest,
      KubernetesKindRegistry kindRegistry) {
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

    if (StringUtils.isEmpty(manifest.getNamespace())
        && kindRegistry.getKindProperties(manifest.getKind()).isNamespaced()) {
      log.warn("{}: manifest namespace may not be null, {}", contextMessage.get(), manifest);
    }
  }

  private static int relationshipCount(Collection<CacheData> data) {
    return data.stream().mapToInt(KubernetesCacheDataConverter::relationshipCount).sum();
  }

  private static int relationshipCount(CacheData data) {
    return data.getRelationships().values().stream().mapToInt(Collection::size).sum();
  }
}
