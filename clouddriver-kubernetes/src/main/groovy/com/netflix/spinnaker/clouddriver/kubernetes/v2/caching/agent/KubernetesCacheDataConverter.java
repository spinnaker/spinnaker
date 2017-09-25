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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifestSpinnakerRelationships;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.APPLICATION;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.CLUSTER;

@Slf4j
public class KubernetesCacheDataConverter {
  private static ObjectMapper mapper = new ObjectMapper();

  public static CacheData fromResource(String account, ObjectMapper mapper, Object resource) {
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

    KubernetesManifestSpinnakerRelationships spinnakerRelationships = KubernetesManifestAnnotater.getManifestRelationships(manifest);
    Map<String, Collection<String>> relationships = new HashMap<>();

    String application = spinnakerRelationships.getApplication();
    if (StringUtils.isEmpty(application)) {
      log.info("Skipping not-spinnaker-owned resource " + namespace + ":" + manifest.getFullResourceName());
      return null;
    }

    relationships.putAll(annotatedRelationships(account, namespace, spinnakerRelationships));
    // TODO(lwander) avoid overwriting keys here
    relationships.putAll(ownerReferenceRelationships(account, namespace, manifest.getOwnerReferences(mapper)));

    String key = Keys.infrastructure(apiVersion, kind, account, namespace, name);
    return new DefaultCacheData(key, attributes, relationships);
  }

  public static KubernetesManifest getManifest(CacheData cacheData) {
    return mapper.convertValue(cacheData.getAttributes().get("manifest"), KubernetesManifest.class);
  }

  static Map<String, Collection<String>> annotatedRelationships(String account, String namespace, KubernetesManifestSpinnakerRelationships spinnakerRelationships) {
    Map<String, Collection<String>> relationships = new HashMap<>();
    String application = spinnakerRelationships.getApplication();

    relationships.put(APPLICATION.toString(), Collections.singletonList(Keys.application(application)));

    String cluster = spinnakerRelationships.getCluster();
    if (!StringUtils.isEmpty(cluster)) {
      relationships.put(CLUSTER.toString(), Collections.singletonList(Keys.cluster(account, cluster)));
    }

    if (spinnakerRelationships.getLoadBalancers() != null) {
      for (String loadBalancer : spinnakerRelationships.getLoadBalancers()) {
        addSingleRelationship(relationships, account, namespace, loadBalancer);
      }
    }

    if (spinnakerRelationships.getSecurityGroups() != null) {
      for (String securityGroup : spinnakerRelationships.getSecurityGroups()) {
        addSingleRelationship(relationships, account, namespace, securityGroup);
      }
    }

    return relationships;
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
        result.add(invertSingleRelationship(group, key, relationship));
      }
    }

    return result;
  }

  static void logStratifiedCacheData(String agentType, Map<String, Collection<CacheData>> stratifiedCacheData) {
    for (Map.Entry<String, Collection<CacheData>> entry : stratifiedCacheData.entrySet()) {
      log.info(agentType + ": grouping " + entry.getKey() + " has " + entry.getValue().size() + " entries");
    }
  }

  static Map<String, Collection<CacheData>> stratifyCacheDataByGroup(List<CacheData> ungroupedCacheData) {
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

  private static CacheData invertSingleRelationship(String group, String key, String relationship) {
    Map<String, Collection<String>> relationships = new HashMap<>();
    relationships.put(group, Collections.singletonList(key));
    return new DefaultCacheData(relationship, null, relationships);
  }
}
