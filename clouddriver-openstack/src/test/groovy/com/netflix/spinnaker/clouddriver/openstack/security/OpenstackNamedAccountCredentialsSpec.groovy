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
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackIdentityV3Provider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties.LbaasConfig
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties.StackConfig
import org.openstack4j.api.OSClient
import org.openstack4j.model.identity.v3.Token
import org.openstack4j.openstack.compute.domain.ext.ExtAvailabilityZone
import spock.lang.Specification
import spock.lang.Unroll

class OpenstackNamedAccountCredentialsSpec extends Specification {

  List<String> regions

  def "setup"() {
    regions = ['east']
  }

  def "Provider factory returns v3 provider"() {
    setup:
    // Mock out the authenticate call within Openstack4J
    OSClient.OSClientV3 mockClient = Mock(OSClient.OSClientV3)
    def openStackIdentityProvider = GroovySpy(OpenstackIdentityV3Provider, global: true)
    openStackIdentityProvider.buildClient() >> mockClient

    when:
    def credentials = new OpenstackNamedAccountCredentials("name", "test", "main", "user", "pw", "project", "domain", "endpoint", [], false, "", new LbaasConfig(pollTimeout: 60, pollInterval: 5),new StackConfig(pollTimeout: 60, pollInterval: 5), new ConsulConfig(), null)
    def client = credentials.credentials.provider.client

    then:
    1 * mockClient.token >> Mock(Token)
    credentials.credentials.provider.identityProvider instanceof OpenstackIdentityV3Provider
    credentials.credentials.provider.identityProvider.token instanceof Token
    client instanceof OSClient.OSClientV3
  }

  static def azA = new ExtAvailabilityZone(zoneName: "azA", zoneState: new ExtAvailabilityZone.ExtZoneState(available: true))
  static def azB = new ExtAvailabilityZone(zoneName: "azB", zoneState: new ExtAvailabilityZone.ExtZoneState(available: true))
  static def azUnavailable = new ExtAvailabilityZone(zoneName: "azC", zoneState: new ExtAvailabilityZone.ExtZoneState(available: false))

  @Unroll()
  def "Builder populates region-to-zone map: #description"() {
    setup:
    OpenstackClientProvider mockProvider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global: true) {
      OpenstackProviderFactory.createProvider(_) >> mockProvider
    }

    when:
    def builder = new OpenstackNamedAccountCredentials.Builder()
    builder.regions = regions
    def account = builder.build()

    then:
    1 * mockProvider.getZones("r1") >> r1_zones
    _ * mockProvider.getZones("r2") >> r2_zones
    account.regionToZones == expected

    where:
    description               | regions      | r1_zones                  | r2_zones   | expected
    "simple case"             | ["r1"]       | [azA]                     | null       | ["r1": ["azA"]]
    "multiple regions"        | ["r1", "r2"] | [azA]                     | [azB]      | ["r1": ["azA"], "r2": ["azB"]]
    "multiple zones"          | ["r1"]       | [azA, azB]                | null       | ["r1": ["azA", "azB"]]
    "skips unavailable zones" | ["r1"]       | [azA, azUnavailable, azB] | null       | ["r1": ["azA", "azB"]]
    "empty region"            | ["r1", "r2"] | null                      | [azA, azB] | ["r1": [], "r2": ["azA", "azB"]]
  }
}
