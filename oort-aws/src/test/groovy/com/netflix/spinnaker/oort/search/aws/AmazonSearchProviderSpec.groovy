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
import com.netflix.spinnaker.oort.search.SearchResultSet
import spock.lang.Shared
import spock.lang.Specification

import java.lang.Void as Should

class AmazonSearchProviderSpec extends Specification {

  @Shared
  AmazonSearchProvider searchProvider

  @Shared
  CacheService cacheService

  def setup() {
    cacheService = Mock(CacheService)
    searchProvider = new AmazonSearchProvider(cacheService: cacheService)
  }

  Should 'filter results, set metadata on results, ignore case'() {
    given:
    List keys = [Keys.getApplicationKey('miss'), Keys.getApplicationKey('FABCO'), Keys.getApplicationKey('cabco')]

    when:
    SearchResultSet results = searchProvider.search('aBC', 1, 10)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> keys
    cacheService.keysByType(_) >> []
    0 * _

    results.totalMatches == 2
    results.pageSize == 10
    results.pageNumber == 1
    results.platform == 'aws'
    results.query == 'aBC'
    results.results.size() == 2
    results.results[0] == Keys.parse(keys[1])
    results.results[1] == Keys.parse(keys[2])
  }

  Should 'respect user-specified size limit'() {
    given:
    List keys = [Keys.getApplicationKey('abc'), Keys.getApplicationKey('abd')]

    when:
    SearchResultSet results = searchProvider.search('ab', 1, 1)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> keys
    cacheService.keysByType(_) >> []
    results.totalMatches == 2
    results.results == [Keys.parse(keys[0])]
  }

  Should 'sort results by query, then alphabetically, ignoring the category'() {
    given:
    List applicationKeys = [Keys.getApplicationKey('abx'), Keys.getApplicationKey('bac')]
    List serverGroupKeys = [Keys.getServerGroupKey('abc', 'account', 'region')]

    when:
    SearchResultSet results = searchProvider.search('b', 1, 3)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> applicationKeys
    1 * cacheService.keysByType(Keys.Namespace.SERVER_GROUPS.ns) >> serverGroupKeys
    cacheService.keysByType(_) >> []
    results.results == [Keys.parse(applicationKeys[1]), Keys.parse(serverGroupKeys[0]), Keys.parse(applicationKeys[0])]
  }

  Should 'filter by type'() {
    given:
    List applicationKeys = [Keys.getApplicationKey('abx'), Keys.getApplicationKey('bac')]

    when:
    SearchResultSet results = searchProvider.search('b', Keys.Namespace.APPLICATIONS.ns, 1, 5)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> applicationKeys
    0 * _
    results.results == [Keys.parse(applicationKeys[1]), Keys.parse(applicationKeys[0])]
  }

  Should 'return empty list when page requested does not exist'() {
    given:
    List applicationKeys = [Keys.getApplicationKey('abx'), Keys.getApplicationKey('bac')]

    when:
    SearchResultSet results = searchProvider.search('b', Keys.Namespace.APPLICATIONS.ns, 2, 5)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> applicationKeys
    0 * _
    results.totalMatches == 2
    results.results == []
  }
}
