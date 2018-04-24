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

import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.search.SearchProvider
import com.netflix.spinnaker.clouddriver.search.SearchResultSet
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

import javax.annotation.PostConstruct
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import static com.netflix.spinnaker.clouddriver.cache.SearchableProvider.SearchableResource

class CatsSearchProvider implements SearchProvider, Runnable {

  private static final Logger log = LoggerFactory.getLogger(CatsSearchProvider)

  private final CatsInMemorySearchProperties catsInMemorySearchProperties
  private final Cache cacheView
  private final List<SearchableProvider> providers
  private final List<String> defaultCaches
  private final Map<SearchableResource, SearchableProvider.SearchResultHydrator> searchResultHydrators

  private final Map<String, Template> urlMappings

  private AtomicReference<Map<String, Collection<Map<String, String>>>> cachedIdentifiersByType = new AtomicReference(
    [:]
  )

  private final FiatPermissionEvaluator permissionEvaluator
  private final List<KeyParser> keyParsers;
  private final List<KeyProcessor> keyProcessors;

  private final ScheduledExecutorService scheduledExecutorService

  CatsSearchProvider(CatsInMemorySearchProperties catsInMemorySearchProperties,
                     Cache cacheView,
                     List<SearchableProvider> providers,
                     Optional<FiatPermissionEvaluator> permissionEvaluator,
                     Optional<List<KeyParser>> keyParsers,
                     Optional<List<KeyProcessor>> keyProcessors) {
    this(catsInMemorySearchProperties, cacheView, providers)

    this.permissionEvaluator = permissionEvaluator.orElse(null)
    this.keyParsers = keyParsers.orElse(Collections.emptyList())
    this.keyProcessors = keyProcessors.orElse(Collections.emptyList())

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

    if (catsInMemorySearchProperties.enabled) {
      scheduledExecutorService = Executors.newScheduledThreadPool(1)
    }
  }

  @VisibleForTesting
  CatsSearchProvider(CatsInMemorySearchProperties catsInMemorySearchProperties,
                     Cache cacheView,
                     List<SearchableProvider> providers) {
    this.catsInMemorySearchProperties = catsInMemorySearchProperties
    this.cacheView = cacheView
    this.providers = providers
  }

  @PostConstruct
  void scheduleRefresh() {
    if (scheduledExecutorService) {
      scheduledExecutorService.scheduleWithFixedDelay(this, 0, catsInMemorySearchProperties.refreshIntervalSeconds, TimeUnit.SECONDS)
    }
  }

  /**
   * Periodically refresh cache identifiers that can then be searched over in-memory vs. in-redis.
   *
   * This is beneficial for sets (like instances) that may have hundreds of thousands of keys.
   */
  @Override
  void run() {
    log.info("Refreshing Cached Identifiers (instances)")
    def instanceIdentifiers = cacheView.getIdentifiers("instances")?.findResults { String key ->
      providers.findResults {
        def v = it.parseKey(key)
        if (v) {
          v["_id"] = key
        }

        return v?.collectEntries {
          [it.key, it.value.toLowerCase()]
        }
      }[0]
    }

    if (instanceIdentifiers) {
      cachedIdentifiersByType.set(["instances": instanceIdentifiers])
    }

    log.info("Refreshed Cached Identifiers (instances)")
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
    if (permissionEvaluator) {
      Authentication auth = SecurityContextHolder.context.authentication

      matches = new ArrayList(matches).findResults { String key ->
        Map<String, String> result = providers.findResult { it.parseKey(key) }
        if (!result) {
          log.warn("No supporting provider found for key (key: ${key})")
          return key
        }

        boolean canView = true
        if (result.application) {
          canView = permissionEvaluator.hasPermission(auth, result.application as String, 'APPLICATION', 'READ')
        }
        if (canView && result.account) {
          canView = permissionEvaluator.hasPermission(auth, result.account as String, 'ACCOUNT', 'READ')
        }
        return canView ? key : null
      }
    }
    generateResultSet(query, matches, pageNumber, pageSize)
  }

  private SearchResultSet generateResultSet(String query, List<String> matches, Integer pageNumber, Integer pageSize) {
    List<String> resultPage = paginateResults(matches, pageSize, pageNumber)
    List<Map<String, String>> results = resultPage.findResults { String key ->
      Map<String, String> result = providers.findResult { it.parseKey(key) }
      if (result) {
        def resultResource = new SearchableResource(resourceType: result.type?.toLowerCase(), platform: result.provider?.toLowerCase())
        if (resultResource in searchResultHydrators) {
          return searchResultHydrators[(resultResource)].hydrateResult(cacheView, result, key)
        } else {
          return result
        }
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

  private List<String> findMatches(String q, List<String> cachesToQuery, Map<String, String> filters) {

    if (!q && keyParsers) {
      // no keyword search so find sensible default value to set for searching
      Set<String> filterKeys = filters.keySet()
      keyParsers.find {
        KeyParser parser = it
        String field = filterKeys.find {String field -> parser.canParseField(field) }
        if (field) {
          q = filters.get(field)
          return true
        }
      }
      log.info("no query string specified, looked for sensible default and found: ${q}")
    }

    log.info("Querying ${cachesToQuery} for term: ${q}")
    String normalizedWord = q.toLowerCase()
    List<String> matches = cachesToQuery.collect { String cache ->
      List<KeyProcessor> keyProcessors = (this.keyProcessors ?: []).findAll { it.canProcess(cache) }

      // if the key represented in the cache doesn't actually exist, don't process it
      Closure keyExists = { String key ->
        boolean exists = keyProcessors.empty || keyProcessors.any { it.exists(key) }
        if (!exists) {
          log.warn("found ${cache} key that did not exist: ${key}")
        }
        return exists
      }

      Closure filtersMatch = { String key ->
        try {
          if (!filters) {
            return true
          }

          if (keyParsers) {
            KeyParser parser = keyParsers.find { it.cloudProvider == filters.cloudProvider && it.canParseType(cache) }
            if (parser) {
              Map<String, String> parsed = parser.parseKey(key)
              return filters.entrySet().every { filter ->
                String[] vals = filter.value.split(',')
                filter.key == 'cloudProvider' || vals.contains(parsed[filter.key]) ||
                  vals.contains(parsed[parser.getNameMapping(cache)])
              }
            } else {
              log.warn("No parser found for $cache:$key")
            }
          }
        } catch (Exception e) {
          log.warn("Failed on $cache:$key", e)
        }
      }

      def identifiers
      if (cachedIdentifiersByType.get().containsKey(cache)) {
        /**
         * Attempt an exact match of the query against any attribute of an instance key (account, region, etc.).
         *
         * This is not 100% consistent with doing `*:${cache}:*${normalizedWord}*` in redis _but_ for instances it
         * should be sufficient.
         */
        def identifiersForCache = cachedIdentifiersByType.get().get(cache)
        identifiers = identifiersForCache.findAll { identifier ->
          identifier.values().contains(normalizedWord)
        }.collect { it["_id"] }
      } else {
        identifiers = cacheView
          .filterIdentifiers(cache, "*:${cache}:*${normalizedWord}*")
      }

      return identifiers
        .findAll(keyExists)
        .findAll(filtersMatch)
    }.flatten()

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
