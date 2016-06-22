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

package com.netflix.spinnaker.clouddriver.openstack.client

import org.openstack4j.api.OSClient
import spock.lang.Specification
import spock.lang.Unroll


@Unroll
class OpenstackClientV2ProviderSpec extends Specification {
  OpenstackClientV2Provider provider
  OSClient.OSClientV2 osClient

  def "setup"() {
    osClient = Mock(OSClient.OSClientV2)
    provider = new OpenstackClientV2Provider(osClient, null)
  }

  def "test get regions - #testCase"() {
    given:
    provider.regions = expectedResult

    when:
    List<String> result = provider.getAllRegions()

    then:
    result == expectedResult

    where:
    testCase | expectedResult
    'empty'  | []
    'null'   | null
    'found'  | ['east', 'west']
  }
}
