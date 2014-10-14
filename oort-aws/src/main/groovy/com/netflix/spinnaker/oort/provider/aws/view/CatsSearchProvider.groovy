/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.provider.aws.view

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.oort.search.SearchProvider
import com.netflix.spinnaker.oort.search.SearchResultSet
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.CLUSTERS
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.SERVER_GROUPS

@Component
class CatsSearchProvider implements SearchProvider {

  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, String>>() {}

  static final List<String> defaultCaches = [
    APPLICATIONS.ns,
    LOAD_BALANCERS.ns,
    CLUSTERS.ns,
    SERVER_GROUPS.ns,
    INSTANCES.ns
  ]

  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  public CatsSearchProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }


  @Override
  String getPlatform() {
    return "aws"
  }

  @Override
  SearchResultSet search(String query, Integer pageNumber, Integer pageSize) {
    search(query, defaultCaches, pageNumber, pageSize)
  }

  @Override
  SearchResultSet search(String query, Integer pageNumber, Integer pageSize, Map<String, String> filters) {
    search(query, defaultCaches, pageNumber, pageSize, filters)
  }

  @Override
  SearchResultSet search(String query, List<String> types, Integer pageNumber, Integer pageSize) {
    search(query, types, pageNumber, pageSize, Collections.emptyMap())
  }

  @Override
  SearchResultSet search(String query, List<String> types, Integer pageNumber, Integer pageSize, Map<String, String> filters) {
    query = query?.toLowerCase() ?: ''
    int skipResults = (pageNumber - 1) * pageSize
    int needResults = pageSize
    List<CacheData> results = []
    for (String type : types) {
      if (needResults) {
        def thisType = cacheView.getAll(type).findAll { matchesQuery(query, it) && matchesFilters(filters, it) }.sort { it.id }

        def toAdd = thisType.drop(skipResults).take(needResults)
        if (skipResults > 0) {
          skipResults = Math.max(0, skipResults - thisType.size())
        }
        needResults -= toAdd
        results.addAll(toAdd)
      }
    }
    List<Map<String, String>> searchResults = results.collect { objectMapper.convertValue(it.attributes, ATTRIBUTES) }
    new SearchResultSet(totalMatches: 69696969, pageNumber: pageNumber, pageSize: pageSize, platform: 'aws', query: query, results: searchResults)
  }

  boolean matchesQuery(String query, CacheData candidate) {
    if (!query) {
      return true
    }

    candidate.attributes.values().any { it.toString().toLowerCase().indexOf(query) != -1 }
  }

  boolean matchesFilters(Map<String, String> filters, CacheData candidate) {
    if (!filters) {
      return true
    }

    filters.every { String attribute, String expectedValue ->
      (candidate.attributes.containsKey(attribute) && candidate.attributes.get(attribute).toString() == expectedValue) ||
        (candidate.relationships.containsKey(attribute) && candidate.relationships.get(attribute).find { it.indexOf(expectedValue) != -1 })
    }
  }
}
