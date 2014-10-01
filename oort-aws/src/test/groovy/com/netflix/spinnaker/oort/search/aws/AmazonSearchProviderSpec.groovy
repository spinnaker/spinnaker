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
import spock.lang.Unroll

class AmazonSearchProviderSpec extends Specification {

  @Shared
  AmazonSearchProvider searchProvider

  @Shared
  CacheService cacheService

  def setup() {
    cacheService = Mock(CacheService)
    searchProvider = new AmazonSearchProvider(cacheService: cacheService)
  }

  @Unroll('for type "#type", expecting "#expected"')
  void 'adds url to results where available'() {
    when:
    SearchResultSet resultSet = searchProvider.search('abc', 1, 10)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> [key]
    cacheService.keysByType(_) >> []
    resultSet.results[0].url == url
    resultSet.results[0].containsKey('url') == url != null

    where:
    key                                                               | url
    Keys.getApplicationKey('abc')                                     | '/applications/abc'
    Keys.getApplicationKey('aBCD')                                    | '/applications/abcd'
    Keys.getServerGroupKey('aBC', 'acct', 'us-w1')                    | '/applications/abc/clusters/acct/aBC/aws/serverGroups/aBC?region=us-w1'
    Keys.getClusterKey('abc','abc','acct')                            | '/applications/abc/clusters/acct/abc'
    Keys.getLoadBalancerKey('abc', 'acct', 'us-w1')                   | '/aws/loadBalancers/abc'
    Keys.getLoadBalancerServerGroupKey('abc', 'acct', 'sg', 'us-w1')  | '/aws/loadBalancers/abc'
    Keys.getServerGroupInstanceKey('abc','a','acct','us-w1')          | null
    Keys.getNamedImageKey('abc','imgName', 'us-w1')                   | null

    type = Keys.parse(key).type
    expected = url ?: '[no url expected]'

  }

  void 'filter results, set metadata on results, ignore case'() {
    given:
    List keys = [Keys.getApplicationKey('miss'), Keys.getApplicationKey('FABCO'), Keys.getApplicationKey('cabco')]

    when:
    SearchResultSet resultSet = searchProvider.search('aBC', 1, 10)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> keys
    cacheService.keysByType(_) >> []
    0 * _

    with(resultSet) {
      totalMatches == 2
      pageSize == 10
      pageNumber == 1
      platform == 'aws'
      query == 'aBC'
      results.size() == 2
      results[0] == Keys.parse(keys[2]) + [url: '/applications/cabco']
      results[1] == Keys.parse(keys[1]) + [url: '/applications/fabco']
    }
  }

  @Unroll('for query "#query" with filters "#filters", expect "#expected"')
  void 'filter results via filters and query'() {
    given:
    List keys = [
      Keys.getLoadBalancerKey('elb1', 'prod', 'us-west-1'),
      Keys.getLoadBalancerKey('elb2', 'test', 'us-east-1'),
      Keys.getLoadBalancerKey('elb3', 'test', 'us-west-1'),
      Keys.getLoadBalancerKey('miss', 'test', 'us-west-1')
    ]


    when:
    SearchResultSet resultSet = searchProvider.search(query, 1, 10, filters)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> keys
    cacheService.keysByType(_) >> []
    0 * _

    resultSet.results.loadBalancer == expected

    where:
    query | filters                                 || expected
    ''    | null                                    || ['elb1', 'elb2', 'elb3', 'miss']
    'elb' | null                                    || ['elb1', 'elb2', 'elb3']
    'elb' | [account: 'test']                       || ['elb2', 'elb3']
    ''    | [region: 'us-west-1']                   || ['elb1', 'elb3', 'miss']
    ''    | [region: 'us-west-1', account: 'test']  || ['elb3', 'miss']
    'mis' | [:]                                     || ['miss']
    'zzz' | [:]                                     || []
    'elb' | [badparam: 'miss']                      || []
    'elb' | [region: 'sa-west-1']                   || []


  }

  void 'ignores key type'() {
    given:
    List keys = [
      "${Keys.Namespace.APPLICATIONS}:${Keys.Namespace.APPLICATIONS.ns.toLowerCase()}foo",
      "${Keys.Namespace.APPLICATIONS}:bar"
    ]

    when:
    SearchResultSet resultSet = searchProvider.search(Keys.Namespace.APPLICATIONS.ns, 1, 10)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> keys
    cacheService.keysByType(_) >> []

    with(resultSet) {
      totalMatches == 1
      !results[0].application.contains('bar')
    }
  }

  void 'respect user-specified size limit'() {
    given:
    List keys = [Keys.getApplicationKey('abc'), Keys.getApplicationKey('abd')]

    when:
    SearchResultSet results = searchProvider.search('ab', 1, 1)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> keys
    cacheService.keysByType(_) >> []
    results.totalMatches == 2
    results.results == [Keys.parse(keys[0]) + [url: '/applications/abc']]
  }

  void 'sort results by query, then alphabetically, ignoring the category'() {
    given:
    List applicationKeys = [Keys.getApplicationKey('abx'), Keys.getApplicationKey('bac')]
    List serverGroupKeys = [Keys.getServerGroupKey('abc', 'account', 'region')]

    when:
    SearchResultSet results = searchProvider.search('b', 1, 3)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> applicationKeys
    1 * cacheService.keysByType(Keys.Namespace.SERVER_GROUPS.ns) >> serverGroupKeys
    cacheService.keysByType(_) >> []
    results.results == [
      Keys.parse(applicationKeys[1]) + [url: '/applications/bac'],
      Keys.parse(serverGroupKeys[0]) + [url: '/applications/abc/clusters/account/abc/aws/serverGroups/abc?region=region'],
      Keys.parse(applicationKeys[0]) + [url: '/applications/abx']
    ]
  }

  void 'filter by type'() {
    given:
    List applicationKeys = [Keys.getApplicationKey('abx'), Keys.getApplicationKey('bac')]

    when:
    SearchResultSet results = searchProvider.search('b', [Keys.Namespace.APPLICATIONS.ns], 1, 5)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> applicationKeys
    0 * _
    results.results == [
      Keys.parse(applicationKeys[1]) + [url: '/applications/bac'],
      Keys.parse(applicationKeys[0]) + [url: '/applications/abx']
    ]
  }

  void 'search multiple types'() {
    given:
    List applicationKeys = [Keys.getApplicationKey('abx'), Keys.getApplicationKey('bac')]
    List imageKeys = [Keys.getImageKey('abx', 'x'), Keys.getImageKey('bac', 'y')]

    when:
    SearchResultSet results = searchProvider.search('b', [Keys.Namespace.IMAGES.ns, Keys.Namespace.APPLICATIONS.ns], 1, 5)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> applicationKeys
    1 * cacheService.keysByType(Keys.Namespace.IMAGES.ns) >> imageKeys
    0 * _
    results.results.size() == 4
  }

  void 'return empty list when page requested does not exist'() {
    given:
    List applicationKeys = [Keys.getApplicationKey('abx'), Keys.getApplicationKey('bac')]

    when:
    SearchResultSet results = searchProvider.search('b', [Keys.Namespace.APPLICATIONS.ns], 2, 5)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS.ns) >> applicationKeys
    0 * _
    results.totalMatches == 2
    results.results == []
  }
  
  
}
