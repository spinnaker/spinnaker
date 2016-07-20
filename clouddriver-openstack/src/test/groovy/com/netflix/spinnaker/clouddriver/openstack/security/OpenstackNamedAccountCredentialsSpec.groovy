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

package com.netflix.spinnaker.clouddriver.openstack.security

import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientV2Provider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientV3Provider
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.api.OSClient
import org.openstack4j.api.client.IOSClientBuilder
import org.openstack4j.model.identity.v2.Access
import org.openstack4j.model.identity.v3.Token
import spock.lang.Specification

class OpenstackNamedAccountCredentialsSpec extends Specification {

  List<String> regions

  def "setup"() {
    regions = ['east']
  }

  def "Provider factory returns v2 provider"() {
    setup:
    // Mock out the authenticate call within Openstack4J
    OSClient.OSClientV2 mockClient = Mock(OSClient.OSClientV2)
    IOSClientBuilder.V2.metaClass.authenticate = { mockClient }

    when:
    def credentials = new OpenstackNamedAccountCredentials("name", "test", "v2", "v1", "test", "user", "pw", "tenant", "domain", "endpoint", regions, false)

    then:
    1 * mockClient.access >> Mock(Access)
    credentials.credentials.provider instanceof OpenstackClientV2Provider
    credentials.credentials.provider.access instanceof Access
  }

  def "Provider factory returns v3 provider"() {
    setup:
    // Mock out the authenticate call within Openstack4J
    OSClient.OSClientV3 mockClient = Mock(OSClient.OSClientV3)
    IOSClientBuilder.V3.metaClass.authenticate = { mockClient }

    when:
    def credentials = new OpenstackNamedAccountCredentials("name", "test", "v3", "v1", "test", "user", "pw", "tenant", "domain", "endpoint", regions, false)

    then:
    1 * mockClient.token >> Mock(Token)
    credentials.credentials.provider instanceof OpenstackClientV3Provider
    credentials.credentials.provider.token instanceof Token
  }


  def "Provider factory throws exception for unknown account type"() {
    when:
    new OpenstackNamedAccountCredentials("name", "test", "v1", "v1", "test", "user", "pw", "tenant", "domain", "endpoint", regions, false)

    then:
    thrown IllegalArgumentException
  }

}
