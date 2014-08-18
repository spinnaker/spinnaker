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
import org.joda.time.LocalDateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@CompileStatic
@RestController
class TypeaheadController {

  protected static final Logger log = Logger.getLogger(this)

  static Object[] cachesToQuery = [
    Keys.Namespace.APPLICATIONS,
    Keys.Namespace.CLUSTERS,
    Keys.Namespace.IMAGES,
    Keys.Namespace.LOAD_BALANCERS,
    Keys.Namespace.SERVER_GROUP_INSTANCE,
    Keys.Namespace.SERVER_GROUPS
  ]

  @Autowired
  CacheService cacheService

  /**
   * Dumb simple typeahead endpoint. Queries against all keys in the cache, returning
   * up to 50 results
   * @param q the phrase to query; words can be separated by spaces, in which case results
   *          must match every word
   * @param size the maximum number of results to return; will be capped at 50 if size > 50
   * @return a list of matching items in a simple map format with two keys:
   *  { "key":<cache key>, "contents": <JSON map value> }
   */

  @RequestMapping(value = '/typeahead')
  List<Object> typeaheadResults(
    @RequestParam String q, @RequestParam(value = 'size', defaultValue = '10') Integer size) {

    log.info("Fetching typeahead results for ${q}, size: ${size}")

    List<Map> results = [];

    List<String> matches = findMatches(q)
    List<String> toReturn = cullResultsToMaxSize(size, matches)
    buildResultSet(toReturn, results)

    results
  }

  private Iterable<String> buildResultSet(List<String> toReturn, List<Map> results) {
    toReturn.each { String key ->
      def contents = cacheService.retrieve(key, Object)
      results << [key: Keys.parse(key), contents: contents]
    }
  }

  private static List<String> cullResultsToMaxSize(int size, List<String> matches) {
    Integer maxResults = Math.min(size ?: 10, 50)
    Integer resultSize = Math.min(matches.size(), maxResults)
    List<String> toReturn = resultSize ? matches[0..resultSize - 1] : new ArrayList<String>()
    toReturn
  }

  private List<String> findMatches(String q) {
    String normalizedWord = q.toLowerCase()
    List<String> matches = new ArrayList<String>()
    cachesToQuery.each { Object cache ->
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
