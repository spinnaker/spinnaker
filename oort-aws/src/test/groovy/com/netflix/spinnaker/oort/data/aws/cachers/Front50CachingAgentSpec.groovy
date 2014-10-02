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

package com.netflix.spinnaker.oort.data.aws.cachers

import com.netflix.spinnaker.amos.aws.NetflixAssumeRoleAmazonCredentials
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.CacheService
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class Front50CachingAgentSpec extends Specification {

  def creds = Stub(NetflixAssumeRoleAmazonCredentials) {
    getFront50() >> { "front50" }
  }

  @Shared
  CacheService cacheService

  @Subject
  Front50ApplicationCachingAgent front50 = new Front50ApplicationCachingAgent(creds)

  def setup() {
    cacheService = Mock(CacheService)
    front50.cacheService = cacheService
  }

  void "should call front50 to get application details"() {
    setup:
    def restTemplate = Mock(RestTemplate)
    front50.restTemplate = restTemplate

    when:
    front50.load()

    then:
    1 * restTemplate.getForObject("front50/applications", List) >> []
  }

  void "should remove front50 applications from cache when they disappear"() {
    setup:
    def restTemplate = Mock(RestTemplate)
    front50.restTemplate = restTemplate

    when:
    front50.load()

    then:
    1 * restTemplate.getForObject("front50/applications", List) >> [[name: "app1"], [name: "app2"]]
    2 * cacheService.retrieve(_, _)
    2 * cacheService.put(_, _)

    when:
    front50.load()

    then:
    1 * restTemplate.getForObject("front50/applications", List) >> [[name: "app1"]]
    1 * cacheService.free(Keys.getApplicationKey("app2"))
    1 * cacheService.retrieve(_, _)
  }
}
