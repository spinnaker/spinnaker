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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKey;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.search.SearchProvider;
import com.netflix.spinnaker.clouddriver.search.SearchResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KubernetesV2SearchProvider implements SearchProvider {
  private final KubernetesCacheUtils cacheUtils;
  private final ObjectMapper mapper;
  private final KubernetesSpinnakerKindMap kindMap;
  private final KubernetesAccountResolver resourcePropertyResolver;
  private final List<String> defaultTypes;
  private final Set<String> logicalTypes;
  private final Set<String> allCaches;

  @Autowired
  public KubernetesV2SearchProvider(
      KubernetesCacheUtils cacheUtils,
      KubernetesSpinnakerKindMap kindMap,
      ObjectMapper objectMapper,
      KubernetesAccountResolver resourcePropertyResolver) {
    this.cacheUtils = cacheUtils;
    this.mapper = objectMapper;
    this.kindMap = kindMap;
    this.resourcePropertyResolver = resourcePropertyResolver;

    this.defaultTypes =
        kindMap.allKubernetesKinds().stream()
            .map(KubernetesKind::toString)
            .collect(Collectors.toList());
    this.logicalTypes =
        Arrays.stream(LogicalKind.values()).map(LogicalKind::toString).collect(Collectors.toSet());

    this.allCaches = new HashSet<>(defaultTypes);
    this.allCaches.addAll(logicalTypes);
  }

  @Override
  public String getPlatform() {
    return KubernetesCloudProvider.ID;
  }

  @Override
  public SearchResultSet search(String query, Integer pageNumber, Integer pageSize) {
    return search(query, defaultTypes, pageNumber, pageSize);
  }

  @Override
  public SearchResultSet search(
      String query, Integer pageNumber, Integer pageSize, Map<String, String> filters) {
    return search(query, defaultTypes, pageNumber, pageSize, filters);
  }

  @Override
  public SearchResultSet search(
      String query, List<String> types, Integer pageNumber, Integer pageSize) {
    return search(query, types, pageNumber, pageSize, Collections.emptyMap());
  }

  @Override
  public SearchResultSet search(
      String query,
      List<String> types,
      Integer pageNumber,
      Integer pageSize,
      Map<String, String> filters) {
    log.info("Querying {} for term {}", types, query);
    List<Map<String, Object>> results =
        paginateResults(getMatches(query, types, filters), pageSize, pageNumber);

    return SearchResultSet.builder()
        .pageNumber(pageNumber)
        .pageSize(pageSize)
        .platform(getPlatform())
        .query(query)
        .totalMatches(results.size())
        .results(results)
        .build();
  }

  private Map<String, Object> convertKeyToMap(String key) {
    Optional<Keys.CacheKey> optional = Keys.parseKey(key);
    if (!optional.isPresent()) {
      return null;
    }

    Keys.CacheKey parsedKey = optional.get();
    Map<String, Object> result;
    String type;

    if (parsedKey instanceof Keys.InfrastructureCacheKey) {
      Keys.InfrastructureCacheKey infraKey = (Keys.InfrastructureCacheKey) parsedKey;
      type = kindMap.translateKubernetesKind(infraKey.getKubernetesKind()).toString();

      KubernetesResourceProperties properties =
          resourcePropertyResolver
              .getResourcePropertyRegistry(infraKey.getAccount())
              .get(infraKey.getKubernetesKind());

      result = properties.getHandler().hydrateSearchResult(infraKey);
    } else if (parsedKey instanceof Keys.LogicalKey) {
      Keys.LogicalKey logicalKey = (Keys.LogicalKey) parsedKey;

      result = mapper.convertValue(logicalKey, new TypeReference<Map<String, Object>>() {});
      result.put(logicalKey.getLogicalKind().singular(), logicalKey.getName());
      type = logicalKey.getGroup();
    } else {
      log.warn("Unknown key type " + parsedKey + ", ignoring.");
      return null;
    }

    result.put("type", type);
    return result;
  }

  private static Stream<KeyRelationship> getMatchingRelationships(
      CacheData cacheData, Set<String> typesToSearch) {
    Keys.CacheKey cacheKey = Keys.parseKey(cacheData.getId()).orElse(null);
    if (!(cacheKey instanceof LogicalKey)) {
      return Stream.empty();
    }
    Map<String, Collection<String>> relationships = cacheData.getRelationships();
    return typesToSearch.stream()
        .map(relationships::get)
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .filter(Objects::nonNull)
        .map(k -> new KeyRelationship(k, (LogicalKey) cacheKey));
  }

  private Map<String, List<Keys.LogicalKey>> getKeysRelatedToLogicalMatches(
      String matchQuery, Set<String> typesToSearch) {
    return logicalTypes.stream()
        .map(type -> cacheUtils.getAllDataMatchingPattern(type, matchQuery))
        .flatMap(Collection::stream)
        .flatMap(cd -> getMatchingRelationships(cd, typesToSearch))
        .collect(
            Collectors.groupingBy(
                KeyRelationship::getInfrastructureKey,
                Collectors.mapping(KeyRelationship::getLogicalKey, Collectors.toList())));
  }

  @Getter
  @RequiredArgsConstructor
  private static class KeyRelationship {
    private final String infrastructureKey;
    private final Keys.LogicalKey logicalKey;
  }

  // TODO(lwander): use filters
  private List<Map<String, Object>> getMatches(
      String query, List<String> types, Map<String, String> filters) {
    String matchQuery = String.format("*%s*", query.toLowerCase());
    Set<String> typesToSearch = new HashSet<>(types);

    // We add k8s versions of Spinnaker types here to ensure that (for example) replica sets are
    // returned when server groups are requested.
    typesToSearch.addAll(
        types.stream()
            .map(
                t -> {
                  try {
                    return SpinnakerKind.fromString(t);
                  } catch (IllegalArgumentException e) {
                    return null;
                  }
                })
            .filter(k -> k != null && k != SpinnakerKind.UNCLASSIFIED)
            .map(kindMap::translateSpinnakerKind)
            .flatMap(Collection::stream)
            .map(KubernetesKind::toString)
            .collect(Collectors.toSet()));

    // Remove caches that we can't search
    typesToSearch.retainAll(allCaches);

    if (typesToSearch.isEmpty()) {
      return Collections.emptyList();
    }

    // Search caches directly
    Stream<Map<String, Object>> directResults =
        typesToSearch.stream()
            .map(type -> cacheUtils.getAllKeysMatchingPattern(type, matchQuery))
            .flatMap(Collection::stream)
            .map(this::convertKeyToMap);

    // Search 'logical' caches (clusters, apps) for indirect matches
    Stream<Map<String, Object>> relatedResults =
        getKeysRelatedToLogicalMatches(matchQuery, typesToSearch).entrySet().stream()
            .map(
                kv -> {
                  Map<String, Object> result = convertKeyToMap(kv.getKey());
                  if (result != null) {
                    kv.getValue()
                        .forEach(k -> result.put(k.getLogicalKind().singular(), k.getName()));
                  }
                  return result;
                });

    return Stream.concat(directResults, relatedResults)
        .filter(Objects::nonNull)
        .filter(result -> typesToSearch.contains(result.get("group")))
        .collect(Collectors.toList());
  }

  private static <T> List<T> paginateResults(
      List<T> matches, Integer pageSize, Integer pageNumber) {
    Integer startingIndex = pageSize * (pageNumber - 1);
    Integer endIndex = Math.min(pageSize * pageNumber, matches.size());
    return startingIndex < endIndex ? matches.subList(startingIndex, endIndex) : new ArrayList<>();
  }
}
