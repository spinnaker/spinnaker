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



package com.netflix.spinnaker.mort.aws.search.aws

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.search.SearchProvider
import com.netflix.spinnaker.clouddriver.search.SearchResultSet
import com.netflix.spinnaker.mort.aws.cache.Keys
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component


/**
 * TODO(cfieber): Copied from oort - need to make CatsSearchProvider support multiple Providers as suppliers
 */
@Component('mortSearchProvider')
class AmazonSearchProvider implements SearchProvider {

  protected static final Logger log = Logger.getLogger(this)

  static final List<String> defaultCaches = [
    Keys.Namespace.SECURITY_GROUPS.ns
  ]

  static SimpleTemplateEngine urlMappingTemplateEngine = new SimpleTemplateEngine()

  static Map<String, Template> urlMappings = [
    (Keys.Namespace.SECURITY_GROUPS.ns):
      urlMappingTemplateEngine.createTemplate('/securityGroups/$account/aws/$name?region=$region'),
  ]

  static Map<String, Closure> searchResultHydrators = [:]

  private final Cache cacheView
  private final AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  public AmazonSearchProvider(Cache cacheView, AccountCredentialsProvider accountCredentialsProvider) {
    this.cacheView = cacheView
    this.accountCredentialsProvider = accountCredentialsProvider
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
    List<String> matches = findMatches(query, types, filters)
    generateResultSet(cacheView, query, matches, pageNumber, pageSize)
  }

  private static SearchResultSet generateResultSet(Cache cacheView, String query, List<String> matches, Integer pageNumber, Integer pageSize) {
    List<Map<String, String>> results = paginateResults(matches, pageSize, pageNumber).collect {
      Map<String, String> result = Keys.parse(it)
      return searchResultHydrators.containsKey(result.type) ? searchResultHydrators[result.type](cacheView, result, it) : result
    }
    SearchResultSet resultSet = new SearchResultSet(
      totalMatches: matches.size(),
      platform: 'aws',
      query: query,
      pageNumber: pageNumber,
      pageSize: pageSize,
      results: results
    )
    resultSet.results.each { Map<String, String> result ->
      result.provider = "aws"

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
      matches.addAll(buildFilterIdentifiers(accountCredentialsProvider, cache, normalizedWord).findAll { String key ->
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

  private Collection<String> buildFilterIdentifiers(AccountCredentialsProvider accountCredentialsProvider, String cache, String query) {
    switch (cache) {
      default:
        return cacheView.filterIdentifiers(cache, "${cache}:*${query}*")
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
