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

package com.netflix.spinnaker.oort.model.aws

import com.netflix.frigga.Names
import com.netflix.spinnaker.oort.model.Health
import com.netflix.spinnaker.oort.model.HealthProvider
import com.netflix.spinnaker.oort.model.ServerGroup
import com.netflix.spinnaker.oort.security.NamedAccountProvider
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class AtlasHealthProvider implements HealthProvider {
  @Autowired
  RestTemplate restTemplate

  @Autowired
  NamedAccountProvider namedAccountProvider

  @Override
  Health getHealth(String account, ServerGroup serverGroup) {
    if (!(serverGroup instanceof AmazonServerGroup)) {
      return null
    }
    AmazonNamedAccount amazonNamedAccount = (AmazonNamedAccount) namedAccountProvider.get(account)
    if (!amazonNamedAccount.atlasHealth) {
      return null
    }
    def names = Names.parseName(serverGroup.name)
    def url = String.format(amazonNamedAccount.atlasHealth, serverGroup.region)
    def result = new AtlasHealth()
    if (url) {
      def health = getAtlasHealth(url, names.cluster, serverGroup.name)
      if (health.containsKey(serverGroup.name)) {
        result.serverGroupHealth = health[serverGroup.name] ? "healthy" : "unhealthy"
      } else {
        result.serverGroupHealth = "unknown"
      }
    }
    result
  }

  private Map getAtlasHealth(String url, String cluster, String serverGroup) {
    def result = [:]
    try {
      def list = restTemplate.getForObject("${url}/api/v1/instance?q=cluster,$cluster,:eq,asg,$serverGroup,:eq,:and", List)
      list.each { Map input ->
        result[input.asg] = input.isHealthy
      }
    } catch (IGNORE) {
    }
    result
  }

}
