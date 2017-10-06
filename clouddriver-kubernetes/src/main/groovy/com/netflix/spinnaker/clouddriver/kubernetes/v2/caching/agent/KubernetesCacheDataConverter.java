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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifestMetadata;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifestSpinnakerRelationships;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.Kind.ARTIFACT;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.APPLICATION;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.CLUSTER;

@Slf4j
public class KubernetesCacheDataConverter {
  private static ObjectMapper mapper = new ObjectMapper();

  public static CacheData convertAsArtifact(String account, ObjectMapper mapper, Object resource) {
    KubernetesManifest manifest = mapper.convertValue(resource, KubernetesManifest.class);
    String namespace = manifest.getNamespace();
    Artifact artifact = KubernetesManifestAnnotater.getArtifact(manifest);
    if (artifact.getType() == null) {
      log.info("No assigned artifact type for resource " + namespace + ":" + manifest.getFullResourceName());
      return null;
    }

    Map<String, Object> attributes = new ImmutableMap.Builder<String, Object>()
        .put("artifact", artifact)
        .put("creationTimestamp", manifest.getCreationTimestamp())
        .build();

    Map<String, Collection<String>> cacheRelationships = new HashMap<>();

    String key = Keys.artifact(artifact.getType(), artifact.getName(), artifact.getLocation(), artifact.getVersion());
    String owner = Keys.infrastructure(manifest, account);
    cacheRelationships.put(manifest.getKind().toString(), Collections.singletonList(owner));

    return new DefaultCacheData(key, attributes, cacheRelationships);
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
    Map<String, Object> attributes = current.getAttributes();
    Map<String, Collection<String>> relationships = current.getRelationships();
    attributes.putAll(added.getAttributes());
    added.getRelationships()
        .entrySet()
        .forEach(entry -> relationships.merge(entry.getKey(), entry.getValue(),
            (a, b) -> {
              Set<String> result = new HashSet<>();
              result.addAll(a);
              result.addAll(b);
              return result;
            }));

    return new DefaultCacheData(id, attributes, relationships);
  }

  public static CacheData convertAsResource(String account, ObjectMapper mapper, Object resource) {
    KubernetesManifest manifest = mapper.convertValue(resource, KubernetesManifest.class);
    KubernetesKind kind = manifest.getKind();
    KubernetesApiVersion apiVersion = manifest.getApiVersion();
    String name = manifest.getName();
    String namespace = manifest.getNamespace();

    Map<String, Object> attributes = new ImmutableMap.Builder<String, Object>()
        .put("kind", kind)
        .put("apiVersion", apiVersion)
        .put("name", name)
        .put("namespace", namespace)
        .put("fullResourceName", manifest.getFullResourceName())
        .put("manifest", manifest)
        .build();

    KubernetesManifestSpinnakerRelationships relationships = KubernetesManifestAnnotater.getManifestRelationships(manifest);
    Moniker moniker = KubernetesManifestAnnotater.getMoniker(manifest);
    Artifact artifact = KubernetesManifestAnnotater.getArtifact(manifest);
    KubernetesManifestMetadata metadata = KubernetesManifestMetadata.builder()
        .relationships(relationships)
        .moniker(moniker)
        .artifact(artifact)
        .build();

    Map<String, Collection<String>> cacheRelationships = new HashMap<>();

    String application = moniker.getApp();
    if (StringUtils.isEmpty(application)) {
      log.info("Skipping not-spinnaker-owned resource " + namespace + ":" + manifest.getFullResourceName());
      return null;
    }

    cacheRelationships.putAll(annotatedRelationships(account, namespace, metadata));
    // TODO(lwander) avoid overwriting keys here
    cacheRelationships.putAll(ownerReferenceRelationships(account, namespace, manifest.getOwnerReferences(mapper)));

    String key = Keys.infrastructure(apiVersion, kind, account, namespace, name);
    return new DefaultCacheData(key, attributes, cacheRelationships);
  }

  public static KubernetesManifest getManifest(CacheData cacheData) {
    return mapper.convertValue(cacheData.getAttributes().get("manifest"), KubernetesManifest.class);
  }

  static Map<String, Collection<String>> annotatedRelationships(String account, String namespace, KubernetesManifestMetadata metadata) {
    KubernetesManifestSpinnakerRelationships relationships = metadata.getRelationships();
    Moniker moniker = metadata.getMoniker();
    Artifact artifact = metadata.getArtifact();
    Map<String, Collection<String>> cacheRelationships = new HashMap<>();
    String application = moniker.getApp();

    cacheRelationships.put(ARTIFACT.toString(), Collections.singletonList(Keys.artifact(artifact.getType(), artifact.getName(), artifact.getLocation(), artifact.getVersion())));
    cacheRelationships.put(APPLICATION.toString(), Collections.singletonList(Keys.application(application)));

    String cluster = moniker.getCluster();
    if (!StringUtils.isEmpty(cluster)) {
      cacheRelationships.put(CLUSTER.toString(), Collections.singletonList(Keys.cluster(account, application, cluster)));
    }

    if (relationships.getLoadBalancers() != null) {
      for (String loadBalancer : relationships.getLoadBalancers()) {
        addSingleRelationship(cacheRelationships, account, namespace, loadBalancer);
      }
    }

    if (relationships.getSecurityGroups() != null) {
      for (String securityGroup : relationships.getSecurityGroups()) {
        addSingleRelationship(cacheRelationships, account, namespace, securityGroup);
      }
    }

    return cacheRelationships;
  }

  static void addSingleRelationship(Map<String, Collection<String>> relationships, String account, String namespace, String fullName) {
    Triple<KubernetesApiVersion, KubernetesKind, String> triple = KubernetesManifest.fromFullResourceName(fullName);
    KubernetesKind kind = triple.getMiddle();
    KubernetesApiVersion apiVersion = triple.getLeft();
    String name = triple.getRight();

    Collection<String> keys = relationships.get(kind.toString());

    if (keys == null) {
      keys = new ArrayList<>();
    }

    keys.add(Keys.infrastructure(apiVersion, kind, account, namespace, name));

    relationships.put(kind.toString(), keys);
  }

  static Map<String, Collection<String>> ownerReferenceRelationships(String account, String namespace, List<KubernetesManifest.OwnerReference> references) {
    Map<String, Collection<String>> relationships = new HashMap<>();
    for (KubernetesManifest.OwnerReference reference : references) {
      KubernetesKind kind = reference.getKind();
      KubernetesApiVersion apiVersion = reference.getApiVersion();
      String name = reference.getName();
      Collection<String> keys = relationships.get(kind.toString());
      if (keys == null) {
        keys = new ArrayList<>();
      }

      keys.add(Keys.infrastructure(apiVersion, kind, account, namespace, name));
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
      log.info(agentType + ": grouping " + entry.getKey() + " has " + entry.getValue().size() + " entries");
    }
  }

  static Map<String, Collection<CacheData>> stratifyCacheDataByGroup(Collection<CacheData> ungroupedCacheData) {
    Map<String, Collection<CacheData>> result = new HashMap<>();
    for (CacheData cacheData : ungroupedCacheData) {
      String key = cacheData.getId();
      Keys.CacheKey parsedKey = Keys.parseKey(key).orElseThrow(() -> new IllegalStateException("Cache data produced with illegal key format " + key));
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

  private static Optional<CacheData> invertSingleRelationship(String group, String key, String relationship) {
    Map<String, Collection<String>> relationships = new HashMap<>();
    relationships.put(group, Collections.singletonList(key));
    return Keys.parseKey(relationship).flatMap(k -> {
      Map<String, Object> attributes = new ImmutableMap.Builder<String, Object>()
          .put("name", k.getName())
          .build();
      return Optional.of(new DefaultCacheData(relationship, attributes, relationships));
    });
  }
}
