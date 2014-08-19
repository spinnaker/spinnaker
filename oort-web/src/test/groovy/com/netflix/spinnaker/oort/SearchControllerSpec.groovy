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

package com.netflix.spinnaker.oort

import com.netflix.spinnaker.oort.controllers.SearchController
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.search.SearchProvider
import com.netflix.spinnaker.oort.search.SearchResultSet
import spock.lang.Shared
import spock.lang.Specification

import java.lang.Void as Should

class SearchControllerSpec extends Specification {

  @Shared
  SearchController searchController

  @Shared
  SearchProvider searchProviderA
  SearchProvider searchProviderB

  def setup() {
    searchProviderA = Mock(SearchProvider)
    searchProviderB = Mock(SearchProvider)
    searchController = new SearchController(searchProviders: [searchProviderA, searchProviderB])
  }

  Should 'query all search providers'() {
    given:
    SearchResultSet resultSetA = Mock(SearchResultSet)
    SearchResultSet resultSetB = Mock(SearchResultSet)

    when:
    List searchResultSets = searchController.search('aBC', '', '', 1, 10)

    then:
    1 * searchProviderA.search('aBC', 1, 10) >> resultSetA
    1 * searchProviderB.search('aBC', 1, 10) >> resultSetB
    0 * _

    searchResultSets == [resultSetA, resultSetB]
  }

  Should 'filter search providers by platform'() {
    given:
    SearchResultSet resultSetA = Mock(SearchResultSet)

    when:
    List searchResultSets = searchController.search('a', '', 'aws', 1, 10)

    then:
    1 * searchProviderA.platform >> 'aws'
    1 * searchProviderB.platform >> 'gce'
    1 * searchProviderA.search('a', 1, 10) >> resultSetA
    0 * _

    searchResultSets == [resultSetA]
  }

  Should 'search on type if provided'() {
    setup:
    SearchResultSet resultSetA = Mock(SearchResultSet)
    SearchResultSet resultSetB = Mock(SearchResultSet)

    when:
    List searchResultSets = searchController.search('aBC', 'applications', '', 1, 10)

    then:
    1 * searchProviderA.search('aBC', 'applications', 1, 10) >> resultSetA
    1 * searchProviderB.search('aBC', 'applications', 1, 10) >> resultSetB
    0 * _

    searchResultSets == [resultSetA, resultSetB]
  }
}

