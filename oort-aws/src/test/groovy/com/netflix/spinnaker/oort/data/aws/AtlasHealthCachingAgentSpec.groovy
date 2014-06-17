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

import com.netflix.spinnaker.oort.data.aws.cachers.AbstractInfrastructureCachingAgent
import com.netflix.spinnaker.oort.data.aws.cachers.AtlasHealthCachingAgent
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import org.springframework.web.client.RestTemplate
import spock.lang.Shared

class AtlasHealthCachingAgentSpec extends AbstractCachingAgentSpec {

  @Shared
  RestTemplate restTemplate

  @Override
  AbstractInfrastructureCachingAgent getCachingAgent() {
    def account = Mock(AmazonNamedAccount)
    account.getName() >> "test"
    account.getAtlasHealth() >> 'atlas-%s'
    restTemplate = Mock(RestTemplate)
    def agent = new AtlasHealthCachingAgent(account, "us-east-1")
    agent.restTemplate = restTemplate
    agent
  }

  void "load new health when new ones are available, remove missing ones, and do nothing when theres nothing new to process"() {
    setup:
    def health = [id: "i-12345", isHealthy: true]

    when:
    agent.load()

    then:
    1 * restTemplate.getForObject("atlas-us-east-1/api/v1/instance", _) >> [health]
    1 * reactor.notify('newHealth', _) >> { eventName, healthContext ->
      assert healthContext.data.instanceId == health.id
    }

    when:
    agent.load()

    then:
    1 * restTemplate.getForObject("atlas-us-east-1/api/v1/instance", _) >> []
    1 * reactor.notify('missingHealth', _) >> { eventName, healthContext ->
      assert healthContext.data.instanceId == health.id
    }

    when:
    agent.load()

    then:
    1 * restTemplate.getForObject("atlas-us-east-1/api/v1/instance", _) >> []
    0 * reactor.notify(_, _)
  }
}
