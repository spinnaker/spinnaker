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

package com.netflix.spinnaker.clouddriver.cache

import com.netflix.spinnaker.cats.cache.Cache
import spock.lang.Shared
import spock.lang.Specification;

class CatsSearchProviderSpec extends Specification {
  def catsInMemorySearchProperties = new CatsInMemorySearchProperties()
  def cache = Mock(Cache)

  def providers = [
    Mock(SearchableProvider) {
      parseKey(_) >> { String k -> return null }
    },
    Mock(SearchableProvider) {
      parseKey(_) >> { String k -> return ["originalKey": k] }
    }
  ]

  def catsSearchProvider = new CatsSearchProvider(catsInMemorySearchProperties, cache, providers)

  @Shared
  def instanceIdentifiers = [
    "aws:instances:prod:us-west-2:I-1234",
    "aws:instances:prod:us-west-2:I-5678",
    "aws:instances:prod:us-west-2:I-9012",
    "aws:instances:prod:us-west-2:I-3456",
    "aws:instances:prod:us-west-2:I-7890",
  ]


  def "should parse instance identifiers"() {
    when:
    catsSearchProvider.run()

    then:
    cache.getIdentifiers("instances") >> { return instanceIdentifiers }

    catsSearchProvider.cachedIdentifiersByType.get() == [
      "instances": instanceIdentifiers.collect {
        [
          originalKey: it.toLowerCase(),
          "_id"      : it.toLowerCase()
        ]
      }
    ]
  }

  def "should handle unparseable instance identifiers"() {
    when:
    providers.clear()

    then:
    catsSearchProvider.run()

    then:
    catsSearchProvider.cachedIdentifiersByType.get() == [:]

    when:
    providers.add(
      Mock(SearchableProvider) {
        parseKey(_) >> { String k -> return null }
      }
    )

    then:
    catsSearchProvider.cachedIdentifiersByType.get() == [:]
  }
}
