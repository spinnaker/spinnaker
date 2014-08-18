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

import com.netflix.spinnaker.oort.controllers.TypeaheadController
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.CacheService
import spock.lang.Shared
import spock.lang.Specification

import java.lang.Void as Should

class TypeaheadControllerSpec extends Specification {

  @Shared
  TypeaheadController typeaheadController

  @Shared
  CacheService cacheService

  def setup() {
    cacheService = Mock(CacheService)
    typeaheadController = new TypeaheadController(cacheService: cacheService)
  }

  Should 'filter results, ignoring case'() {
    given:
    List keys = [Keys.getApplicationKey('miss'), Keys.getApplicationKey('FABCO'), Keys.getApplicationKey('cabco')]
    Map fabcoData = [data: 1]
    Map cabcoData = [data: 2]

    when:
    List results = typeaheadController.typeaheadResults('aBC', 10)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS) >> keys
    cacheService.keysByType(_) >> []
    1 * cacheService.retrieve(keys[1], Object) >> fabcoData
    1 * cacheService.retrieve(keys[2], Object) >> cabcoData
    0 * _

    results.size() == 2
    results[0] == [key: Keys.parse(keys[1]), contents: fabcoData]
    results[1] == [key: Keys.parse(keys[2]), contents: cabcoData]
  }

  Should 'limit results to 50'() {
    given:
    List keys = []
    while (keys.size() < 51) {
      keys << Keys.getApplicationKey('a'+keys.size())
    }

    when:
    List results = typeaheadController.typeaheadResults('a', 100)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS) >> keys
    cacheService.keysByType(_) >> []
    50 * cacheService.retrieve(_, Object) >> [:]
    keys.size() == 51
    results.size() == 50
  }

  Should 'respect user-specified size limit'() {
    given:
    List keys = [Keys.getApplicationKey('abc'), Keys.getApplicationKey('abd')]

    when:
    List results = typeaheadController.typeaheadResults('ab', 1)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS) >> keys
    cacheService.keysByType(_) >> []
    1 * cacheService.retrieve(keys[0], Object) >> [:]
    results.key == [Keys.parse(keys[0])]
  }

  Should 'sort results by query, then alphabetically, ignoring the category'() {
    given:
    List applicationKeys = [Keys.getApplicationKey('abx'), Keys.getApplicationKey('bac')]
    List serverGroupKeys = [Keys.getServerGroupKey('abc', 'account', 'region')]

    when:
    List results = typeaheadController.typeaheadResults('b', 3)

    then:
    1 * cacheService.keysByType(Keys.Namespace.APPLICATIONS) >> applicationKeys
    1 * cacheService.keysByType(Keys.Namespace.SERVER_GROUPS) >> serverGroupKeys
    cacheService.keysByType(_) >> []
    results*.key as List == [Keys.parse(applicationKeys[1]), Keys.parse(serverGroupKeys[0]), Keys.parse(applicationKeys[0])]

  }

}

