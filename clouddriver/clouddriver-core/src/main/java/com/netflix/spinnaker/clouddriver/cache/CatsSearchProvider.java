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

import static com.netflix.spinnaker.clouddriver.cache.SearchableProvider.SearchableResource;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.search.SearchProvider;
import com.netflix.spinnaker.clouddriver.search.SearchResultSet;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class CatsSearchProvider implements SearchProvider, Runnable {

  private static final Logger log = LoggerFactory.getLogger(CatsSearchProvider.class);
  private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{(\\w+)}|\\$(\\w+)");

  private final CatsInMemorySearchProperties catsInMemorySearchProperties;
  private final Cache cacheView;
  private final List<SearchableProvider> providers;
  private final List<String> defaultCaches;
  private final Map<SearchableResource, SearchableProvider.SearchResultHydrator>
      searchResultHydrators;

  private final Map<String, String> urlMappings;
  private final ProviderRegistry providerRegistry;

  private final AtomicReference<Map<String, Collection<String>>> cachedIdentifiersByType =
      new AtomicReference<>(new HashMap<>());

  private final FiatPermissionEvaluator permissionEvaluator;
  private final List<KeyParser> keyParsers;

  private final ScheduledExecutorService scheduledExecutorService;

  public CatsSearchProvider(
      CatsInMemorySearchProperties catsInMemorySearchProperties,
      Cache cacheView,
      List<SearchableProvider> providers,
      ProviderRegistry providerRegistry,
      Optional<FiatPermissionEvaluator> permissionEvaluator,
      Optional<List<KeyParser>> keyParsers) {
    this.catsInMemorySearchProperties = catsInMemorySearchProperties;
    this.cacheView = cacheView;
    this.providers = providers;

    this.permissionEvaluator = permissionEvaluator.orElse(null);
    this.keyParsers = keyParsers.orElse(Collections.emptyList());
    this.providerRegistry = providerRegistry;

    defaultCaches =
        providers.stream().flatMap(p -> p.getDefaultCaches().stream()).collect(Collectors.toList());
    log.info("Enabled default caches: {}", defaultCaches);

    searchResultHydrators = new HashMap<>();
    providers.forEach(prov -> searchResultHydrators.putAll(prov.getSearchResultHydrators()));

    urlMappings = new HashMap<>();
    providers.forEach(provider -> urlMappings.putAll(provider.getUrlMappingTemplates()));

    if (catsInMemorySearchProperties.isEnabled()) {
      scheduledExecutorService =
          Executors.newScheduledThreadPool(
              1,
              new ThreadFactoryBuilder()
                  .setNameFormat(CatsSearchProvider.class.getSimpleName() + "-%d")
                  .build());
    } else {
      scheduledExecutorService = null;
    }
  }

  public CatsSearchProvider(
      CatsInMemorySearchProperties catsInMemorySearchProperties,
      Cache cacheView,
      List<SearchableProvider> providers,
      ProviderRegistry providerRegistry) {
    this(
        catsInMemorySearchProperties,
        cacheView,
        providers,
        providerRegistry,
        Optional.empty(),
        Optional.empty());
  }

  @PostConstruct
  void scheduleRefresh() {
    if (scheduledExecutorService != null) {
      scheduledExecutorService.scheduleWithFixedDelay(
          this, 0, catsInMemorySearchProperties.getRefreshIntervalSeconds(), TimeUnit.SECONDS);
    }
  }

  /**
   * Periodically refresh cache identifiers that can then be searched over in-memory vs. in-redis.
   *
   * <p>This is beneficial for sets (like instances) that may have hundreds of thousands of keys.
   */
  @Override
  public void run() {
    try {
      log.info("Refreshing Cached Identifiers (instances)");
      List<String> instanceIdentifiers =
          providers.stream()
              .filter(provider -> provider.supportsSearch("instances", Collections.emptyMap()))
              .flatMap(
                  provider -> {
                    var cache = providerRegistry.getProviderCache(provider.getProviderName());
                    return cache.getIdentifiers("instances").stream()
                        .filter(key -> key != null && provider.parseKey(key) != null)
                        .map(String::toLowerCase);
                  })
              .collect(Collectors.toList());

      if (!instanceIdentifiers.isEmpty()) {
        cachedIdentifiersByType.set(Map.of("instances", instanceIdentifiers));
      }

      log.info("Refreshed Cached Identifiers (found {} instances)", instanceIdentifiers.size());
    } catch (Exception e) {
      log.error("Unable to refresh cached identifiers (instances)", e);
    }
  }

  @Override
  public String getPlatform() {
    return "aws"; // TODO(cfieber) - need a better story around this
  }

  @Override
  public SearchResultSet search(String query, Integer pageNumber, Integer pageSize) {
    return search(query, defaultCaches, pageNumber, pageSize);
  }

  @Override
  public SearchResultSet search(
      String query, Integer pageNumber, Integer pageSize, Map<String, String> filters) {
    return search(query, defaultCaches, pageNumber, pageSize, filters);
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
    // ensure we're only searching for types supported by the backing providers
    types = types.stream().filter(defaultCaches::contains).collect(Collectors.toList());

    List<String> matches = findMatches(query, types, filters);
    if (permissionEvaluator != null) {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();

      matches =
          matches.stream()
              .filter(
                  key -> {
                    Map<String, String> result =
                        providers.stream()
                            .map(p -> p.parseKey(key))
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null);
                    if (result == null) {
                      log.warn("No supporting provider found for key (key: {})", key);
                      return true;
                    }

                    boolean canView = true;
                    if (result.get("application") != null && !result.get("application").isEmpty()) {
                      canView =
                          permissionEvaluator.hasPermission(
                              auth, result.get("application"), "APPLICATION", "READ");
                    }
                    if (canView
                        && result.get("account") != null
                        && !result.get("account").isEmpty()) {
                      canView =
                          permissionEvaluator.hasPermission(
                              auth, result.get("account"), "ACCOUNT", "READ");
                    }
                    return canView;
                  })
              .collect(Collectors.toList());
    }
    return generateResultSet(query, matches, pageNumber, pageSize);
  }

  private SearchResultSet generateResultSet(
      String query, List<String> matches, Integer pageNumber, Integer pageSize) {
    List<String> resultPage = paginateResults(matches, pageSize, pageNumber);
    List<Map<String, Object>> results =
        resultPage.stream()
            .map(
                key -> {
                  Map<String, String> result =
                      providers.stream()
                          .map(p -> p.parseKey(key))
                          .filter(Objects::nonNull)
                          .findFirst()
                          .orElse(null);
                  if (result != null) {
                    SearchableResource resultResource =
                        new SearchableResource(
                            result.get("type") != null ? result.get("type").toLowerCase() : null,
                            result.get("provider") != null
                                ? result.get("provider").toLowerCase()
                                : null);
                    if (searchResultHydrators.containsKey(resultResource)) {
                      return new HashMap<String, Object>(
                          searchResultHydrators
                              .get(resultResource)
                              .hydrateResult(cacheView, result, key));
                    } else {
                      return new HashMap<String, Object>(result);
                    }
                  }
                  return null;
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    int filteredItems = resultPage.size() - results.size();

    SearchResultSet resultSet =
        SearchResultSet.builder()
            .totalMatches(matches.size() - filteredItems)
            .platform(getPlatform())
            .query(query)
            .pageNumber(pageNumber)
            .pageSize(pageSize)
            .results(results)
            .build();

    resultSet
        .getResults()
        .forEach(
            result -> {
              if (result.get("provider") == null) {
                result.put("provider", getPlatform());
              }

              Object type = result.get("type");
              if (type != null && urlMappings.containsKey(type.toString())) {
                result.put("url", resolveTemplate(urlMappings.get(type.toString()), result));
              }
            });
    return resultSet;
  }

  @SuppressWarnings("unchecked")
  private List<String> findMatches(
      String q, List<String> cachesToQuery, Map<String, String> filters) {

    if ((q == null || q.isEmpty())
        && keyParsers != null
        && !keyParsers.isEmpty()
        && filters != null
        && !filters.isEmpty()) {
      // no keyword search so find sensible default value to set for searching
      for (KeyParser parser : keyParsers) {
        String field =
            filters.keySet().stream().filter(parser::canParseField).findFirst().orElse(null);
        if (field != null) {
          q = filters.get(field);
          break;
        }
      }

      if (q != null && !q.isEmpty()) {
        log.info(
            "no query string specified, looked for sensible default and found: {} (cachesToQuery: {})",
            q,
            cachesToQuery);
      } else {
        log.info(
            "no query string specified and no sensible default found (cachesToQuery: {})",
            cachesToQuery);
        return new ArrayList<>();
      }
    }

    log.info("Querying {} for term: {}", cachesToQuery, q);
    String normalizedWord = q.toLowerCase();
    String effectiveQ = q;
    List<String> matches =
        cachesToQuery.stream()
            .flatMap(
                cache -> {
                  Predicate<String> filtersMatch =
                      key -> {
                        try {
                          if (filters == null || filters.isEmpty()) {
                            return true;
                          }

                          KeyParser parser =
                              keyParsers.stream()
                                  .filter(
                                      kp ->
                                          kp.getCloudProvider().equals(filters.get("cloudProvider"))
                                              && kp.canParseType(cache))
                                  .findFirst()
                                  .orElse(null);
                          if (parser != null) {
                            Map<String, String> parsed = parser.parseKey(key);
                            return filters.entrySet().stream()
                                .allMatch(
                                    filter -> {
                                      String[] vals = filter.getValue().split(",");
                                      return "cloudProvider".equals(filter.getKey())
                                          || (parsed != null
                                              && ((parsed.containsKey(filter.getKey())
                                                      && Arrays.asList(vals)
                                                          .contains(parsed.get(filter.getKey())))
                                                  || (parsed.containsKey(
                                                          parser.getNameMapping(cache))
                                                      && Arrays.asList(vals)
                                                          .contains(
                                                              parsed.get(
                                                                  parser.getNameMapping(cache))))));
                                    });
                          } else {
                            log.debug("No parser found for {}:{}", cache, key);
                            return true;
                          }
                        } catch (Exception e) {
                          log.warn("Failed on {}:{}", cache, key, e);
                          return false;
                        }
                      };

                  Collection<String> identifiers;
                  Map<String, Collection<String>> cached = cachedIdentifiersByType.get();
                  if (cached.containsKey(cache)) {
                    /*
                     * Attempt an exact match of the query against any attribute of an
                     * instance key (account, region, etc.).
                     *
                     * This is not 100% consistent with doing
                     * `*:${cache}:*${normalizedWord}*` in redis _but_ for instances it
                     * should be sufficient.
                     */
                    Collection<String> identifiersForCache = cached.get(cache);
                    identifiers =
                        identifiersForCache.stream()
                            .filter(id -> id.contains(normalizedWord))
                            .collect(Collectors.toList());
                  } else {
                    List<SearchableProvider> validProviders =
                        providers.stream()
                            .filter(sp -> sp.supportsSearch(cache, filters))
                            .collect(Collectors.toList());
                    HashSet<String> identifierSet = new HashSet<>();
                    for (SearchableProvider sp : validProviders) {
                      var providerCache = providerRegistry.getProviderCache(sp.getProviderName());
                      String searchGlob = sp.buildSearchTerm(cache, normalizedWord);
                      Collection<String> filteredIds =
                          providerCache.filterIdentifiers(cache, searchGlob);
                      filteredIds.removeAll(identifierSet);
                      Collection<String> existingIds =
                          providerCache.existingIdentifiers(cache, filteredIds);
                      identifierSet.addAll(existingIds);
                    }
                    identifiers = identifierSet;
                  }

                  return identifiers.stream().filter(filtersMatch);
                })
            .collect(Collectors.toList());

    matches.sort(
        (a, b) -> {
          String aKey = a.toLowerCase().substring(a.indexOf(':'));
          String bKey = b.toLowerCase().substring(b.indexOf(':'));
          int indexA = aKey.indexOf(effectiveQ);
          int indexB = bKey.indexOf(effectiveQ);
          return indexA == indexB ? aKey.compareTo(bKey) : indexA - indexB;
        });
    return matches;
  }

  private static List<String> paginateResults(
      List<String> matches, Integer pageSize, Integer pageNumber) {
    log.info(
        "Paginating {} results; page number: {}, items per page: {}",
        matches.size(),
        pageNumber,
        pageSize);
    int startingIndex = pageSize * (pageNumber - 1);
    int endIndex = Math.min(pageSize * pageNumber, matches.size());
    boolean hasResults = startingIndex < endIndex;
    return hasResults
        ? new ArrayList<>(matches.subList(startingIndex, endIndex))
        : new ArrayList<>();
  }

  private static String resolveTemplate(String template, Map<String, Object> binding) {
    Matcher matcher = TEMPLATE_PATTERN.matcher(template);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
      Object value = binding.getOrDefault(key, "");
      matcher.appendReplacement(
          result, Matcher.quoteReplacement(value != null ? value.toString() : ""));
    }
    matcher.appendTail(result);
    return result.toString();
  }
}
