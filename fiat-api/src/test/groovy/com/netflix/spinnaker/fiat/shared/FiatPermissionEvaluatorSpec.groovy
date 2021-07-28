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

import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Application
import com.netflix.spinnaker.fiat.model.resources.Authorizable
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.fiat.model.resources.Resource
import com.netflix.spinnaker.fiat.model.resources.ResourceType
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount
import com.netflix.spinnaker.kork.common.Header
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Shared
import spock.lang.Subject
import spock.lang.Unroll

import javax.servlet.http.HttpServletResponse

class FiatPermissionEvaluatorSpec extends FiatSharedSpecification {

  @Subject
  FiatPermissionEvaluator evaluator = new FiatPermissionEvaluator(
          registry,
          fiatService,
          buildConfigurationProperties(),
          fiatStatus,
          FiatPermissionEvaluator.RetryHandler.NOOP
  )

  @Shared
  def authentication = new PreAuthenticatedAuthenticationToken("testUser", null, [])

  def cleanup() {
    MDC.clear()
    SecurityContextHolder.clearContext()
  }

  @Unroll
  def "should parse application name"() {
    when:
    def result = evaluator.hasPermission(authentication,
                                         resource,
                                         resourceType.name,
                                         authorization)

    then:
    1 * fiatService.getUserPermission("testUser") >> new UserPermission.View(new UserPermission())
    !result

    when:
    result = evaluator.hasPermission(authentication,
                                     resource,
                                     resourceType.name,
                                     authorization)

    then:
    1 * fiatService.getUserPermission("testUser") >> new UserPermission(
            applications: [
                new Application(name: "abc-def",
                                permissions: Permissions.factory([
                                    (Authorization.READ): ["testRole"] as Set
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

    SecurityContextHolder.getContext().setAuthentication(authentication)
    Resource resourceCanCreate = new Application().setName("app1")
    Resource resourceCannotCreate = new Application().setName("app2")
    fiatService.canCreate("testUser", 'APPLICATION', resourceCanCreate) >> null // doesn't return anything in case of success
    fiatService.canCreate("testUser", 'APPLICATION', resourceCannotCreate) >> {
      throw RetrofitError.httpError("", new Response("", HttpServletResponse.SC_NOT_FOUND, "", [], null), null, null)
    }

    expect:
    evaluator.hasPermission(authentication, resource, 'APPLICATION', 'READ')

    // Missing authorization:
    !evaluator.hasPermission(authentication, resource, 'APPLICATION', 'WRITE')

    // Missing resource
    !evaluator.hasPermission(authentication, resource, 'SERVICE_ACCOUNT', 'WRITE')

    evaluator.hasPermission(authentication, svcAcct, 'SERVICE_ACCOUNT', 'WRITE')

    evaluator.canCreate('APPLICATION', resourceCanCreate)
    !evaluator.canCreate('APPLICATION', resourceCannotCreate)
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
    MDC.put(Header.USER.header, "fallback")
    MDC.put(Header.ACCOUNTS.header, "account1,account2")

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
    resourceType << [ResourceType.ACCOUNT, ResourceType.APPLICATION, ResourceType.BUILD_SERVICE, ResourceType.ROLE, ResourceType.SERVICE_ACCOUNT]*.toString()
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

  @Unroll
  def "should evaluate permissions for extension resources"() {
    setup:
    def extensionResource = new MyExtensionResourceView()
    extensionResource.with {
      name = "extension_resource"
      authorizations = [Authorization.READ] as Set
    }

    when:
    def hasPermission = evaluator.hasPermission(authentication, "extension_resource", resourceType, authorization)

    then:
    1 * fiatService.getUserPermission("testUser") >> new UserPermission.View()
        .setExtensionResources([
            (MY_EXTENSION_RESOURCE): [extensionResource] as Set
        ])

    hasPermission == expectedHasPermission

    where:
    resourceType              | authorization | expectedHasPermission
    "MY_EXTENSION_RESOURCE"   | "READ"        | true
    "MY_EXTENSION_RESOURCE"   | "WRITE"       | false
    "YOUR_EXTENSION_RESOURCE" | "READ"        | false
    "YOUR_EXTENSION_RESOURCE" | "WRITE"       | false
  }

  private static FiatClientConfigurationProperties buildConfigurationProperties() {
    FiatClientConfigurationProperties configurationProperties = new FiatClientConfigurationProperties()
    configurationProperties.enabled = true
    configurationProperties.cache.maxEntries = 0
    configurationProperties.cache.expiresAfterWriteSeconds = 0
    return configurationProperties
  }

  private static MY_EXTENSION_RESOURCE = new ResourceType("MY_EXTENSION_RESOURCE");
  private static class MyExtensionResourceView implements Authorizable {
    String name
    Set<Authorization> authorizations
  }
}
