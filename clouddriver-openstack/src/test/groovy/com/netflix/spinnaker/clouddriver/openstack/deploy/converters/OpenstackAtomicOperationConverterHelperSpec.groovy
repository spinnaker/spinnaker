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

package com.netflix.spinnaker.clouddriver.openstack.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.OpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import spock.lang.Shared
import spock.lang.Specification

class OpenstackAtomicOperationConverterHelperSpec extends Specification {

  def "handles the account name"() {
    given:
    def openstackCredentials = Mock(OpenstackCredentials)
    def creds = Mock(OpenstackNamedAccountCredentials) {
      1 * getCredentials() >> openstackCredentials
    }
    def credentialsSupport = Mock(AbstractAtomicOperationsCredentialsSupport) {
      1 * getCredentialsObject('os-account') >> creds
      1 * getObjectMapper() >> new ObjectMapper()
    }

    when:
    def description = OpenstackAtomicOperationConverterHelper.convertDescription(input, credentialsSupport, OpenstackAtomicOperationDescription)

    then:
    description
    description.account == 'os-account'
    description.region == 'west'
    description.credentials == openstackCredentials

    where:
    input << [
      [account: 'os-account', region: 'west'],
      [credentials: 'os-account', region: 'west'],
      [account: 'os-account', credentials: 'something else', region: 'west']
    ]
  }
}
