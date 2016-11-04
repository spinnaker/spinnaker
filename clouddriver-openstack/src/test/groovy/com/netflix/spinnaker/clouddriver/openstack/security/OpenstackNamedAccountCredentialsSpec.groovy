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

import com.netflix.spinnaker.clouddriver.consul.config.ConsulConfig
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackIdentityV3Provider
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties.LbaasConfig
import org.openstack4j.api.OSClient
import org.openstack4j.api.client.IOSClientBuilder
import org.openstack4j.model.identity.v3.Token
import spock.lang.Specification

class OpenstackNamedAccountCredentialsSpec extends Specification {

  List<String> regions

  def "setup"() {
    regions = ['east']
  }

  def "Provider factory returns v3 provider"() {
    setup:
    // Mock out the authenticate call within Openstack4J
    OSClient.OSClientV3 mockClient = Mock(OSClient.OSClientV3)
    IOSClientBuilder.V3.metaClass.authenticate = { mockClient }

    when:
    def credentials = new OpenstackNamedAccountCredentials("name", "test", "main", "user", "pw", "project", "domain", "endpoint", [], false, "", new LbaasConfig(pollTimeout: 60, pollInterval: 5), new ConsulConfig(), null)
    def client = credentials.credentials.provider.client

    then:
    1 * mockClient.token >> Mock(Token)
    credentials.credentials.provider.identityProvider instanceof OpenstackIdentityV3Provider
    credentials.credentials.provider.identityProvider.token instanceof Token
    client instanceof OSClient.OSClientV3
  }

}
