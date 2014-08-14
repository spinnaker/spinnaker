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

import com.netflix.spinnaker.oort.model.CacheService
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class TypeaheadController {

  protected static final Logger log = Logger.getLogger(this)

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
  List<Map> typeaheadResults(
    @RequestParam String q, @RequestParam(value = 'size', defaultValue = '10') Integer size) {

    log.info(String.format('Getting typeahead results for %s, size: %d', q, size))

    String[] normalizedWords = q.toLowerCase().split(' ')
    List<Map> results = [];

    List fromCache = cacheService.keys().findAll { String key ->
      String normalizedKey = key.toLowerCase()
      normalizedWords.every { String word ->
        normalizedKey.contains(word)
      }
    }.toList()

    Integer maxResults = Math.min(size ?: 10, 50)
    Integer resultSize = Math.min(fromCache.size(), maxResults)
    List toReturn = resultSize ? fromCache[0..resultSize - 1] : []
    toReturn.each { String key ->
      results << [key: key, contents: cacheService.retrieve(key, Map)]
    }
    results
  }

}
