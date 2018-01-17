/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.SearchService
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import javax.servlet.http.HttpServletRequest;

class SearchControllerSpec extends Specification {
  def searchService = Mock(SearchService)
  def httpServletRequest = Mock(HttpServletRequest)

  @Subject
  def controller = new SearchController(searchService: searchService)

  @Unroll
  def "should return empty results when `q` parameter is < 3 characters"() {
    when:
    controller.search(query, null, null, 100, 0, null, httpServletRequest).isEmpty()

    then:
    expectedSearches * searchService.search(query, null, null, null, 100, 0, [:]) >> { return [] }

    where:
    query || expectedSearches
    null  || 0
    ""    || 0
    "a"   || 0
    "ab"  || 0
    "abc" || 1
  }
}
