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

  def "get base name - #testCase"() {
    when:
    String result = resolver.getBaseName(baseName)

    then:
    result == expected

    where:
    testCase       | baseName           | expected
    'not matching' | 'test'             | null
    'matching'     | 'north-south-east' | 'north'
    'null'         | null               | null
  }

  def "get internal port - #testCase"() {
    when:
    int result = resolver.getInternalPort(description)

    then:
    result == expected

    where:
    testCase    | description        | expected
    'not found' | 'test'             | -1
    'found'     | 'internal_port=20' | 20
    'null'      | null               | -1
  }
}
