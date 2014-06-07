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

import com.netflix.spinnaker.oort.security.NamedAccountProvider
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Specification

import java.lang.Void as Should

class Front50ApplicationLoaderSpec extends Specification {

  @Shared
  Front50ApplicationLoader loader

  def setup() {
    loader = new Front50ApplicationLoader()
    def namedAccountProvider = Mock(NamedAccountProvider)
    namedAccountProvider.getAccountNames() >> ["test"]
    namedAccountProvider.get("test") >> new AmazonNamedAccount(null, null, null, null, "front50", null, null)
    loader.namedAccountProvider = namedAccountProvider
  }

  Should "call front50 to get application details"() {
    setup:
    def restTemplate = Mock(RestTemplate)
    loader.restTemplate = restTemplate

    when:
    loader.load()

    then:
    1 * restTemplate.getForObject("front50/applications", List) >> []
  }
}
