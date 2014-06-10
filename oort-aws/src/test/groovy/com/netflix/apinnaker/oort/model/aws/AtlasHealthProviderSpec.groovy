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

package com.netflix.apinnaker.oort.model.aws

import com.netflix.spinnaker.oort.model.ServerGroup
import com.netflix.spinnaker.oort.model.aws.AmazonServerGroup
import com.netflix.spinnaker.oort.model.aws.AtlasHealthProvider
import com.netflix.spinnaker.oort.security.NamedAccountProvider
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Specification

class AtlasHealthProviderSpec extends Specification {

  @Shared
  AtlasHealthProvider provider

  @Shared
  RestTemplate restTemplate

  def setup() {
    provider = new AtlasHealthProvider()
    def namedAccountProvider = Mock(NamedAccountProvider)
    def namedAccount = Mock(AmazonNamedAccount)
    namedAccount.getAtlasHealth() >> "atlas"
    namedAccountProvider.get("test") >> namedAccount
    provider.namedAccountProvider = namedAccountProvider
    restTemplate = Mock(RestTemplate)
    provider.restTemplate = restTemplate
  }

  void "getting health makes external call to atlas"() {
    setup:
    def cluster = "kato-main"
    def serverGroupName = "kato-main-v000"
    def region = "us-east-1"
    def serverGroup = new AmazonServerGroup(serverGroupName, "aws", region)

    when:
    provider.getHealth("test", serverGroup)

    then:
    1 * restTemplate.getForObject("atlas/api/v1/instance?q=cluster,$cluster,:eq,asg,$serverGroupName,:eq,:and", _)
  }
}
