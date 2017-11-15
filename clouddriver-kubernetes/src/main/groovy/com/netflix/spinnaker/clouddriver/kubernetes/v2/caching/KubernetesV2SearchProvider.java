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
  final private List<String> defaultTypes;
  final private Set<LogicalKind> logicalTypes;

  @Autowired
  public KubernetesV2SearchProvider(KubernetesCacheUtils cacheUtils, KubernetesSpinnakerKindMap kindMap, ObjectMapper mapper) {
    this.cacheUtils = cacheUtils;
    this.mapper = mapper;
    this.kindMap = kindMap;

    this.defaultTypes = kindMap.allKubernetesKinds()
        .stream()
        .map(KubernetesKind::toString)
        .collect(Collectors.toList());
    this.logicalTypes = Arrays.stream(LogicalKind.values())
        .collect(Collectors.toSet());
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
    Map<String, Object> result = mapper.convertValue(parsedKey, new TypeReference<Map<String, Object>>() {});

    if (parsedKey instanceof Keys.InfrastructureCacheKey) {
      Keys.InfrastructureCacheKey infraKey = (Keys.InfrastructureCacheKey) parsedKey;
      result.put("type", kindMap.translateKubernetesKind(infraKey.getKubernetesKind()).toString());
      result.put("region", infraKey.getNamespace());
    } else {
      result.put("type", parsedKey.getGroup());
    }

    return result;
  }

  private Map<String, List<String>> getKeysRelatedToLogicalMatches(String matchQuery) {
    return logicalTypes.stream()
        .map(type -> cacheUtils.getAllDataMatchingPattern(type.toString(), matchQuery)
            .stream()
            .map(e -> e.getRelationships()
                .values()
                .stream()
                .map(Collection::stream)
                .flatMap(x -> x)
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

  private List<Map<String, Object>> getMatches(String query, List<String> types, Map<String, String> filters) {
    String matchQuery = String.format("*%s*", query.toLowerCase());
    Set<String> typeSet = new HashSet<>(types);

    List<Map<String, Object>> results = types.stream()
        .map(type -> cacheUtils.getAllKeysMatchingPattern(type, matchQuery))
        .map(Collection::stream)
        .flatMap(x -> x)
        .map(this::convertKeyToMap)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

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
                  .forEach(k -> {
                    result.put(k.getLogicalKind().singular(), k.getName());
                  });

              return result;
            }
        )
        .filter(r -> typeSet.contains(r.get("type")))
        .collect(Collectors.toList()));


    log.info("Found {} keys matching {}", results.size(), query);

    return results;
  }

  private static <T> List<T> paginateResults(List<T> matches, Integer pageSize, Integer pageNumber) {
    Integer startingIndex = pageSize * (pageNumber - 1);
    Integer endIndex = Math.min(pageSize * pageNumber, matches.size());
    return startingIndex < endIndex ? matches.subList(startingIndex, endIndex) : new ArrayList<>();
  }
}
