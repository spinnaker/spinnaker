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

package com.netflix.spinnaker.oort.aws.provider.view

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.AmazonCredentials
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.search.SearchProvider
import com.netflix.spinnaker.clouddriver.search.SearchResultSet
import com.netflix.spinnaker.oort.aws.data.Keys
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.regex.Pattern

import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.CLUSTERS
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.SERVER_GROUPS

@Component
class CatsSearchProvider implements SearchProvider {

  private static final Logger log = LoggerFactory.getLogger(CatsSearchProvider)

  static final List<String> defaultCaches = [
    APPLICATIONS.ns,
    LOAD_BALANCERS.ns,
    CLUSTERS.ns,
    SERVER_GROUPS.ns,
    INSTANCES.ns
  ]

  static Pattern INSTANCE_ID_PATTERN = Pattern.compile('(i-)?[0-9a-f]{8}')

  static SimpleTemplateEngine urlMappingTemplateEngine = new SimpleTemplateEngine()

  static Map<String, Template> urlMappings = [
    (SERVER_GROUPS.ns):
      urlMappingTemplateEngine.createTemplate('/applications/${application.toLowerCase()}/clusters/$account/$cluster/aws/serverGroups/$serverGroup?region=$region'),
    (LOAD_BALANCERS.ns):
      urlMappingTemplateEngine.createTemplate('/aws/loadBalancers/$loadBalancer'),
//    (Keys.Namespace.LOAD_BALANCER_SERVER_GROUPS.ns):
//      urlMappingTemplateEngine.createTemplate('/aws/loadBalancers/$loadBalancer'),
    (CLUSTERS.ns):
      urlMappingTemplateEngine.createTemplate('/applications/${application.toLowerCase()}/clusters/$account/$cluster'),
    (APPLICATIONS.ns):
      urlMappingTemplateEngine.createTemplate('/applications/${application.toLowerCase()}')
  ]

  static Map<String, Closure> searchResultHydrators = [
    (INSTANCES.ns): { Cache cacheView, Map<String, String> result, String instanceCacheKey ->
      def item = cacheView.get(INSTANCES.ns, instanceCacheKey)
      if (!item.relationships["serverGroups"]) {
        return result
      }

      def serverGroup = Keys.parse(item.relationships["serverGroups"][0])
      return result + [
        application: serverGroup.application as String,
        cluster: serverGroup.cluster as String,
        serverGroup: serverGroup.serverGroup as String
      ]
    }
  ]

  private final Cache cacheView
  private final AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  public CatsSearchProvider(Cache cacheView, AccountCredentialsProvider accountCredentialsProvider) {
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

  /**
   * Perform an exact match against the instances cache, vs. *glob* searches for everything else
   */
  private Collection<String> buildFilterIdentifiers(AccountCredentialsProvider accountCredentialsProvider, String cache, String query) {
    switch (cache) {
      case INSTANCES.ns:
        if (!query.matches(INSTANCE_ID_PATTERN)) {
          return []
        }
        def normalizedQuery = query.startsWith('i-') ? query : 'i-' + query
        Set<NetflixAmazonCredentials> amazonCredentials = accountCredentialsProvider.all.findAll {
          it instanceof NetflixAmazonCredentials
        } as Set<NetflixAmazonCredentials>

        def possibleInstanceIdentifiers = []
        amazonCredentials.each { NetflixAmazonCredentials credentials ->
          credentials.regions.each { AmazonCredentials.AWSRegion region ->
            possibleInstanceIdentifiers << Keys.getInstanceKey(normalizedQuery, credentials.name, region.name)
          }
        }
        return cacheView.getAll(INSTANCES.ns, possibleInstanceIdentifiers)*.id

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
