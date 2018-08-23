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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKey;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesCacheUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.search.SearchProvider;
import com.netflix.spinnaker.clouddriver.search.SearchResultSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

@Component
@Slf4j
public class KubernetesV2SearchProvider implements SearchProvider {
  final private KubernetesCacheUtils cacheUtils;
  final private ObjectMapper mapper;
  final private KubernetesSpinnakerKindMap kindMap;
  final private KubernetesResourcePropertyRegistry registry;
  final private List<String> defaultTypes;
  final private Set<String> logicalTypes;
  final private Set<String> allCaches;

  @Autowired
  public KubernetesV2SearchProvider(KubernetesCacheUtils cacheUtils,
      KubernetesSpinnakerKindMap kindMap,
      ObjectMapper objectMapper,
      KubernetesResourcePropertyRegistry registry) {
    this.cacheUtils = cacheUtils;
    this.mapper = objectMapper;
    this.kindMap = kindMap;
    this.registry = registry;

    this.defaultTypes = kindMap.allKubernetesKinds()
        .stream()
        .map(KubernetesKind::toString)
        .collect(Collectors.toList());
    this.logicalTypes = Arrays.stream(LogicalKind.values())
        .map(LogicalKind::toString)
        .collect(Collectors.toSet());

    this.allCaches = new HashSet<>(defaultTypes);
    this.allCaches.addAll(logicalTypes);
  }

  @Override
  public String getPlatform() {
    return KubernetesCloudProvider.getID();
  }

  @Override
  public SearchResultSet search(String query, Integer pageNumber, Integer pageSize) {
    return search(query, defaultTypes, pageNumber, pageSize);
  }

  @Override
  public SearchResultSet search(String query, Integer pageNumber, Integer pageSize, Map<String, String> filters) {
    return search(query, defaultTypes, pageNumber, pageSize, filters);
  }

  @Override
  public SearchResultSet search(String query, List<String> types, Integer pageNumber, Integer pageSize) {
    return search(query, types, pageNumber, pageSize, Collections.emptyMap());
  }

  @Override
  public SearchResultSet search(String query, List<String> types, Integer pageNumber, Integer pageSize, Map<String, String> filters) {
    log.info("Querying {} for term {}", types, query);
    List<Map<String, Object>> results = paginateResults(getMatches(query, types, filters), pageSize, pageNumber);

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

      KubernetesResourceProperties properties = registry.get(infraKey.getAccount(), infraKey.getKubernetesKind());
      if (properties == null) {
        log.warn("No hydrator for type {}, this is possibly a developer error", infraKey.getKubernetesKind());
        return null;
      }

      result = properties.getHandler().hydrateSearchResult(infraKey, cacheUtils);
    } else if (parsedKey instanceof Keys.LogicalKey) {
      Keys.LogicalKey logicalKey = (Keys.LogicalKey) parsedKey;

      result = mapper.convertValue(parsedKey, new TypeReference<Map<String, Object>>() {});
      result.put(logicalKey.getLogicalKind().singular(), logicalKey.getName());
      type = parsedKey.getGroup();
    } else {
      log.warn("Unknown key type " + parsedKey + ", ignoring.");
      return null;
    }

    result.put("type", type);
    return result;
  }

  private Map<String, List<String>> getKeysRelatedToLogicalMatches(String matchQuery) {
    return logicalTypes.stream()
        .map(type -> cacheUtils.getAllDataMatchingPattern(type, matchQuery)
            .stream()
            .map(e -> e.getRelationships()
                .values()
                .stream()
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(k -> new ImmutablePair<>(k, e.getId()))
            ).flatMap(x -> x)
        ).flatMap(x -> x)
        .collect(
            Collectors.groupingBy(Pair::getLeft,
                Collectors.reducing(
                    Collections.emptyList(),
                    i -> Collections.singletonList(i.getRight()),
                    (a, b) -> {
                      List<String> res = new ArrayList<>();
                      res.addAll(a);
                      res.addAll(b);
                      return res;
                    }
                )
            )
        );
  }

  // TODO(lwander): use filters
  private List<Map<String, Object>> getMatches(String query, List<String> types, Map<String, String> filters) {
    String matchQuery = String.format("*%s*", query.toLowerCase());
    Set<String> typeSet = new HashSet<>(types);

    // We add k8s versions of Spinnaker types here to ensure that (for example) replica sets are returned when server groups are requested.
    typeSet.addAll(types.stream()
        .map(t -> {
          try {
            return KubernetesSpinnakerKindMap.SpinnakerKind.fromString(t);
          } catch (IllegalArgumentException e) {
            return null;
          }
        }).filter(Objects::nonNull)
        .map(kindMap::translateSpinnakerKind)
        .flatMap(Collection::stream)
        .map(KubernetesKind::toString)
        .collect(Collectors.toSet())
    );

    // Remove caches that we can't search
    typeSet.retainAll(allCaches);

    // Search caches directly
    List<Map<String, Object>> results = typeSet.stream()
        .map(type -> cacheUtils.getAllKeysMatchingPattern(type, matchQuery))
        .flatMap(Collection::stream)
        .map(this::convertKeyToMap)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    // Search 'logical' caches (clusters, apps) for indirect matches
    Map<String, List<String>> keyToAllLogicalKeys = getKeysRelatedToLogicalMatches(matchQuery);
    results.addAll(keyToAllLogicalKeys.entrySet().stream()
        .map(kv -> {
              Map<String, Object> result = convertKeyToMap(kv.getKey());
              if (result == null) {
                return null;
              }

              kv.getValue().stream()
                  .map(Keys::parseKey)
                  .filter(Optional::isPresent)
                  .map(Optional::get)
                  .filter(LogicalKey.class::isInstance)
                  .map(k -> (LogicalKey) k)
                  .forEach(k -> result.put(k.getLogicalKind().singular(), k.getName()));

              return result;
            }
        )
        .filter(Objects::nonNull)
        .collect(Collectors.toList()));

    results = results.stream()
        .filter(r -> typeSet.contains(r.get("type")) || typeSet.contains(r.get("group")))
        .collect(Collectors.toList());

    return results;
  }

  private static <T> List<T> paginateResults(List<T> matches, Integer pageSize, Integer pageNumber) {
    Integer startingIndex = pageSize * (pageNumber - 1);
    Integer endIndex = Math.min(pageSize * pageNumber, matches.size());
    return startingIndex < endIndex ? matches.subList(startingIndex, endIndex) : new ArrayList<>();
  }
}
