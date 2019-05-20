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
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.slf4j.MDC
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class FiatPermissionEvaluatorSpec extends Specification {
  FiatService fiatService = Mock(FiatService)
  Registry registry = new NoopRegistry()
  FiatStatus fiatStatus = Mock(FiatStatus) {
    _ * isEnabled() >> { return true }
  }

  @Shared
  def authentication = new PreAuthenticatedAuthenticationToken("testUser", null, [])

  @Subject
  FiatPermissionEvaluator evaluator = new FiatPermissionEvaluator(
      registry,
      fiatService,
      buildConfigurationProperties(),
      fiatStatus,
      FiatPermissionEvaluator.RetryHandler.NOOP
  )

  def cleanup() {
    MDC.clear()
  }

  @Unroll
  def "should parse application name"() {
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
    String resource = "readable"
    String svcAcct = "svcAcct"

    UserPermission.View upv = new UserPermission.View()
    upv.setApplications([new Application.View().setName(resource)
                                               .setAuthorizations([Authorization.READ] as Set)] as Set)
    upv.setServiceAccounts([new ServiceAccount.View().setName(svcAcct)
                                                     .setMemberOf(["foo"])] as Set)
    fiatService.getUserPermission("testUser") >> upv

    expect:
    evaluator.hasPermission(authentication, resource, 'APPLICATION', 'READ')

    // Missing authorization:
    !evaluator.hasPermission(authentication, resource, 'APPLICATION', 'WRITE')

    // Missing resource
    !evaluator.hasPermission(authentication, resource, 'SERVICE_ACCOUNT', 'WRITE')

    evaluator.hasPermission(authentication, svcAcct, 'SERVICE_ACCOUNT', 'WRITE')
  }

  @Unroll
  def "should retry fiat requests"() {
    given:
    def retryConfiguration = new FiatClientConfigurationProperties.RetryConfiguration()
    retryConfiguration.setMaxBackoffMillis(10)
    retryConfiguration.setInitialBackoffMillis(15)
    retryConfiguration.setRetryMultiplier(1.5)

    and:
    FiatPermissionEvaluator evaluator = new FiatPermissionEvaluator(
            registry,
            fiatService,
            buildConfigurationProperties(),
            fiatStatus,
            new FiatPermissionEvaluator.ExponentialBackoffRetryHandler(retryConfiguration)
    )

    when:
    def view = evaluator.getPermission("testUser")

    then:
    2 * fiatService.getUserPermission("testUser") >> { throw new IllegalStateException("something something something")}

    view == null
  }

  @Unroll
  def "should support legacy fallback when fiat is unavailable"() {
    given:
    MDC.put(AuthenticatedRequest.Header.USER.header, "fallback")
    MDC.put(AuthenticatedRequest.Header.ACCOUNTS.header, "account1,account2")

    when:
    def permission = evaluator.getPermission("testUser")
    def hasPermission = evaluator.hasPermission(authentication, "my_application", "APPLICATION", "READ")

    then:
    2 * fiatStatus.isLegacyFallbackEnabled() >> { return legacyFallbackEnabled }
    2 * fiatService.getUserPermission("testUser") >> { throw new IllegalStateException("something something something")}

    hasPermission == expectedToHavePermission
    permission?.name == expectedName
    permission?.accounts*.name?.sort() == expectedAccounts?.sort()

    where:
    legacyFallbackEnabled || expectedToHavePermission || expectedName || expectedAccounts
    true                  || true                     || "fallback"   || ["account1", "account2"]
    false                 || false                    || null         || null
  }

  @Unroll
  def "should deny access to an application that has an empty set of authorizations"() {
    when:
    def hasReadPermission = evaluator.hasPermission(authentication, "my_application", "APPLICATION", "READ")
    def hasWritePermission = evaluator.hasPermission(authentication, "my_application", "APPLICATION", "WRITE")

    then:
    2 * fiatService.getUserPermission("testUser") >> {
      return new UserPermission.View()
          .setApplications(
          [
              new Application.View()
                  .setName("my_application")
                  .setAuthorizations(authorizations as Set<Authorization>)
          ] as Set<Application.View>
      )
    }

    !hasReadPermission
    !hasWritePermission

    where:
    authorizations << [null, [] as Set]
  }

  @Unroll
  def "should allow access to unknown applications"() {
    when:
    def hasPermission = evaluator.hasPermission(authentication, "my_application", "APPLICATION", "READ")

    then:
    1 * fiatService.getUserPermission("testUser") >> {
      return new UserPermission.View()
          .setAllowAccessToUnknownApplications(allowAccessToUnknownApplications)
          .setApplications(Collections.emptySet())
    }

    hasPermission == expectedToHavePermission

    where:
    allowAccessToUnknownApplications || expectedToHavePermission
    false                            || false
    true                             || true
  }

  @Unroll
  def "should allow an admin to access #resourceType"() {
    given:
    fiatService.getUserPermission("testUser") >> {
      return new UserPermission.View()
          .setApplications(Collections.emptySet())
          .setAdmin(true)
    }

    expect:
    evaluator.hasPermission(authentication, "my_resource", resourceType, "READ")
    evaluator.hasPermission(authentication, "my_resource", resourceType, "WRITE")

    where:
    resourceType << ResourceType.values()*.toString()
  }

  def "should support isAdmin check for a user"() {
    given:
    1 * fiatService.getUserPermission("testUser") >> {
      return new UserPermission.View()
          .setApplications(Collections.emptySet())
          .setAdmin(isAdmin)
    }

    expect:
    evaluator.isAdmin(authentication) == expectedIsAdmin

    where:
    isAdmin || expectedIsAdmin
    false   || false
    true    || true
  }

  private static FiatClientConfigurationProperties buildConfigurationProperties() {
    FiatClientConfigurationProperties configurationProperties = new FiatClientConfigurationProperties()
    configurationProperties.enabled = true
    configurationProperties.cache.maxEntries = 0
    configurationProperties.cache.expiresAfterWriteSeconds = 0
    return configurationProperties
  }
}
