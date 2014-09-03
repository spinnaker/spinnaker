/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.data.aws

import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.data.aws.cachers.AbstractInfrastructureCachingAgent
import com.netflix.spinnaker.oort.data.aws.cachers.DiscoveryCachingAgent
import com.netflix.spinnaker.oort.config.discovery.DiscoveryApi
import com.netflix.spinnaker.oort.model.discovery.DiscoveryApplication
import com.netflix.spinnaker.oort.model.discovery.DiscoveryApplications
import com.netflix.spinnaker.oort.model.discovery.DiscoveryInstance
import spock.lang.Shared

class DiscoveryCachingAgentSpec extends AbstractCachingAgentSpec {
  @Shared
  DiscoveryApi discoveryApi

  private static final String[] ACCOUNTS = ['test', 'othertest']
  private static final String REGION = 'us-east-1'

  @Override
  AbstractInfrastructureCachingAgent getCachingAgent() {
    def accounts = ACCOUNTS.collect { new NetflixAmazonCredentials(name: it, discovery: 'http://discotest') }
    discoveryApi = Mock(DiscoveryApi)
    new DiscoveryCachingAgent(accounts, REGION, discoveryApi)
  }

  void "load new health when new ones are available, remove missing ones, and do nothing when theres nothing new to process"() {
    setup:
    def health = new DiscoveryInstance(instanceId: "i-12345")
    def keys = ACCOUNTS.collect { Keys.getInstanceHealthKey(health.instanceId, it, REGION, DiscoveryCachingAgent.PROVIDER_NAME) }

    when:
    agent.load()

    then:
    1 * discoveryApi.loadDiscoveryApplications() >> new DiscoveryApplications(applications: [new DiscoveryApplication(instances: [health])])
    for (key in keys) {
      1 * cacheService.put(key, health)
    }

    when:
    agent.load()

    then:
    1 * discoveryApi.loadDiscoveryApplications() >> new DiscoveryApplications(applications: [])
    for (key in keys) {
      1 * cacheService.free(key)
    }

    when:
    agent.load()

    then:
    1 * discoveryApi.loadDiscoveryApplications() >> new DiscoveryApplications(applications: [])
    0 * cacheService.free(_)
  }
}
