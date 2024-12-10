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
import com.netflix.spinnaker.fiat.model.SpinnakerAuthorities
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Application
import com.netflix.spinnaker.fiat.model.resources.Authorizable
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.fiat.model.resources.Resource
import com.netflix.spinnaker.fiat.model.resources.ResourceType
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount
import com.netflix.spinnaker.kork.common.Header
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Call;
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory;
import spock.lang.Shared
import spock.lang.Subject
import spock.lang.Unroll

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
  def authentication = new PreAuthenticatedAuthenticationToken("testUser", null,
          [SpinnakerAuthorities.forRoleName('test group')])

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
    !result

    when:
    fiatService = Mock(FiatService) {
      getUserPermission("testUser") >> Mock(Call){
        execute() >> Response.success(new UserPermission(
                applications: [
                        new Application(name: "abc-def",
                                permissions: Permissions.factory([
                                        (Authorization.READ): ["testRole"] as Set
                                ])),
                ],
                roles: [new Role("testRole")]
        ).getView() )
      }
    }
    evaluator = updateEvaluator(fiatService)
    result = evaluator.hasPermission(authentication,
                                     resource,
                                     resourceType.name,
                                     authorization)

    then:
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
    SecurityContextHolder.getContext().setAuthentication(authentication)
    Resource resourceCanCreate = new Application().setName("app1")
    Resource resourceCannotCreate = new Application().setName("app2")
    SpinnakerHttpException spinnakerHttpException = makeSpinnakerHttpException(404)

    FiatService fiatService = Mock(FiatService) {
      getUserPermission("testUser") >> Mock(Call) {
        execute() >> Response.success(upv)
      }
      canCreate("testUser", "APPLICATION", resourceCanCreate) >> Mock(Call) {
        execute() >> Response.success(null) // doesn't return anything in case of success
      }
      canCreate("testUser", "APPLICATION", resourceCannotCreate) >> {
        throw spinnakerHttpException
      }
    }
    evaluator = updateEvaluator(fiatService)

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
    FiatService fiatService = Mock(FiatService) {
      getUserPermission("testUser") >>  {
        throw new IllegalStateException("something something something")
      }
    }
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
    view == null
  }

  @Unroll
  def "should support legacy fallback when fiat is unavailable"() {
    given:
    MDC.put(Header.USER.header, "fallback")
    MDC.put(Header.ACCOUNTS.header, "account1,account2")

    when:
    FiatService fiatService = Mock(FiatService) {
      getUserPermission("testUser") >>  {
        throw new IllegalStateException("something something something")
      }
    }

    FiatPermissionEvaluator evaluator = updateEvaluator(fiatService)
    def permission = evaluator.getPermission("testUser")
    def hasPermission = evaluator.hasPermission(authentication, "my_application", "APPLICATION", "READ")

    then:
    2 * fiatStatus.isLegacyFallbackEnabled() >> { return legacyFallbackEnabled }

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
    FiatService fiatService = Mock(FiatService) {
      getUserPermission("testUser") >> Mock(Call){
        execute() >> Response.success(new UserPermission.View()
                .setApplications(
                        [
                                new Application.View()
                                        .setName("my_application")
                                        .setAuthorizations(authorizations as Set<Authorization>)
                        ] as Set<Application.View>
                ) )
      }
    }

    evaluator = updateEvaluator(fiatService)
    def hasReadPermission = evaluator.hasPermission(authentication, "my_application", "APPLICATION", "READ")
    def hasWritePermission = evaluator.hasPermission(authentication, "my_application", "APPLICATION", "WRITE")

    then:
    !hasReadPermission
    !hasWritePermission

    where:
    authorizations << [null, [] as Set]
  }

  @Unroll
  def "should allow access to unknown applications"() {
    when:
    FiatService fiatService = Mock(FiatService) {
      getUserPermission("testUser") >> Mock(Call){
        execute() >> Response.success(new UserPermission.View()
                .setAllowAccessToUnknownApplications(allowAccessToUnknownApplications)
                .setApplications(Collections.emptySet()))
      }
    }

    evaluator = updateEvaluator(fiatService)
    def hasPermission = evaluator.hasPermission(authentication, "my_application", "APPLICATION", "READ")

    then:
    hasPermission == expectedToHavePermission

    where:
    allowAccessToUnknownApplications || expectedToHavePermission
    false                            || false
    true                             || true
  }

  @Unroll
  def "should allow an admin to access #resourceType"() {
    given:
    FiatService fiatService = Mock(FiatService) {
      getUserPermission("testUser") >> Mock(Call){
        execute() >> Response.success(new UserPermission.View()
                .setApplications(Collections.emptySet())
                .setAdmin(true))
      }
    }

    evaluator = updateEvaluator(fiatService)

    expect:
    evaluator.hasPermission(authentication, "my_resource", resourceType, "READ")
    evaluator.hasPermission(authentication, "my_resource", resourceType, "WRITE")

    where:
    resourceType << [ResourceType.ACCOUNT, ResourceType.APPLICATION, ResourceType.BUILD_SERVICE, ResourceType.ROLE, ResourceType.SERVICE_ACCOUNT]*.toString()
  }

  def "should support isAdmin check for a user"() {
    given:
    FiatService fiatService = Mock(FiatService) {
      getUserPermission("testUser") >> Mock(Call){
        execute() >> Response.success(new UserPermission.View()
                .setApplications(Collections.emptySet())
                .setAdmin(isAdmin))
      }
    }

    evaluator = updateEvaluator(fiatService)

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
    FiatService fiatService = Mock(FiatService) {
      getUserPermission("testUser") >> Mock(Call){
        execute() >> Response.success(new UserPermission.View()
                .setExtensionResources([
                        (MY_EXTENSION_RESOURCE): [extensionResource] as Set
                ]))
      }
    }

    evaluator = updateEvaluator(fiatService)
    def hasPermission = evaluator.hasPermission(authentication, "extension_resource", resourceType, authorization)

    then:
    hasPermission == expectedHasPermission

    where:
    resourceType              | authorization | expectedHasPermission
    "MY_EXTENSION_RESOURCE"   | "READ"        | true
    "MY_EXTENSION_RESOURCE"   | "WRITE"       | false
    "YOUR_EXTENSION_RESOURCE" | "READ"        | false
    "YOUR_EXTENSION_RESOURCE" | "WRITE"       | false
  }

  def "should evaluate permissions for AccessControlled objects"() {
    given:
    def resource = new AccessControlledResource(new Permissions.Builder().add(Authorization.READ, 'test group').build())

    when:
    def hasPermission = evaluator.hasPermission(authentication, resource, authorization)

    then:
    hasPermission == expectedHasPermission

    where:
    authorization      | expectedHasPermission
    'read'             | true
    "read"             | true
    'READ'             | true
    "READ"             | true
    Authorization.READ | true
    'write'            | false
    'WRITE'            | false
    'EXECUTE'          | false
  }

  private static FiatClientConfigurationProperties buildConfigurationProperties() {
    FiatClientConfigurationProperties configurationProperties = new FiatClientConfigurationProperties()
    configurationProperties.enabled = true
    configurationProperties.cache.maxEntries = 0
    configurationProperties.cache.expiresAfterWriteSeconds = 0
    configurationProperties.grantedAuthorities.enabled = true
    return configurationProperties
  }

  private static MY_EXTENSION_RESOURCE = new ResourceType("MY_EXTENSION_RESOURCE");
  private static class MyExtensionResourceView implements Authorizable {
    String name
    Set<Authorization> authorizations
  }

  private FiatPermissionEvaluator updateEvaluator(FiatService updatedFiatService) {
    new FiatPermissionEvaluator(
            registry,
            updatedFiatService,
            buildConfigurationProperties(),
            fiatStatus,
            FiatPermissionEvaluator.RetryHandler.NOOP
    )
  }
  private static SpinnakerHttpException makeSpinnakerHttpException(int status) {
    String url = "https://some-url";
    Response retrofit2Response =
            Response.error(
                    status,
                    ResponseBody.create(
                            MediaType.parse("application/json"),
                            "{ \"message\": \"arbitrary message\" }"));

    Retrofit retrofit =
            new Retrofit.Builder()
                    .baseUrl(url)
                    .addConverterFactory(JacksonConverterFactory.create())
                    .build();

    return new SpinnakerHttpException(retrofit2Response, retrofit);
  }

}
