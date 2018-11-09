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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.model.EntityTags
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.security.User
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification
import spock.lang.Unroll

class AuthorizationSupportSpec extends Specification {

  FiatPermissionEvaluator permissionEvaluator
  AccountCredentialsProvider accountCredentialsProvider = Mock(AccountCredentialsProvider)

  def setup() {
    def ctx = SecurityContextHolder.createEmptyContext()
    ctx.setAuthentication(new TestingAuthenticationToken(new User(email: "testUser"), null))
    SecurityContextHolder.setContext(ctx)

    permissionEvaluator = Mock(FiatPermissionEvaluator)
  }

  def cleanup() {
    SecurityContextHolder.clearContext()
  }

  def "filter LoadBalancerProvider.Item"() {
    given:
    AuthorizationSupport support = new AuthorizationSupport(permissionEvaluator: permissionEvaluator)

    when:
    def result = support.filterLoadBalancerProviderItem(newTestItem())

    then:
    1 * permissionEvaluator.hasPermission(_, "test", 'APPLICATION', 'READ') >> true
    1 * permissionEvaluator.hasPermission(_, "account1", 'ACCOUNT', 'READ') >> true
    1 * permissionEvaluator.hasPermission(_, "account2", 'ACCOUNT', 'READ') >> true
    result == true

    when:
    result = support.filterLoadBalancerProviderItem(newTestItem())

    then:
    1 * permissionEvaluator.hasPermission(_, "test", 'APPLICATION', 'READ') >> false
    result == false

    when:
    def item = newTestItem()
    result = support.filterLoadBalancerProviderItem(item)

    then:
    1 * permissionEvaluator.hasPermission(_, "test", 'APPLICATION', 'READ') >> true
    1 * permissionEvaluator.hasPermission(_, "account1", 'ACCOUNT', 'READ') >> true
    1 * permissionEvaluator.hasPermission(_, "account2", 'ACCOUNT', 'READ') >> false
    result == true
    item.byAccounts.size() == 1

    when:
    result = support.filterLoadBalancerProviderItem(newTestItem())

    then:
    1 * permissionEvaluator.hasPermission(_, "test", 'APPLICATION', 'READ') >> true
    1 * permissionEvaluator.hasPermission(_, "account1", 'ACCOUNT', 'READ') >> false
    1 * permissionEvaluator.hasPermission(_, "account2", 'ACCOUNT', 'READ') >> false
    result == false
  }

  def "filter LoadBalancerProvider.Items"() {
    given:
    AuthorizationSupport support = new AuthorizationSupport(permissionEvaluator: permissionEvaluator)

    when:
    def result = support.filterLoadBalancerProviderItems(newTestItems())

    then:
    1 * permissionEvaluator.hasPermission(_, "test1", 'APPLICATION', 'READ') >> true
    1 * permissionEvaluator.hasPermission(_, "account1", 'ACCOUNT', 'READ') >> true
    1 * permissionEvaluator.hasPermission(_, "account2", 'ACCOUNT', 'READ') >> true
    1 * permissionEvaluator.hasPermission(_, "test2", 'APPLICATION', 'READ') >> true
    1 * permissionEvaluator.hasPermission(_, "account1", 'ACCOUNT', 'READ') >> true
    1 * permissionEvaluator.hasPermission(_, "account2", 'ACCOUNT', 'READ') >> true
    result == true

    when:
    def items = newTestItems()
    result = support.filterLoadBalancerProviderItems(items)

    then:
    1 * permissionEvaluator.hasPermission(_, "test1", 'APPLICATION', 'READ') >> true
    1 * permissionEvaluator.hasPermission(_, "account1", 'ACCOUNT', 'READ') >> true
    1 * permissionEvaluator.hasPermission(_, "account2", 'ACCOUNT', 'READ') >> true
    1 * permissionEvaluator.hasPermission(_, "test2", 'APPLICATION', 'READ') >> false
    result == true
    items.size() == 1

    when:
    items = newTestItems()
    result = support.filterLoadBalancerProviderItems(items)

    then:
    1 * permissionEvaluator.hasPermission(_, "test1", 'APPLICATION', 'READ') >> false
    1 * permissionEvaluator.hasPermission(_, "test2", 'APPLICATION', 'READ') >> false
    result == true
    items.isEmpty()
  }

  def "filter list items"() {
    setup:
    AuthorizationSupport support = new AuthorizationSupport(permissionEvaluator: permissionEvaluator)
    def list = [
        [account: "account1"],
        [accountName: "account2"],
        [noAccount: 123]
    ]

    when:
    def result = support.filterForAccounts(list)

    then:
    1 * permissionEvaluator.hasPermission(_, "account1", 'ACCOUNT', 'READ') >> true
    1 * permissionEvaluator.hasPermission(_, "account2", 'ACCOUNT', 'READ') >> true
    result == true
    list.size() == 3

    when:
    result = support.filterForAccounts(list)

    then:
    1 * permissionEvaluator.hasPermission(_, "account1", 'ACCOUNT', 'READ') >> false
    1 * permissionEvaluator.hasPermission(_, "account2", 'ACCOUNT', 'READ') >> false
    result == true
    list.size() == 1

    when:
    list = [
        new ClassWithAccount(account: "account1"),
        new ClassWithAccount(account: "account2"),
        new ClassWithoutAccount(name: "batman"),
    ]
    result = support.filterForAccounts(list)

    then:
    1 * permissionEvaluator.hasPermission(_, "account1", 'ACCOUNT', 'READ') >> true
    1 * permissionEvaluator.hasPermission(_, "account2", 'ACCOUNT', 'READ') >> true
    result == true
    list.size() == 3

    when:
    result = support.filterForAccounts(list)

    then:
    1 * permissionEvaluator.hasPermission(_, "account1", 'ACCOUNT', 'READ') >> false
    1 * permissionEvaluator.hasPermission(_, "account2", 'ACCOUNT', 'READ') >> false
    result == true
    list.size() == 1
  }

  @Unroll
  def "should verify access to entity tags account/application"() {
    given:
    def support = new AuthorizationSupport(
      permissionEvaluator: permissionEvaluator,
      accountCredentialsProvider: accountCredentialsProvider
    )

    and:
    1 * accountCredentialsProvider.getAll() >> {
      return [
        accountCredentials("account1", "1"),
        accountCredentials("account2", "2")
      ]
    }

    _ * permissionEvaluator.hasPermission(_, "account1", "ACCOUNT", "READ") >> true
    _ * permissionEvaluator.hasPermission(_, "account2", "ACCOUNT", "READ") >> false
    _ * permissionEvaluator.hasPermission(_, "clouddriver", "APPLICATION", "READ") >> true
    _ * permissionEvaluator.hasPermission(_, "gate", "APPLICATION", "READ") >> false

    expect:
    support.authorizeEntityTags([entityTags("id-1", accountId, application)]) == isAuthorized

    where:
    accountId | application   || isAuthorized
    "1"       | "clouddriver" || true
    "2"       | "clouddriver" || false
    null      | "clouddriver" || true
    null      | "gate"        || false
    null      | null          || true

  }

  EntityTags entityTags(String id, String accountId, String application) {
    return new EntityTags(
      id: id,
      entityRef: new EntityTags.EntityRef(
        accountId: accountId,
        application: application
      )
    )
  }

  AccountCredentials accountCredentials(String name, String accountId) {
    return Mock(AccountCredentials) {
      _ * getName() >> { return name }
      _ * getAccountId() >> { return accountId }
    }
  }

  static List<TestItem> newTestItems() {
    return [newTestItem("test1-item"), newTestItem("test2-item")]
  }

  static TestItem newTestItem(String name = "test-item") {
    def acct1 = new TestByAccount(name: "account1")
    def acct2 = new TestByAccount(name: "account2")
    return new TestItem(name: name, byAccounts: [acct1, acct2])
  }

  static class TestItem implements LoadBalancerProvider.Item {
    String name
    List<TestByAccount> byAccounts = []
  }

  static class TestByAccount implements LoadBalancerProvider.ByAccount {
    String name
    List<TestByRegion> byRegions = []
  }

  static class TestByRegion implements LoadBalancerProvider.ByRegion {
    String name
    List<TestDetails> loadBalancers = []
  }

  static class TestDetails implements LoadBalancerProvider.Details {

  }

  static class ClassWithAccount {
    String account
  }

  static class ClassWithoutAccount {
    String name
  }
}
