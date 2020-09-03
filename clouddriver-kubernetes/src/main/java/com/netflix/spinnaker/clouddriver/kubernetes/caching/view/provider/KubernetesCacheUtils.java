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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.ImmutableSetMultimap.flatteningToImmutableSetMultimap;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.data.KubernetesCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@NonnullByDefault
class KubernetesCacheUtils {
  private final Cache cache;
  private final KubernetesSpinnakerKindMap kindMap;
  private final KubernetesAccountResolver resourcePropertyResolver;

  @Autowired
  public KubernetesCacheUtils(
      Cache cache,
      KubernetesSpinnakerKindMap kindMap,
      KubernetesAccountResolver resourcePropertyResolver) {
    this.cache = cache;
    this.kindMap = kindMap;
    this.resourcePropertyResolver = resourcePropertyResolver;
  }

  Collection<CacheData> getAllKeys(String type) {
    return cache.getAll(type);
  }

  Collection<String> getAllKeysMatchingPattern(String type, String key) {
    return cache.filterIdentifiers(type, key);
  }

  Collection<CacheData> getAllDataMatchingPattern(String type, String key) {
    return cache.getAll(type, getAllKeysMatchingPattern(type, key));
  }

  Optional<CacheData> getSingleEntry(String type, String key) {
    return Optional.ofNullable(cache.get(type, key));
  }

  /**
   * Given an account, a namespace, and a resource name, returns the {@link CacheData} entry for
   * that item.
   *
   * <p>If the resource name cannot be parsed into a kind and a name, or if there is no entry in the
   * cache for the requested item, returjns an empty {@link Optional}.
   *
   * @param account the account of the requested item
   * @param namespace the namespace for the requested item, which can be empty for a cluster-scoped
   *     resource
   * @param name the full name of the requested item in the form "kind name" (ex: "pod my-pod-abcd")
   * @return An optional containg the requested {@link CacheData} item, or an empty {@link Optional}
   *     if the item is not found.
   */
  Optional<CacheData> getSingleEntry(String account, String namespace, String name) {
    KubernetesCoordinates coords;
    try {
      coords = KubernetesCoordinates.builder().namespace(namespace).fullResourceName(name).build();
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    return getSingleEntry(
        coords.getKind().toString(), Keys.InfrastructureCacheKey.createKey(account, coords));
  }

  Optional<CacheData> getSingleEntryWithRelationships(
      String type, String key, RelationshipCacheFilter cacheFilter) {
    return Optional.ofNullable(cache.get(type, key, cacheFilter));
  }

  /** Gets the keys for all relationships of a given Spinnaker kind for a CacheData item. */
  ImmutableCollection<String> getRelationshipKeys(
      CacheData cacheData, SpinnakerKind spinnakerKind) {
    return relationshipTypes(spinnakerKind)
        .flatMap(t -> relationshipKeys(cacheData, t))
        .collect(toImmutableSet());
  }

  /** Gets the keys for all relationships of a given type for a collection of CacheData items. */
  private ImmutableMultimap<String, String> getRelationshipKeys(
      Collection<CacheData> cacheData, String type) {
    return cacheData.stream()
        .collect(
            flatteningToImmutableSetMultimap(CacheData::getId, cd -> relationshipKeys(cd, type)));
  }

  /** Gets the data for all relationships of a given type for a CacheData item. */
  Collection<CacheData> getRelationships(CacheData cacheData, String relationshipType) {
    return cache.getAll(
        relationshipType, relationshipKeys(cacheData, relationshipType).collect(toImmutableSet()));
  }

  /** Gets the data for all relationships of a given Spinnaker kind for a single CacheData item. */
  ImmutableCollection<CacheData> getRelationships(
      CacheData cacheData, SpinnakerKind spinnakerKind) {
    return getRelationships(ImmutableList.of(cacheData), spinnakerKind).get(cacheData.getId());
  }

  /**
   * Gets the data for all relationships of a given Spinnaker kind for a collection of CacheData
   * items.
   */
  ImmutableMultimap<String, CacheData> getRelationships(
      Collection<CacheData> cacheData, SpinnakerKind spinnakerKind) {
    ImmutableListMultimap.Builder<String, CacheData> result = ImmutableListMultimap.builder();
    relationshipTypes(spinnakerKind)
        .forEach(type -> result.putAll(getRelationships(cacheData, type)));
    return result.build();
  }

  /** Gets the data for all relationships of a given type for a collection of CacheData items. */
  private Multimap<String, CacheData> getRelationships(
      Collection<CacheData> cacheData, String type) {
    ImmutableMultimap<String, String> relKeys = getRelationshipKeys(cacheData, type);

    // Prefetch the cache data for all relationships. This is to avoid making a separate call
    // the cache for each of the source items.
    // Note that relKeys.values() is not deduplicated; we'll defer to the cache implementation
    // to decide whether it's worth deduplicating before fetching data. In the event that we
    // do get back duplicates, we'll just keep the first for each key.
    ImmutableMap<String, CacheData> relData =
        cache.getAll(type, relKeys.values()).stream()
            .collect(toImmutableMap(CacheData::getId, cd -> cd, (cd1, cd2) -> cd1));

    // Note that the filterValues here is important to handle race conditions where a relationship
    // is deleted by the time we look it up; in that case, relData might not contain the data for
    // a requested key.
    return Multimaps.filterValues(
        Multimaps.transformValues(relKeys, relData::get), Objects::nonNull);
  }

  /** Returns a stream of all relationships of a given type for a given CacheData. */
  private Stream<String> relationshipKeys(CacheData cacheData, String type) {
    Collection<String> relationships = cacheData.getRelationships().get(type);
    // Avoiding creating an Optional here as this is deeply nested in performance-sensitive code.
    if (relationships == null) {
      return Stream.empty();
    }
    return relationships.stream();
  }

  /** Given a spinnaker kind, returns a stream of the relationship types representing that kind. */
  private Stream<String> relationshipTypes(SpinnakerKind spinnakerKind) {
    return kindMap.translateSpinnakerKind(spinnakerKind).stream().map(KubernetesKind::toString);
  }

  /**
   * Given a collection of Spinnaker kinds, return a cache filter restricting relationships to those
   * kinds.
   */
  RelationshipCacheFilter getCacheFilter(Collection<SpinnakerKind> spinnakerKinds) {
    return RelationshipCacheFilter.include(
        spinnakerKinds.stream().flatMap(this::relationshipTypes).toArray(String[]::new));
  }

  /**
   * Returns a Predicate that returns true the first time it sees a CacheData with a given id, and
   * false all subsequent times.
   */
  Predicate<CacheData> distinctById() {
    Set<String> seen = new HashSet<>();
    return cd -> seen.add(cd.getId());
  }

  KubernetesHandler getHandler(KubernetesCacheData cacheData) {
    Keys.InfrastructureCacheKey key =
        (Keys.InfrastructureCacheKey) Keys.parseKey(cacheData.primaryData().getId()).get();
    // TODO(ezimanyi): The kind is also stored directly on the cache data; get it from there instead
    // of reading it from the manifest.
    KubernetesKind kind =
        KubernetesCacheDataConverter.getManifest(cacheData.primaryData()).getKind();
    return resourcePropertyResolver
        .getResourcePropertyRegistry(key.getAccount())
        .get(kind)
        .getHandler();
  }
}
