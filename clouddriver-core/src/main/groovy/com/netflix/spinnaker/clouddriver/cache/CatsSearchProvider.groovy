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

package com.netflix.spinnaker.clouddriver.cache

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.search.SearchProvider
import com.netflix.spinnaker.clouddriver.search.SearchResultSet
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class CatsSearchProvider implements SearchProvider {

  private static final Logger log = LoggerFactory.getLogger(CatsSearchProvider)

  private final Cache cacheView
  private final List<SearchableProvider> providers
  private final List<String> defaultCaches
  private final Map<String, SearchableProvider.SearchResultHydrator> searchResultHydrators

  private final Map<String, Template> urlMappings

  @Autowired
  public CatsSearchProvider(Cache cacheView, List<SearchableProvider> providers) {
    this.cacheView = cacheView
    this.providers = providers
    defaultCaches = providers.defaultCaches.flatten()
    log.info("Enabled default caches: ${defaultCaches}")
    searchResultHydrators = providers.inject([:]) { Map acc, SearchableProvider prov ->
      acc.putAll(prov.searchResultHydrators)
      return acc
    }
    SimpleTemplateEngine tmpl = new SimpleTemplateEngine()
    urlMappings = providers.inject([:]) { Map mappings, SearchableProvider provider ->
      mappings.putAll(provider.urlMappingTemplates.collectEntries { [(it.key): tmpl.createTemplate(it.value)] })
      return mappings
    }
  }

  @Override
  String getPlatform() {
    return "aws" //TODO(cfieber) - need a better story around this
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
    // ensure we're only searching for types supported by the backing providers
    types = defaultCaches.intersect(types)

    List<String> matches = findMatches(query, types, filters)
    generateResultSet(query, matches, pageNumber, pageSize)
  }

  private SearchResultSet generateResultSet(String query, List<String> matches, Integer pageNumber, Integer pageSize) {
    List<String> resultPage = paginateResults(matches, pageSize, pageNumber)
    List<Map<String, String>> results = resultPage.findResults { String key ->
      Map<String, String> result = providers.findResult { it.parseKey(key) }
      if (result) {
        return searchResultHydrators.containsKey(result.type) ? searchResultHydrators[result.type].hydrateResult(cacheView, result, key) : result
      }
      return null
    }

    int filteredItems = resultPage.size() - results.size()

    SearchResultSet resultSet = new SearchResultSet(
      totalMatches: matches.size() - filteredItems,
      platform: getPlatform(),
      query: query,
      pageNumber: pageNumber,
      pageSize: pageSize,
      results: results
    )
    resultSet.results.each { Map<String, String> result ->
      if (!result.provider) {
        result.provider = getPlatform()
      }

      if (urlMappings.containsKey(result.type)) {
        def binding = [:]
        binding.putAll(result)
        result.url = urlMappings[result.type].make(binding).toString()
      }
    }
    resultSet
  }

  private List<String> findMatches(String q, List<String> toQuery, Map<String, String> filters) {
    log.info("Querying ${toQuery} for term: ${q}")
    String normalizedWord = q.toLowerCase()
    List<String> matches = new ArrayList<String>()
    toQuery.each { String cache ->
      matches.addAll(cacheView.filterIdentifiers(cache, "*:${cache}:*${normalizedWord}*").findAll { String key ->
        try {
          if (!filters) {
            return true
          }
          def item = cacheView.get(cache, key)
          filters.entrySet().every { filter ->
            if (item.relationships[filter.key]) {
              item.relationships[filter.key].find { it.indexOf(filter.value) != -1 }
            } else {
              item.attributes[filter.key] == filter.value
            }
          }
        } catch (Exception e) {
          log.warn("Failed on $cache:$key", e)
        }
      })
    }
    matches.sort { String a, String b ->
      def aKey = a.toLowerCase().substring(a.indexOf(':'))
      def bKey = b.toLowerCase().substring(b.indexOf(':'))
      def indexA = aKey.indexOf(q)
      def indexB = bKey.indexOf(q)
      return indexA == indexB ? aKey <=> bKey : indexA - indexB
    }
  }

  private static List<String> paginateResults(List<String> matches, Integer pageSize, Integer pageNumber) {
    log.info("Paginating ${matches.size()} results; page number: ${pageNumber}, items per page: ${pageSize}")
    Integer startingIndex = pageSize * (pageNumber - 1)
    Integer endIndex = Math.min(pageSize * pageNumber, matches.size())
    boolean hasResults = startingIndex < endIndex
    List<String> toReturn = hasResults ? matches[startingIndex..endIndex - 1] : new ArrayList<String>()
    toReturn
  }
}
