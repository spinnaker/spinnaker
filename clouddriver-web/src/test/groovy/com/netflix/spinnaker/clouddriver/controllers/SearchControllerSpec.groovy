/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.search.SearchProvider
import com.netflix.spinnaker.clouddriver.search.SearchResultSet
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest

class SearchControllerSpec extends Specification {

  SearchProvider searchProviderA = Mock(SearchProvider)
  SearchProvider searchProviderB = Mock(SearchProvider)
  SearchController searchController = new SearchController(searchProviders: [searchProviderA, searchProviderB])
  HttpServletRequest request = Mock(HttpServletRequest)
  Enumeration enumeration = Mock(Enumeration)

  List getSearchResults(Map filters) {
    return searchController.search(
      filters.get("q") as String,
      filters.get("type") as List<String>,
      filters.get("platform") as String,
      filters.get("pageSize") as int,
      filters.get("page") as int,
      request)
  }

  def setup() {
  }

  def 'query all search providers'() {
    given:
    SearchResultSet rsA = new SearchResultSet(platform: 'a', totalMatches: 1, pageSize: 10, pageNumber: 1, query: 'aBC', results: [[item1: 'foo']])
    SearchResultSet rsB = new SearchResultSet(platform: 'b', totalMatches: 1, pageSize: 10, pageNumber: 1, query: 'aBC', results: [[item2: 'bar']])
    SearchResultSet expected = new SearchResultSet(platform: 'aws', totalMatches: 2, pageSize: 10, pageNumber: 1, query: 'aBC', results: [[item1: 'foo'],[item2: 'bar']])

    when:
    def filters = [
      q : 'aBC',
      page : 1,
      pageSize: 10
    ]
    List searchResultSets = getSearchResults(filters)

    then:
    1 * searchProviderA.search('aBC', 1, 10, [:]) >> rsA
    1 * searchProviderB.search('aBC', 1, 10, [:]) >> rsB
    1 * request.getParameterNames() >> enumeration

    searchResultSets == [expected]
  }

  def 'should not fail all searches if any provider fail'() {
    given:
    SearchResultSet rsB = new SearchResultSet(platform: 'b', totalMatches: 1, pageSize: 10, pageNumber: 1, query: 'aBC', results: [[item2: 'bar']])
    SearchResultSet expected = new SearchResultSet(platform: 'aws', totalMatches: 1, pageSize: 10, pageNumber: 1, query: 'aBC', results: [[item2: 'bar']])

    and:
    def filters = [
      q: 'aBC',
      page: 1,
      pageSize: 10
    ]

    when:
    List searchResultSets = getSearchResults(filters)

    then:
    1 * searchProviderA.search(filters.q, filters.page, filters.pageSize, [:]) >> {
      throw new Exception("An exception message")
    }

    1 * searchProviderB.search(filters.q, filters.page, filters.pageSize, [:]) >> rsB
    searchResultSets == [expected]
    notThrown(Exception)
  }

  def 'filter search providers by platform'() {
    given:
    SearchResultSet resultSetA = Stub(SearchResultSet)
    def filters = [
      q: "a",
      platform: "aws",
      page: 1,
      pageSize: 10
    ]

    when:
    List searchResultSets = getSearchResults(filters)

    then:
    1 * searchProviderA.platform >> 'aws'
    1 * searchProviderB.platform >> 'gce'
    1 * searchProviderA.search('a', 1, 10, [:]) >> resultSetA
    1 * request.getParameterNames() >> enumeration

    searchResultSets == [resultSetA]
  }

  def 'search on type if provided'() {
    setup:
    SearchResultSet resultSetA = Stub(SearchResultSet)
    searchController = new SearchController(searchProviders: [searchProviderA])
    def filters = [
      q: "aBC",
      type: ['applications'],
      page: 1,
      pageSize: 10
    ]

    when:
    List searchResultSets = getSearchResults(filters)

    then:
    1 * searchProviderA.search('aBC', ['applications'], 1, 10, [:]) >> resultSetA
    1 * request.getParameterNames() >> enumeration

    searchResultSets == [resultSetA]
  }

  def "if only one search provider, don't aggregate into an aws result"() {
    SearchResultSet rsA = Stub(SearchResultSet)
    searchController = new SearchController(searchProviders: [searchProviderA])
    def filters = [
      q: "aBC",
      page: 1,
      pageSize: 10
    ]

    when:
    List searchResultSets = getSearchResults(filters)

    then:
    1 * searchProviderA.search('aBC', 1, 10, [:]) >> rsA
    1 * request.getParameterNames() >> enumeration

    searchResultSets == [rsA]
  }
}

