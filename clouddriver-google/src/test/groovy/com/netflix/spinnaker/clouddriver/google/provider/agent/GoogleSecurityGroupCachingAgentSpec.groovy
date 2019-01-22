/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Firewall
import com.google.api.services.compute.model.FirewallList
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Subject

class GoogleSecurityGroupCachingAgentSpec extends Specification {

  static final String PROJECT_NAME = "my-project"
  static final String REGION = 'global'
  static final String ACCOUNT_NAME = 'some-account-name'

  void "should add security groups on initial run"() {
    setup:
      def registry = new DefaultRegistry()
      def computeMock = Mock(Compute)
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).name(ACCOUNT_NAME).compute(computeMock).build()
      def firewallsMock = Mock(Compute.Firewalls)
      def firewallsListMock = Mock(Compute.Firewalls.List)
      def securityGroupA = new Firewall(name: 'name-a',
                                        selfLink: 'https://compute.googleapis.com/compute/v1/projects/my-project/global/firewalls/name-a')
      def securityGroupB = new Firewall(name: 'name-b',
                                        selfLink: 'https://compute.googleapis.com/compute/v1/projects/my-project/global/firewalls/name-b')
      def keyGroupA = Keys.getSecurityGroupKey(securityGroupA.name as String,
                                               securityGroupA.name as String,
                                               REGION,
                                               ACCOUNT_NAME)
      def keyGroupB = Keys.getSecurityGroupKey(securityGroupB.name as String,
                                               securityGroupB.name as String,
                                               REGION,
                                               ACCOUNT_NAME)
      def firewallListReal = new FirewallList(items: [
          securityGroupA,
          securityGroupB
      ])
      def ProviderCache providerCache = Mock(ProviderCache)
      @Subject GoogleSecurityGroupCachingAgent agent = new GoogleSecurityGroupCachingAgent("testApplicationName",
                                                                                           credentials,
                                                                                           new ObjectMapper(),
                                                                                           registry)
      // We are passing registry in above because the constructor needs it.
      // However there is also an @autowired registry on the instance this test
      // needs. Normally we could use the autowire in the constructor but the
      // autowire doesnt happen in this test so we inject it above.
      // Normally it is autowired so doesnt seem appropriate to bind the local
      // registry from the constructor (overriding the autowire) so we'll just
      // reset it here.
      agent.registry = registry

    when:
      def cache = agent.loadData(providerCache)

    then:
      1 * computeMock.firewalls() >> firewallsMock
      1 * firewallsMock.list(PROJECT_NAME) >> firewallsListMock
      1 * firewallsListMock.execute() >> firewallListReal
      with(cache.cacheResults.get(Keys.Namespace.SECURITY_GROUPS.ns)) { Collection<CacheData> cd ->
        cd.size() == 2
        cd.id.containsAll([keyGroupA, keyGroupB])
      }
      1 * providerCache.getAll("onDemand", [keyGroupA, keyGroupB])
      0 * _
  }
}
