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
import com.netflix.spinnaker.oort.search.SearchProvider
import com.netflix.spinnaker.oort.search.SearchResultSet
import spock.lang.Specification

class SearchControllerSpec extends Specification {

  SearchController searchController
  SearchProvider searchProviderA
  SearchProvider searchProviderB

  def setup() {
    searchProviderA = Mock(SearchProvider)
    searchProviderB = Mock(SearchProvider)
    searchController = new SearchController(searchProviders: [searchProviderA, searchProviderB])
  }

   def 'query all search providers'() {
    given:
    SearchResultSet rsA = new SearchResultSet(platform: 'a', totalMatches: 1, pageSize: 10, pageNumber: 1, query: 'aBC', results: [[item1: 'foo']])
    SearchResultSet rsB = new SearchResultSet(platform: 'b', totalMatches: 1, pageSize: 10, pageNumber: 1, query: 'aBC', results: [[item2: 'bar']])
    SearchResultSet expected = new SearchResultSet(platform: 'aws', totalMatches: 2, pageSize: 10, pageNumber: 1, query: 'aBC', results: [[item1: 'foo'],[item2: 'bar']])

    when:
    SearchController.SearchQueryCommand q = new SearchController.SearchQueryCommand(q:'aBC', page: 1, pageSize: 10)
    List searchResultSets = searchController.search(q)

    then:
    1 * searchProviderA.search('aBC', 1, 10, null) >> rsA
    1 * searchProviderB.search('aBC', 1, 10, null) >> rsB
    0 * _

    searchResultSets == [expected]
  }

  def 'filter search providers by platform'() {
    given:
    SearchResultSet resultSetA = Stub(SearchResultSet)

    when:
    SearchController.SearchQueryCommand q = new SearchController.SearchQueryCommand(q:'a', platform: 'aws', page: 1, pageSize: 10)
    List searchResultSets = searchController.search(q)

    then:
    1 * searchProviderA.platform >> 'aws'
    1 * searchProviderB.platform >> 'gce'
    1 * searchProviderA.search('a', 1, 10, null) >> resultSetA
    0 * _

    searchResultSets == [resultSetA]
  }

  def 'search on type if provided'() {
    setup:
    SearchResultSet resultSetA = Stub(SearchResultSet)
    searchController = new SearchController(searchProviders: [searchProviderA])

    when:
    SearchController.SearchQueryCommand q = new SearchController.SearchQueryCommand(q:'aBC', type: ['applications'], page: 1, pageSize: 10)
    List searchResultSets = searchController.search(q)

    then:
    1 * searchProviderA.search('aBC', ['applications'], 1, 10, null) >> resultSetA
    0 * _

    searchResultSets == [resultSetA]
  }

  def "if only one search provider, don't aggregate into an aws result"() {
    SearchResultSet rsA = Stub(SearchResultSet)
    searchController = new SearchController(searchProviders: [searchProviderA])

    when:
    SearchController.SearchQueryCommand q = new SearchController.SearchQueryCommand(q:'aBC', page: 1, pageSize: 10)
    List searchResultSets = searchController.search(q)

    then:
    1 * searchProviderA.search('aBC', 1, 10, null) >> rsA
    0 * _

    searchResultSets == [rsA]
  }


}

