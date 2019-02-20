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
import com.netflix.spinnaker.cats.provider.ProviderRegistry
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
  private final ProviderRegistry providerRegistry



  private final AtomicReference<Map<String, Collection<String>>> cachedIdentifiersByType = new AtomicReference(
    [:]
  )

  private final FiatPermissionEvaluator permissionEvaluator
  private final List<KeyParser> keyParsers

  private final ScheduledExecutorService scheduledExecutorService

  CatsSearchProvider(CatsInMemorySearchProperties catsInMemorySearchProperties,
                     Cache cacheView,
                     List<SearchableProvider> providers,
                     ProviderRegistry providerRegistry,
                     Optional<FiatPermissionEvaluator> permissionEvaluator,
                     Optional<List<KeyParser>> keyParsers) {
    this.catsInMemorySearchProperties = catsInMemorySearchProperties
    this.cacheView = cacheView
    this.providers = providers

    this.permissionEvaluator = permissionEvaluator.orElse(null)
    this.keyParsers = keyParsers.orElse(Collections.emptyList())
    this.providerRegistry = providerRegistry

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

  CatsSearchProvider(CatsInMemorySearchProperties catsInMemorySearchProperties,
                     Cache cacheView,
                     List<SearchableProvider> providers,
                     ProviderRegistry providerRegistry) {
    this(catsInMemorySearchProperties, cacheView, providers, providerRegistry, Optional.empty(), Optional.empty())

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
    try {
      log.info("Refreshing Cached Identifiers (instances)")
      def instanceIdentifiers = providers.findAll { provider ->
        provider.supportsSearch('instances', Collections.emptyMap())
      }.collect { provider ->
        def cache = providerRegistry.getProviderCache(provider.getProviderName())
        return cache.getIdentifiers("instances").findResults { key ->
          // Even though we don't need the parsed Map, we should still allow the provider to reject invalid keys
          if (provider.parseKey(key))
            return key?.toLowerCase()
        }
      }.flatten()

      if (instanceIdentifiers) {
        cachedIdentifiersByType.set(["instances": instanceIdentifiers])
      }

      log.info("Refreshed Cached Identifiers (found ${instanceIdentifiers.size()} instances)")
    } catch (Exception e) {
      log.error("Unable to refresh cached identifiers (instances)", e)
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

    if (!q && keyParsers && filters) {
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

      if (q) {
        log.info(
          "no query string specified, looked for sensible default and found: {} (cachesToQuery: {})",
          q,
          cachesToQuery
        )
      } else {
        log.info("no query string specified and no sensible default found (cachesToQuery: {})", cachesToQuery)
        return []
      }
    }

    log.info("Querying ${cachesToQuery} for term: ${q}")
    String normalizedWord = q.toLowerCase()
    List<String> matches = cachesToQuery.collect { String cache ->
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
                filter.key == 'cloudProvider' || parsed &&
                  ((parsed.containsKey(filter.key) && vals.contains(parsed[filter.key])) ||
                  (parsed.containsKey(parser.getNameMapping(cache)) && vals.contains(parsed[parser.getNameMapping(cache)])))
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
      def cached = cachedIdentifiersByType.get()
      if (cached.containsKey(cache)) {
        /**
         * Attempt an exact match of the query against any attribute of an instance key (account, region, etc.).
         *
         * This is not 100% consistent with doing `*:${cache}:*${normalizedWord}*` in redis _but_ for instances it
         * should be sufficient.
         */
        def identifiersForCache = cached.get(cache)
        identifiers = identifiersForCache.findAll { it.contains(normalizedWord) }
      } else {
        List<SearchableProvider> validProviders = providers.findAll { it.supportsSearch(cache, filters) }
        identifiers = new HashSet<>()
        for (SearchableProvider sp : validProviders) {
          def providerCache = providerRegistry.getProviderCache(sp.getProviderName())
          def searchGlob = sp.buildSearchTerm(cache, normalizedWord)
          def filteredIds = providerCache.filterIdentifiers(cache, searchGlob)
          filteredIds.removeAll(identifiers)
          def existingIds = providerCache.existingIdentifiers(cache, filteredIds)
          identifiers.addAll(existingIds)
        }
      }

      return identifiers
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
