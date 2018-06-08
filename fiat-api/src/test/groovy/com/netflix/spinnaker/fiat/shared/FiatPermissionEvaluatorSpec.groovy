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

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Application
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.fiat.model.resources.ResourceType
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class FiatPermissionEvaluatorSpec extends Specification {
  DynamicConfigService dynamicConfigService = Mock(DynamicConfigService) {
    _ * isEnabled('fiat', true) >> { return true }
  }
  FiatService fiatService = Mock(FiatService)
  Registry registry = new NoopRegistry();

  @Subject
  FiatPermissionEvaluator evaluator = new FiatPermissionEvaluator(
      dynamicConfigService,
      registry,
      fiatService,
      buildConfigurationProperties()
  )

  @Unroll
  def "should parse application name"() {
    setup:
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
    1 * fiatService.getUserPermission("testUser") >> new UserPermission(
            applications: [
                new Application(name: "abc-def",
                                permissions: Permissions.factory([
                                    (Authorization.READ): ["testRole"]
                                ])),
            ],
            roles: [new Role("testRole")]
        ).getView()
    result

    where:
    resource           | resourceName         | resourceType
    "abc-def"          | "abc-def"            | ResourceType.APPLICATION

    authorization = 'READ'
  }

  def "should grant or deny permission"() {
    setup:
    Authentication authentication = new PreAuthenticatedAuthenticationToken("testUser",
                                                                            null,
                                                                            new ArrayList<>())
    String resource = "readable"
    String svcAcct = "svcAcct"

    UserPermission.View upv = new UserPermission.View()
    upv.setApplications([new Application.View().setName(resource)
                                               .setAuthorizations([Authorization.READ] as Set)] as Set)
    upv.setServiceAccounts([new ServiceAccount.View().setName(svcAcct)
                                                     .setMemberOf(["foo"])] as Set)

    when:
    def hasPermission = evaluator.hasPermission(authentication,
                                                resource,
                                                'APPLICATION',
                                                'READ')

    then:
    1 * fiatService.getUserPermission("testUser") >> upv
    hasPermission

    when:
    hasPermission = evaluator.hasPermission(authentication,
                                            resource,
                                            'APPLICATION',
                                            'WRITE') // Missing authorization

    then:
    1 * fiatService.getUserPermission("testUser") >> upv
    !hasPermission

    when:
    hasPermission = evaluator.hasPermission(authentication,
                                            resource, // Missing resource
                                            'SERVICE_ACCOUNT',
                                            'WRITE')

    then:
    1 * fiatService.getUserPermission("testUser") >> upv
    !hasPermission

    when:
    hasPermission = evaluator.hasPermission(authentication,
                                            svcAcct,
                                            'SERVICE_ACCOUNT',
                                            'WRITE')

    then:
    1 * fiatService.getUserPermission("testUser") >> upv
    hasPermission
  }

  private static FiatClientConfigurationProperties buildConfigurationProperties() {
    FiatClientConfigurationProperties configurationProperties = new FiatClientConfigurationProperties();
    configurationProperties.enabled = true
    configurationProperties.cache.maxEntries = 0

    return configurationProperties
  }
}
