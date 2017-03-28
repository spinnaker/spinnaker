/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.shared

import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Application
import com.netflix.spinnaker.fiat.model.resources.ResourceType
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import spock.lang.Specification
import spock.lang.Unroll

class FiatPermissionEvaluatorSpec extends Specification {

  @Unroll
  def "should parse application name"() {
    setup:
    FiatClientConfigurationProperties configProps = new FiatClientConfigurationProperties()
    configProps.cache.maxEntries = 0
    FiatService fiatService = Mock(FiatService)
    FiatPermissionEvaluator evaluator = new FiatPermissionEvaluator(fiatEnabled: true,
                                                                    fiatService: fiatService,
                                                                    configProps: configProps)
    evaluator.afterPropertiesSet()

    Authentication authentication = new PreAuthenticatedAuthenticationToken("testUser",
                                                                            null,
                                                                            new ArrayList<>())

    when:
    def result = evaluator.hasPermission(authentication,
                                         resource,
                                         resourceType.name(),
                                         authorization)

    then:
    1 * fiatService.getUserPermission("testUser") >> new UserPermission.View(new UserPermission())
    !result

    when:

    result = evaluator.hasPermission(authentication,
                                     resource,
                                     resourceType.name(),
                                     authorization)

    then:
    1 * fiatService.getUserPermission("testUser") >> new UserPermission.View(
        new UserPermission(
            applications: [new Application(name: "abc")]
        ))
    result

    where:
    resource           | resourceName | resourceType
    "abc"              | "abc"        | ResourceType.APPLICATION
    "abc-def"          | "abc"        | ResourceType.APPLICATION
    "abc-def-ghi"      | "abc"        | ResourceType.APPLICATION
    "abc-def-ghi-1234" | "abc"        | ResourceType.APPLICATION

    authorization = 'READ'
  }
}
