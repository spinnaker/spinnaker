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

package com.netflix.spinnaker.oort.controllers

import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.CacheService
import groovy.transform.CompileStatic
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@CompileStatic
@RestController
class SearchController {

  protected static final Logger log = Logger.getLogger(this)
  static final Integer MAX_PAGE_SIZE = 100

  static String[] cachesToQuery = [
    Keys.Namespace.APPLICATIONS.ns,
    Keys.Namespace.CLUSTERS.ns,
    Keys.Namespace.IMAGES.ns,
    Keys.Namespace.LOAD_BALANCERS.ns,
    Keys.Namespace.SERVER_GROUP_INSTANCES.ns,
    Keys.Namespace.SERVER_GROUPS.ns
  ]

  @Autowired
  CacheService cacheService

  /**
   * Dumb simple search endpoint. Queries against all keys in the cache, returning
   * up to 50 results
   * @param q the phrase to query; words can be separated by spaces, in which case results
   *          must match every word
   * @param pageSize the maximum number of results to return per page; will be capped at 50 if size > 50
   * @param type a {@link com.netflix.spinnaker.oort.data.aws.Keys.Namespace} value, used to only return results of that type
   * @param pageNumber the page number, starting with 1
   * @return a list of matching items in a simple map format with two keys:
   *  { "key":<cache key>, "contents": <JSON map value> }
   */

  @RequestMapping(value = '/search')
  Map<String, Object> searchResults(
    @RequestParam String q,
    @RequestParam(value = 'type', defaultValue = '') String type,
    @RequestParam(value = 'page', defaultValue = '1') Integer pageNumber,
    @RequestParam(value = 'pageSize', defaultValue = '10') Integer pageSize) {

    log.info("Fetching search results for ${q}, pageSize: ${pageSize}, type: ${type}, pageNumber: ${pageNumber}")
    List<Map> results = [];

    List<String> matches = findMatches(q, type)
    Integer totalResults = matches.size()
    List<String> toReturn = paginateResults(matches, pageSize, pageNumber)
    toReturn.each { String key ->
      results << Keys.parse(key)
    }
    [count: totalResults, matches: results]
  }

  private static List<String> paginateResults(List<String> matches, Integer pageSize, Integer pageNumber) {
    Integer maxResults = Math.min(pageSize ?: 10, MAX_PAGE_SIZE)
    Integer startingIndex = maxResults * (pageNumber - 1)
    Integer endIndex = Math.min(maxResults * pageNumber, matches.size())
    boolean hasResults = startingIndex < endIndex
    List<String> toReturn = hasResults ? matches[startingIndex..endIndex - 1] : new ArrayList<String>()
    toReturn
  }

  private List<String> findMatches(String q, String type) {
    String normalizedWord = q.toLowerCase()
    List<String> matches = new ArrayList<String>()
    def toQuery = type ? [type] : cachesToQuery
    toQuery.each { Object cache ->
      matches.addAll(cacheService.keysByType(cache).findAll { String key ->
        key.toLowerCase().indexOf(normalizedWord) >= 0
      })
    }
    matches.sort {String a, String b ->
      def baseA = a.substring(a.indexOf(':'))
      def indexA = baseA.indexOf(normalizedWord)
      def baseB = b.substring(b.indexOf(':'))
      def indexB = baseB.indexOf(normalizedWord)
      return indexA == indexB ? baseA <=> baseB : indexA - indexB
    }
  }

}
