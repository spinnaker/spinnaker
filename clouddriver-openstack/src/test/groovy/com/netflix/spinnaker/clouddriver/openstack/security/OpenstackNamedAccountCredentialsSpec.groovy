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
import spock.lang.Specification

class OpenstackNamedAccountCredentialsSpec extends Specification {


  def "Provider factory returns v2 provider"() {
    setup:
    // Mock out the authenticate call within Openstack4J
    IOSClientBuilder.V2.metaClass.authenticate = { Mock(OSClient.OSClientV2) }

    when:
    def credentials = new OpenstackNamedAccountCredentials("name", "test", "v2", "test", "user", "pw", "tenant", "domain", "endpoint", false)

    then:
    credentials.credentials.provider instanceof OpenstackClientV2Provider
    credentials.credentials.provider.client instanceof OSClient.OSClientV2
  }

  def "Provider factory returns v3 provider"() {
    setup:
    // Mock out the authenticate call within Openstack4J
    IOSClientBuilder.V3.metaClass.authenticate = { Mock(OSClient.OSClientV3) }

    when:
    def credentials = new OpenstackNamedAccountCredentials("name", "test", "v3", "test", "user", "pw", "tenant", "domain", "endpoint", false)

    then:
    credentials.credentials.provider instanceof OpenstackClientV3Provider
    credentials.credentials.provider.client instanceof OSClient.OSClientV3
  }


  def "Provider factory throws exception for unknown account type"() {
    when:
    def credentials = new OpenstackNamedAccountCredentials("name", "test", "v1", "test", "user", "pw", "tenant", "domain", "endpoint", false)

    then:
    thrown IllegalArgumentException
  }

}
