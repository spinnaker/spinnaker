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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesCachingProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestMetadata;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestSpinnakerRelationships;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.names.KubernetesManifestNamer;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import io.kubernetes.client.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.Kind.ARTIFACT;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.CLUSTERS;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind.NAMESPACE;
import static java.lang.Math.toIntExact;

@Slf4j
public class KubernetesCacheDataConverter {
  private static ObjectMapper mapper = new ObjectMapper();
  private static final JSON json = new JSON();
  // TODO(lwander): make configurable
  private static final int logicalTtlSeconds = toIntExact(TimeUnit.MINUTES.toSeconds(10));
  private static final int infrastructureTtlSeconds = -1;

  public static CacheData convertAsArtifact(String account, KubernetesManifest manifest) {
    KubernetesCachingProperties cachingProperties = KubernetesManifestAnnotater.getCachingProperties(manifest);
    if (cachingProperties.isIgnore()) {
      return null;
    }

    logMalformedManifest(() -> "Converting " + manifest + " to a cached artifact", manifest);

    String namespace = manifest.getNamespace();
    Optional<Artifact> optional = KubernetesManifestAnnotater.getArtifact(manifest);
    if (!optional.isPresent()) {
      return null;
    }

    Artifact artifact = optional.get();

    try {
      KubernetesManifest lastAppliedConfiguration = KubernetesManifestAnnotater.getLastAppliedConfiguration(manifest);
      if (artifact.getMetadata() == null) {
        artifact.setMetadata(new HashMap<>());
      }
      artifact.getMetadata().put("lastAppliedConfiguration", lastAppliedConfiguration);
      artifact.getMetadata().put("account", account);
    } catch (Exception e) {
      log.warn("Unable to get last applied configuration from {}: ", manifest, e);
    }

    if (artifact.getType() == null) {
      log.debug("No assigned artifact type for resource " + namespace + ":" + manifest.getFullResourceName());
      return null;
    }

    Map<String, Object> attributes = new ImmutableMap.Builder<String, Object>()
        .put("artifact", artifact)
        .put("creationTimestamp", Optional.ofNullable(manifest.getCreationTimestamp()).orElse(""))
        .build();

    Map<String, Collection<String>> cacheRelationships = new HashMap<>();

    String key = Keys.artifact(artifact.getType(), artifact.getName(), artifact.getLocation(), artifact.getVersion());
    String owner = Keys.infrastructure(manifest, account);
    cacheRelationships.put(manifest.getKind().toString(), Collections.singletonList(owner));

    return new DefaultCacheData(key, logicalTtlSeconds, attributes, cacheRelationships);
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
    Map<String, Object> attributes = new HashMap<>();
    attributes.putAll(current.getAttributes());
    attributes.putAll(added.getAttributes());
    // Behavior is: if no ttl is set on either, the merged key won't expire
    int ttl = Math.min(current.getTtlSeconds(), added.getTtlSeconds());

    Map<String, Collection<String>> relationships = new HashMap<>();
    relationships.putAll(current.getRelationships());
    added.getRelationships()
        .entrySet()
        .forEach(entry -> relationships.merge(entry.getKey(), entry.getValue(),
            (a, b) -> {
              Set<String> result = new HashSet<>();
              result.addAll(a);
              result.addAll(b);
              return result;
            }));

    return new DefaultCacheData(id, ttl, attributes, relationships);
  }

  public static CacheData convertAsResource(String account,
      KubernetesManifest manifest,
      List<KubernetesManifest> resourceRelationships) {
    KubernetesCachingProperties cachingProperties = KubernetesManifestAnnotater.getCachingProperties(manifest);
    if (cachingProperties.isIgnore()) {
      return null;
    }

    logMalformedManifest(() -> "Converting " + manifest + " to a cached resource", manifest);

    KubernetesKind kind = manifest.getKind();
    boolean hasClusterRelationship = false;
    boolean isNamespaced = true;
    if (kind != null) {
      hasClusterRelationship = kind.hasClusterRelationship();
      isNamespaced = kind.isNamespaced();
    }

    KubernetesApiVersion apiVersion = manifest.getApiVersion();
    String name = manifest.getName();
    String namespace = manifest.getNamespace();
    Namer<KubernetesManifest> namer = account == null
      ? new KubernetesManifestNamer()
      : NamerRegistry.lookup()
          .withProvider(KubernetesCloudProvider.getID())
          .withAccount(account)
          .withResource(KubernetesManifest.class);
    Moniker moniker = namer.deriveMoniker(manifest);

    Map<String, Object> attributes = new ImmutableMap.Builder<String, Object>()
        .put("kind", kind)
        .put("apiVersion", apiVersion)
        .put("name", name)
        .put("namespace", namespace)
        .put("fullResourceName", manifest.getFullResourceName())
        .put("manifest", manifest)
        .put("moniker", moniker)
        .build();

    KubernetesManifestSpinnakerRelationships relationships = KubernetesManifestAnnotater.getManifestRelationships(manifest);
    Optional<Artifact> optional = KubernetesManifestAnnotater.getArtifact(manifest);
    KubernetesManifestMetadata metadata = KubernetesManifestMetadata.builder()
        .relationships(relationships)
        .moniker(moniker)
        .artifact(optional)
        .build();

    Map<String, Collection<String>> cacheRelationships = new HashMap<>();

    String application = moniker.getApp();
    if (StringUtils.isEmpty(application)) {
      log.debug("Encountered not-spinnaker-owned resource " + namespace + ":" + manifest.getFullResourceName());
    } else {
      cacheRelationships.putAll(annotatedRelationships(account, metadata, hasClusterRelationship));
    }

    // TODO(lwander) avoid overwriting keys here
    cacheRelationships.putAll(ownerReferenceRelationships(account, namespace, manifest.getOwnerReferences()));
    cacheRelationships.putAll(implicitRelationships(manifest, account, resourceRelationships));

    String key = Keys.infrastructure(kind, account, namespace, name);
    return new DefaultCacheData(key, infrastructureTtlSeconds, attributes, cacheRelationships);
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

  public static <T> T getResource(KubernetesManifest manifest, Class<T> clazz) {
    // A little hacky, but the only way to deserialize any timestamps using string constructors
    return json.deserialize(json.serialize(manifest), clazz);
  }

  static Map<String, Collection<String>> annotatedRelationships(String account,
      KubernetesManifestMetadata metadata,
      boolean hasClusterRelationship) {
    Moniker moniker = metadata.getMoniker();
    String application = moniker.getApp();
    Optional<Artifact> optional = metadata.getArtifact();
    Map<String, Collection<String>> cacheRelationships = new HashMap<>();

    if (optional.isPresent()) {
      Artifact artifact = optional.get();
      cacheRelationships.put(ARTIFACT.toString(), Collections.singletonList(Keys.artifact(artifact.getType(), artifact.getName(), artifact.getLocation(), artifact.getVersion())));
    }

    cacheRelationships.put(APPLICATIONS.toString(), Collections.singletonList(Keys.application(application)));

    String cluster = moniker.getCluster();
    if (StringUtils.isNotEmpty(cluster) && hasClusterRelationship) {
      cacheRelationships.put(CLUSTERS.toString(), Collections.singletonList(Keys.cluster(account, application, cluster)));
    }

    return cacheRelationships;
  }

  static void addSingleRelationship(Map<String, Collection<String>> relationships, String account, String namespace, String fullName) {
    Pair<KubernetesKind, String> triple = KubernetesManifest.fromFullResourceName(fullName);
    KubernetesKind kind = triple.getLeft();
    String name = triple.getRight();

    Collection<String> keys = relationships.get(kind.toString());

    if (keys == null) {
      keys = new ArrayList<>();
    }

    keys.add(Keys.infrastructure(kind, account, namespace, name));

    relationships.put(kind.toString(), keys);
  }

  static Map<String, Collection<String>> implicitRelationships(KubernetesManifest source, String account, List<KubernetesManifest> manifests) {
    String namespace = source.getNamespace();
    Map<String, Collection<String>> relationships = new HashMap<>();
    manifests = manifests == null ? new ArrayList<>() : manifests;
    logMalformedManifests(() -> "Determining implicit relationships for " + source + " in " + account, manifests);
    for (KubernetesManifest manifest : manifests) {
      KubernetesKind kind = manifest.getKind();
      String name = manifest.getName();
      Collection<String> keys = relationships.get(kind.toString());
      if (keys == null) {
        keys = new ArrayList<>();
      }

      keys.add(Keys.infrastructure(kind, account, namespace, name));
      relationships.put(kind.toString(), keys);
    }

    return relationships;
  }

  static Map<String, Collection<String>> ownerReferenceRelationships(String account, String namespace, List<KubernetesManifest.OwnerReference> references) {
    Map<String, Collection<String>> relationships = new HashMap<>();
    references = references == null ? new ArrayList<>() : references;
    for (KubernetesManifest.OwnerReference reference : references) {
      KubernetesKind kind = reference.getKind();
      String name = reference.getName();
      Collection<String> keys = relationships.get(kind.toString());
      if (keys == null) {
        keys = new ArrayList<>();
      }

      keys.add(Keys.infrastructure(kind, account, namespace, name));
      relationships.put(kind.toString(), keys);
    }

    return relationships;
  }

  /**
   * To ensure the entire relationship graph is bidirectional, invert any relationship entries here to point back at the
   * resource being cached (key).
   */
  static List<CacheData> invertRelationships(CacheData cacheData) {
    String key = cacheData.getId();
    Keys.CacheKey parsedKey = Keys.parseKey(key).orElseThrow(() -> new IllegalStateException("Cache data produced with illegal key format " + key));
    String group = parsedKey.getGroup();
    Map<String, Collection<String>> relationshipGroupings = cacheData.getRelationships();
    List<CacheData> result = new ArrayList<>();

    for (Collection<String> relationships : relationshipGroupings.values()) {
      for (String relationship : relationships) {
        invertSingleRelationship(group, key, relationship).flatMap(cd -> {
          result.add(cd);
          return Optional.empty();
        });
      }
    }

    return result;
  }

  static void logStratifiedCacheData(String agentType, Map<String, Collection<CacheData>> stratifiedCacheData) {
    for (Map.Entry<String, Collection<CacheData>> entry : stratifiedCacheData.entrySet()) {
      log.info(agentType + ": grouping " + entry.getKey() + " has " + entry.getValue().size() + " entries and " + relationshipCount(entry.getValue()) + " relationships");
    }
  }

  static void logMalformedManifests(Supplier<String> contextMessage, List<KubernetesManifest> relationships) {
    for (KubernetesManifest relationship : relationships) {
      logMalformedManifest(contextMessage, relationship);
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
    return data.stream()
      .map(d -> relationshipCount(d))
        .reduce(0, (a, b) -> a + b);
  }

  static int relationshipCount(CacheData data) {
    return data.getRelationships().values()
        .stream()
        .map(Collection::size)
        .reduce(0, (a, b) -> a + b);
  }

  static Map<String, Collection<CacheData>> stratifyCacheDataByGroup(Collection<CacheData> ungroupedCacheData) {
    Map<String, Collection<CacheData>> result = new HashMap<>();
    for (CacheData cacheData : ungroupedCacheData) {
      String key = cacheData.getId();
      Keys.CacheKey parsedKey = Keys.parseKey(key).orElseThrow(() -> new IllegalStateException("Cache data produced with illegal key format " + key));
      if (parsedKey instanceof Keys.InfrastructureCacheKey) {
        // given that we now have large caching agents that are authoritative for huge chunks of the cache,
        // it's possible that some resources (like events) still point to deleted resources. these won't have
        // any attributes, but if we add a cache entry here, the deleted item will still be cached
        if (cacheData.getAttributes() == null || cacheData.getAttributes().isEmpty()) {
          continue;
        }
      }

      String group = parsedKey.getGroup();

      Collection<CacheData> groupedCacheData = result.get(group);
      if (groupedCacheData == null) {
        groupedCacheData = new ArrayList<>();
      }

      groupedCacheData.add(cacheData);
      result.put(group, groupedCacheData);
    }

    return result;
  }

  /*
   * Worth noting the strange behavior here. If we are inverting a relationship to create a cache data for
   * either a cluster or an application we need to insert attributes to ensure the cache data gets entered into
   * the cache. If we are caching anything else, we don't want competing agents to overrwrite attributes, so
   * we leave them blank.
   */
  private static Optional<CacheData> invertSingleRelationship(String group, String key, String relationship) {
    Map<String, Collection<String>> relationships = new HashMap<>();
    relationships.put(group, Collections.singletonList(key));
    return Keys.parseKey(relationship).map(k -> {
      Map<String, Object> attributes;
      int ttl;
      if (Keys.LogicalKind.isLogicalGroup(k.getGroup())) {
        ttl = logicalTtlSeconds;
        attributes = new ImmutableMap.Builder<String, Object>()
            .put("name", k.getName())
            .build();
      } else {
        ttl = infrastructureTtlSeconds;
        attributes = new HashMap<>();
      }
      return new DefaultCacheData(relationship, ttl, attributes, relationships);
    });
  }
}
