/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.cache

import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class KeysSpec extends Specification {

  void "test parse key format - #testCase"() {
    when:
    Map<String, String> result = Keys.parse(value)

    then:
    result == expected

    where:
    testCase            | value         | expected
    'no delimiter'      | 'test'        | null
    'less than 5 parts' | 'test:test'   | null
    'more than 5 parts' | 't:t:t:t:t:t' | null
  }

  void "test invalid parts - #testCase"() {
    when:
    Map<String, String> result = Keys.parse(value)

    then:
    result == expected

    where:
    testCase    | value               | expected
    'provider'  | 'openstackprovider' | null
    'namespace' | 'stuff'             | null
  }

  void "test instance map"() {
    given:
    String instanceId = 'testInstance'
    String account = 'testAccount'
    String region = 'testRegion'
    String key = Keys.getInstanceKey(instanceId, account, region)

    when:
    Map<String, String> result = Keys.parse(key)

    then:
    result == [account: account, region: region, instanceId: instanceId, provider: OpenstackCloudProvider.ID, type: Keys.Namespace.INSTANCES.ns]
  }
}
