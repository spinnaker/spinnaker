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

package com.netflix.spinnaker.oort.search.aws

import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.search.SearchProvider
import com.netflix.spinnaker.oort.search.SearchResultSet
import groovy.transform.CompileStatic
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class AmazonSearchProvider implements SearchProvider {

  protected static final Logger log = Logger.getLogger(this)

  @Autowired
  CacheService cacheService

  static List<String> defaultCaches = [
    Keys.Namespace.APPLICATIONS.ns,
    Keys.Namespace.CLUSTERS.ns,
    Keys.Namespace.IMAGES.ns,
    Keys.Namespace.LOAD_BALANCER_SERVER_GROUPS.ns,
    Keys.Namespace.SERVER_GROUP_INSTANCES.ns,
    Keys.Namespace.SERVER_GROUPS.ns
  ]

  String platform = 'aws'

  @Override
  SearchResultSet search(String query, Integer pageNumber, Integer pageSize) {
    List<String> matches = findMatches(query, defaultCaches)
    generateResultSet(query, matches, pageNumber, pageSize)
  }

  @Override
  SearchResultSet search(String query, String type, Integer pageNumber, Integer pageSize) {
    List<String> matches = findMatches(query, [type])
    generateResultSet(query, matches, pageNumber, pageSize)
  }

  private static SearchResultSet generateResultSet(String query, List<String> matches, Integer pageNumber, Integer pageSize) {
    List<Map<String, String>> results = paginateResults(matches, pageSize, pageNumber).collect {
      Keys.parse(it)
    }
    new SearchResultSet(
      totalMatches: matches.size(),
      platform: 'aws',
      query: query,
      pageNumber: pageNumber,
      pageSize: pageSize,
      results: results
    )
  }

  private List<String> findMatches(String q, List<String> toQuery) {
    log.info("Querying ${toQuery} for term: ${q}")
    String normalizedWord = q.toLowerCase()
    List<String> matches = new ArrayList<String>()
    toQuery.each { String cache ->
      matches.addAll(cacheService.keysByType(cache).findAll { String key ->
        key.toLowerCase().indexOf(normalizedWord) >= 0
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
