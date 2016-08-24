/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.domain

import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class LoadBalancerResolverSpec extends Specification {

  LoadBalancerResolver resolver = new Object() as LoadBalancerResolver

  def "get internal port - #testCase"() {
    when:
    Map<String, String> result = resolver.parseListenerKey(description)

    then:
    result.toString() == expected.toString()

    where:
    testCase    | description                        | expected
    'not found' | 'test'                             | [:]
    'found'     | 'HTTP:80:HTTP:8080'                | [externalProtocol: 'HTTP', externalPort: '80', internalProtocol: 'HTTP', internalPort: 8080]
    'null'      | null                               | [:]
  }

  def "get created time - #testCase"() {
    when:
    Long result = resolver.parseCreatedTime(description)

    then:
    result == expected

    where:
    testCase    | description                        | expected
    'not found' | 'test'                             | null
    'found'     | 'created_time=42'                  | 42l
    'found'     | 'internal_port=20,created_time=42' | 42l
    'found'     | 'created_time=42,internal_port=20' | 42l
    'null'      | null                               | null
  }
}
