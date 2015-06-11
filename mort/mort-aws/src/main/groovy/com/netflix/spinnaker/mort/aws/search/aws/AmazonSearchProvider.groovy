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

import com.netflix.spinnaker.mort.aws.cache.Keys
import com.netflix.spinnaker.mort.model.CacheService
import com.netflix.spinnaker.mort.search.SearchProvider
import com.netflix.spinnaker.mort.search.SearchResultSet
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import groovy.transform.CompileStatic
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * TODO: Copied from oort; should refactor to common library
 */
@Component
@CompileStatic
class AmazonSearchProvider implements SearchProvider {

  protected static final Logger log = Logger.getLogger(this)

  @Autowired
  CacheService cacheService

  static List<String> defaultCaches = [
    Keys.Namespace.SECURITY_GROUPS.ns
  ]
  
  static SimpleTemplateEngine urlMappingTemplateEngine = new SimpleTemplateEngine()

  static Map<String, Template> urlMappings = [
    (Keys.Namespace.SECURITY_GROUPS.ns):
      urlMappingTemplateEngine.createTemplate('/securityGroups/$account/aws/$name?region=$region'),
  ]

  String platform = 'aws'

  @Override
  SearchResultSet search(String query, Integer pageNumber, Integer pageSize) {
    List<String> matches = findMatches(query, defaultCaches)
    generateResultSet(query, matches, pageNumber, pageSize)
  }

  @Override
  SearchResultSet search(String query, List<String> types, Integer pageNumber, Integer pageSize) {
    List<String> matches = findMatches(query, types)
    generateResultSet(query, matches, pageNumber, pageSize)
  }

  private static SearchResultSet generateResultSet(String query, List<String> matches, Integer pageNumber, Integer pageSize) {
    List<Map<String, String>> results = paginateResults(matches, pageSize, pageNumber).collect {
      Keys.parse(it)
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
      if (urlMappings.containsKey(result.type)) {
        def binding = [:]
        binding.putAll(result)
        result.url = urlMappings[result.type].make(binding).toString()
      }
    }
    resultSet
  }

  private List<String> findMatches(String q, List<String> toQuery) {
    log.info("Querying ${toQuery} for term: ${q}")
    String normalizedWord = q.toLowerCase()
    List<String> matches = new ArrayList<String>()
    toQuery.each { String cache ->
      matches.addAll(cacheService.keysByType(cache).findAll { String key ->
        key.substring(key.indexOf(':')).toLowerCase().indexOf(normalizedWord) >= 0
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
