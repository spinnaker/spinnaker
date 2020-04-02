/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Application
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.gate.services.internal.ExtendedFiatService
import com.netflix.spinnaker.kork.exceptions.SpinnakerException
import com.netflix.spinnaker.security.User
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.converter.ConversionException
import spock.lang.Specification
import spock.lang.Unroll

class PermissionServiceSpec extends Specification {

  def "lookupServiceAccount returns empty on 404"() {
    given:
    def extendedFiatService = Stub(ExtendedFiatService) {
      getUserServiceAccounts(user) >> { throw theFailure }
    }
    def subject = new PermissionService(extendedFiatService: extendedFiatService)

    when:
    def result = subject.lookupServiceAccounts(user)

    then:
    noExceptionThrown()
    result == []

    where:
    user = 'foo@bar.com'

    theFailure << [httpRetrofitError(404), conversionError(404)]

  }

  @Unroll
  def "lookupServiceAccount failures are marked retryable"() {
    given:
    def extendedFiatService = Stub(ExtendedFiatService) {
      getUserServiceAccounts(user) >> { throw theFailure }
    }
    def subject = new PermissionService(extendedFiatService: extendedFiatService)

    when:
    subject.lookupServiceAccounts(user)

    then:
    def exception = thrown(SpinnakerException)
    exception.retryable == expectedRetryable

    where:
    user = 'foo@bar.com'

    theFailure             || expectedRetryable
    httpRetrofitError(400) || false
    conversionError(400)   || false
    conversionError(200)   || false
    networkError()         || true
    httpRetrofitError(500) || true
    conversionError(500)   || true
    unexpectedError()      || true
  }

  private RetrofitError conversionError(int code) {
    RetrofitError.conversionError(
      'http://foo',
      new Response('http://foo', code, 'you are bad', [], null),
      null,
      null,
      new ConversionException('boom'))
  }

  private RetrofitError networkError() {
    RetrofitError.networkError('http://foo', new IOException())
  }

  private RetrofitError unexpectedError() {
    RetrofitError.unexpectedError('http://foo', new Throwable())
  }

  private RetrofitError httpRetrofitError(int code) {
    RetrofitError.httpError(
      'http://foo',
      new Response('http://foo', code, 'you are bad', [], null),
      null,
      null)
  }


  @Unroll
  def "getServiceAccountsForApplication when #desc looks up all serviceAccounts (#expectLookup) and falls back to getServiceAccounts (#expectFallback)"() {
    given:
    def cfgProps = new ServiceAccountFilterConfigProps(enabled, matchAuths ? auths : [])
    def user = username == null ? null : Stub(User) {
      getUsername() >> username
    }
    def fiatStatus = Stub(FiatStatus) {
      isEnabled() >> fiatEnabled
    }
    def permissionEvaluator = Mock(FiatPermissionEvaluator)
    def extendedFiatService = Mock(ExtendedFiatService)
    def result = hasResult ? lookupResult : []

    def subject = new PermissionService(
      fiatStatus: fiatStatus,
      permissionEvaluator: permissionEvaluator,
      extendedFiatService: extendedFiatService,
      serviceAccountFilterConfigProps: cfgProps)

    when:
    subject.getServiceAccountsForApplication(user, application)

    then:
    (expectLookup ? 1 : 0) * extendedFiatService.getUserServiceAccounts(username) >> result
    (expectFallback ? 1 : 0) * permissionEvaluator.getPermission(username) >> userPermissionView
    0 * _

    where:
    enabled | matchAuths | username      | application | fiatEnabled | hasResult || expectLookup || expectFallback || desc
    true    | true       | 'foo@bar.com' | 'myapp'     | true        | true      || true         || false          || "successfully performs filtered lookup"
    false   | true       | 'foo@bar.com' | 'myapp'     | true        | true      || false        || true           || "filtering disabled"
    true    | false      | 'foo@bar.com' | 'myapp'     | true        | true      || false        || true           || "no authorizations to filter on"
    true    | true       | null          | 'myapp'     | true        | true      || false        || false          || "no username supplied"
    true    | true       | 'foo@bar.com' | null        | true        | true      || false        || true           || "no application supplied"
    true    | true       | 'foo@bar.com' | 'myapp'     | false       | true      || false        || false          || "fiat disabled"
    true    | true       | 'foo@bar.com' | 'myapp'     | true        | false     || true         || true           || "no service accounts match"

    auths = [Authorization.WRITE]
    lookupResult = [sa("foo-service-account", application, auths)]
    userPermission = new UserPermission(id: 'foo@bar.com')
    userPermissionView = userPermission.getView()
  }

  private UserPermission.View sa(String name, String application, Collection<Authorization> auths) {
    UserPermission userPermission = new UserPermission();
    userPermission.setId(name)
    userPermission.setRoles([new Role(name: name, source: Role.Source.EXTERNAL)] as Set<Role>)

    Application app = new Application()
    app.setName(application)
    Permissions.Builder pb = new Permissions.Builder()
    for (auth in auths) {
      pb.add(auth, name)
    }
    app.setPermissions(pb.build())

    userPermission.setApplications([app] as Set<Application>)
    return userPermission.getView()
  }
}
