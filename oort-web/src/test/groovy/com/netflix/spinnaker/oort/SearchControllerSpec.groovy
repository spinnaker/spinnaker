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
import spock.lang.Shared
import spock.lang.Specification

import java.lang.Void as Should

class SearchControllerSpec extends Specification {

  @Shared
  SearchController searchController

  @Shared
  CacheService cacheService

  def setup() {
    cacheService = Mock(CacheService)
    searchController = new SearchController(cacheService: cacheService)
  }

  Should 'filter results, ignoring case'() {
    given:
    List keys = [Keys.getApplicationKey('miss'), Keys.getApplicationKey('FABCO'), Keys.getApplicationKey('cabco')]

    when:
    Map results = searchController.searchResults('aBC', '', 1, 10)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> keys
    cacheService.keysByType(_) >> []
    0 * _

    results.count == 2
    results.matches.size() == 2
    results.matches[0] == Keys.parse(keys[1])
    results.matches[1] == Keys.parse(keys[2])
  }

  Should 'limit results to MAX_RESULTS'() {
    given:
    List keys = []
    while (keys.size() < SearchController.MAX_PAGE_SIZE + 1) {
      keys << Keys.getApplicationKey('a'+keys.size())
    }

    when:
    Map page1 = searchController.searchResults('a', '', 1, SearchController.MAX_PAGE_SIZE + 2)
    Map page2 = searchController.searchResults('a', '', 2, SearchController.MAX_PAGE_SIZE + 2)

    then:
    2 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> keys
    cacheService.keysByType(_) >> []
    page1.count == SearchController.MAX_PAGE_SIZE + 1
    page2.count == page1.count
    page1.matches.size() == SearchController.MAX_PAGE_SIZE
    page2.matches.size() == 1
  }

  Should 'respect user-specified size limit'() {
    given:
    List keys = [Keys.getApplicationKey('abc'), Keys.getApplicationKey('abd')]

    when:
    Map results = searchController.searchResults('ab', '', 1, 1)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> keys
    cacheService.keysByType(_) >> []
    results.matches == [Keys.parse(keys[0])]
  }

  Should 'sort results by query, then alphabetically, ignoring the category'() {
    given:
    List applicationKeys = [Keys.getApplicationKey('abx'), Keys.getApplicationKey('bac')]
    List serverGroupKeys = [Keys.getServerGroupKey('abc', 'account', 'region')]

    when:
    Map results = searchController.searchResults('b', '', 1, 3)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> applicationKeys
    1 * cacheService.keysByType(Keys.Namespace.SERVER_GROUPS.ns) >> serverGroupKeys
    cacheService.keysByType(_) >> []
    results.matches == [Keys.parse(applicationKeys[1]), Keys.parse(serverGroupKeys[0]), Keys.parse(applicationKeys[0])]
  }

  Should 'filter by type'() {
    given:
    List applicationKeys = [Keys.getApplicationKey('abx'), Keys.getApplicationKey('bac')]

    when:
    Map results = searchController.searchResults('b', Keys.Namespace.APPLICATIONS.ns, 1, 5)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> applicationKeys
    0 * _
    results.matches == [Keys.parse(applicationKeys[1]), Keys.parse(applicationKeys[0])]
  }

  Should 'return empty list when page requested does not exist'() {
    given:
    List applicationKeys = [Keys.getApplicationKey('abx'), Keys.getApplicationKey('bac')]

    when:
    Map results = searchController.searchResults('b', Keys.Namespace.APPLICATIONS.ns, 2, 5)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> applicationKeys
    0 * _
    results.count == 2
    results.matches.size() == 0
  }

}

