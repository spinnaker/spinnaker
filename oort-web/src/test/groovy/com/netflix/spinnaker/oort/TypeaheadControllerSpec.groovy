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
    List keys = ['miss','FABCO', 'cabco']
    Map fabcoData = [data: 1]
    Map cabcoData = [data: 2]

    when:
    List results = typeaheadController.typeaheadResults('aBC', 10)

    then:
    1 * cacheService.keys() >> keys
    1 * cacheService.retrieve(keys[1], Map) >> fabcoData
    1 * cacheService.retrieve(keys[2], Map) >> cabcoData
    0 * _

    results.size() == 2
    results[0] == [key: keys[1], contents: fabcoData]
    results[1] == [key: keys[2], contents: cabcoData]
  }

  Should 'limit results to 50'() {
    given:
    List keys = []
    while (keys.size() < 51) {
      keys << 'a'+keys.size()
    }

    when:
    List results = typeaheadController.typeaheadResults('a', 100)

    then:
    1 * cacheService.keys() >> keys
    50 * cacheService.retrieve(_, Map) >> [:]
    keys.size() == 51
    results.size() == 50
  }

  Should 'respect user-specified size limit'() {
    given:
    List keys = ['abc', 'abd']

    when:
    List results = typeaheadController.typeaheadResults('ab', 1)

    then:
    1 * cacheService.keys() >> keys
    1 * cacheService.retrieve('abc', Map) >> [:]
    results.key == ['abc']
  }

  Should 'search on all words'() {
    given:
    List keys = ['abcd','bcde','cdef']

    when:
    List results = typeaheadController.typeaheadResults('b d', 10)

    then:
    1 * cacheService.keys() >> keys
    cacheService.retrieve(_, Map) >> [:]
    results.key == ['abcd', 'bcde']
  }
}

