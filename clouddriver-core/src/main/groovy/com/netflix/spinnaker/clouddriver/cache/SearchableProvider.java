/*
 * Copyright 2015 Netflix, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.cache;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.provider.Provider;
import groovy.transform.Canonical;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public interface SearchableProvider extends Provider {

  /** Names of caches to search by default */
  Set<String> getDefaultCaches();

  /**
   * Map keyed by named cache to a template that produces a url for a search result.
   *
   * <p>The template will be supplied the result from calling parseKey on the search key
   */
  Map<String, String> getUrlMappingTemplates();

  /** SearchResultHydrators for cache types */
  Map<SearchableResource, SearchResultHydrator> getSearchResultHydrators();

  /** The parts of the key, if this Provider supports keys of this type, otherwise null. */
  Map<String, String> parseKey(String key);

  default Optional<KeyParser> getKeyParser() {
    return Optional.empty();
  }

  /**
   * Build a search term for querying.
   *
   * <p>If this SearchableProvider supplies a KeyParser then the search term is scoped to that
   * KeyParsers cloudProvider, otherwise injects a wildcard glob at the start.
   *
   * <p>Supplying a KeyParser to provide a CloudProviderId to scope the search more narrowly results
   * in improved search performance.
   */
  default String buildSearchTerm(String type, String queryTerm) {
    String prefix = getKeyParser().map(KeyParser::getCloudProvider).orElse("*");
    return prefix + ":" + type + ":*" + queryTerm + "*";
  }

  default boolean supportsSearch(String type, Map<String, String> filters) {
    final boolean filterMatch;
    if (filters == null || !filters.containsKey("cloudProvider")) {
      filterMatch = true;
    } else {
      filterMatch =
          getKeyParser()
              .map(
                  kp ->
                      kp.canParseType(type)
                          && kp.getCloudProvider().equals(filters.get("cloudProvider")))
              .orElse(true);
    }

    return filterMatch && hasAgentForType(type, getAgents());
  }

  static boolean hasAgentForType(String type, Collection<Agent> agents) {
    return agents.stream()
        .filter(CachingAgent.class::isInstance)
        .map(CachingAgent.class::cast)
        .anyMatch(
            ca ->
                ca.getProvidedDataTypes().stream().anyMatch(pdt -> pdt.getTypeName().equals(type)));
  }

  /**
   * A SearchResultHydrator provides a custom strategy for enhancing result data for a particular
   * cache type.
   */
  public static interface SearchResultHydrator {
    Map<String, String> hydrateResult(Cache cacheView, Map<String, String> result, String id);
  }

  @Canonical
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class SearchableResource {
    /** Lowercase name of a resource type. e.g. 'instances', 'load_balancers' */
    String resourceType;

    /** Lowercase name of the platform. e.g. 'aws', 'gce' */
    String platform;
  }
}
