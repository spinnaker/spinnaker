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

package com.netflix.spinnaker.fiat.providers

import com.netflix.spinnaker.fiat.config.FiatRoleConfig
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount
import com.netflix.spinnaker.fiat.providers.internal.Front50Service
import org.apache.commons.collections4.CollectionUtils
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.function.Predicate

class DefaultServiceAccountProviderSpec extends Specification {

  @Shared
  ServiceAccount aAcct = new ServiceAccount(name: "a", memberOf: ["a"])

  @Shared
  ServiceAccount bAcct = new ServiceAccount(name: "b", memberOf: ["a", "b"])

  @Shared
  ServiceAccount cAcct = new ServiceAccount(name: "c", memberOf: [])

  @Shared
  Front50Service front50Service = Mock(Front50Service) {
    getAllServiceAccounts() >> [aAcct, bAcct, cAcct]
  }

  @Unroll
  def "should return all accounts the specified groups has access to"() {
    given:
    FiatRoleConfig fiatRoleConfig = Mock(FiatRoleConfig) {
      isOrMode() >> false
    }
    DefaultServiceAccountResourceProvider provider = new DefaultServiceAccountResourceProvider(
            front50Service, [new DefaultServiceAccountPredicateProvider(fiatRoleConfig)]
    )

    when:
    def result = provider.getAllRestricted("userId", input.collect { new Role(it) } as Set, isAdmin)

    then:
    CollectionUtils.disjunction(result, expected).isEmpty()

    when:
    provider.getAllRestricted(null, null, false)

    then:
    thrown IllegalArgumentException

    where:
    input           | isAdmin || expected
    []              | false   || []
    ["a"]           | false   || [aAcct]
    ["b"]           | false   || []
    ["c"]           | false   || []
    ["a", "b"]      | false   || [aAcct, bAcct]
    ["a", "b", "c"] | false   || [aAcct, bAcct]
    []              | true    || [aAcct, bAcct]
    []              | true    || [aAcct, bAcct]
  }

  @Unroll
  def "should return all accounts the specified groups has access to in or mode"() {
    given:
    FiatRoleConfig fiatRoleConfig = Mock(FiatRoleConfig) {
      isOrMode() >> true
    }
    DefaultServiceAccountResourceProvider provider = new DefaultServiceAccountResourceProvider(
            front50Service, [new DefaultServiceAccountPredicateProvider(fiatRoleConfig)]
    )

    when:
    def result = provider.getAllRestricted("userId", input.collect { new Role(it) } as Set, isAdmin)

    then:
    CollectionUtils.disjunction(result, expected).isEmpty()

    when:
    provider.getAllRestricted(null, null, false)

    then:
    thrown IllegalArgumentException

    where:
    input           | isAdmin || expected
    []              | false   || []
    ["a"]           | false   || [aAcct, bAcct]
    ["b"]           | false   || [bAcct]
    ["c"]           | false   || []
    ["a", "b"]      | false   || [aAcct, bAcct]
    ["a", "b", "c"] | false   || [aAcct, bAcct]
    []              | true    || [aAcct, bAcct]
    []              | true    || [aAcct, bAcct]
  }

  @Unroll
  def "role names and memberOf are compared case-insensitively (or mode: #orMode)"() {
    given:
    def upperAcct = new ServiceAccount(name: "upper", memberOf: [" TeamA ", "TEAMB"])
    def emptyAcct = new ServiceAccount(name: "empty", memberOf: [])
    Front50Service front50 = Mock(Front50Service) {
      getAllServiceAccounts() >> [upperAcct, emptyAcct]
    }
    FiatRoleConfig fiatRoleConfig = Mock(FiatRoleConfig) {
      isOrMode() >> orMode
    }
    def provider = new DefaultServiceAccountResourceProvider(
        front50, [new DefaultServiceAccountPredicateProvider(fiatRoleConfig)])

    when:
    def result = provider.getAllRestricted("userId", input.collect { new Role(it) } as Set, isAdmin)

    then:
    CollectionUtils.disjunction(result, expected.collect { it == "upper" ? upperAcct : emptyAcct }).isEmpty()

    where:
    orMode | input              | isAdmin || expected
    false  | ["teama"]          | false   || []
    false  | ["TEAMA", "teamB"] | false   || ["upper"]
    false  | []                 | true    || ["upper"]
    true   | ["teama"]          | false   || ["upper"]
    true   | ["TeamB"]          | false   || ["upper"]
    true   | ["teamc"]          | false   || []
    true   | []                 | true    || ["upper"]
  }

  def "predicate providers are asked once per lookup with a set of role names"() {
    given:
    def predicateProvider = Mock(ServiceAccountPredicateProvider)
    def provider = new DefaultServiceAccountResourceProvider(front50Service, [predicateProvider])

    when:
    def result = provider.getAllRestricted("userId", [new Role("A")] as Set, false)

    then:
    1 * predicateProvider.get("userId", { it instanceof Set && it == ["a"] as Set }, false) >>
        ({ ServiceAccount svcAcct -> svcAcct.memberOf.contains("b") } as Predicate)
    0 * predicateProvider.get(_, { it instanceof List }, _)
    result == [bAcct] as Set
  }

  def "predicate providers that only implement the list variant keep working"() {
    given:
    List<Object> receivedRoles = []
    def listOnly = new ServiceAccountPredicateProvider() {
      @Override
      Predicate<ServiceAccount> get(String userId, List<String> userRoles, boolean isAdmin) {
        receivedRoles << userRoles
        return { ServiceAccount svcAcct -> userRoles.containsAll(svcAcct.memberOf) } as Predicate
      }
    }
    def provider = new DefaultServiceAccountResourceProvider(front50Service, [listOnly])

    when:
    def result = provider.getAllRestricted("userId", [new Role("a")] as Set, false)

    then:
    result == [aAcct] as Set
    receivedRoles == [["a"]]
    receivedRoles.every { it instanceof List }
  }

  def "access is granted if any predicate provider grants it"() {
    given:
    def grantsA = { userId, userRoles, isAdmin ->
      return { ServiceAccount svcAcct -> svcAcct.name == "a" } as Predicate
    } as ServiceAccountPredicateProvider
    def grantsB = { userId, userRoles, isAdmin ->
      return { ServiceAccount svcAcct -> svcAcct.name == "b" } as Predicate
    } as ServiceAccountPredicateProvider
    def grantsC = { userId, userRoles, isAdmin ->
      return { ServiceAccount svcAcct -> svcAcct.name == "c" } as Predicate
    } as ServiceAccountPredicateProvider
    def provider = new DefaultServiceAccountResourceProvider(front50Service, [grantsA, grantsB, grantsC])

    expect: "service accounts without memberOf are never returned"
    provider.getAllRestricted("userId", [] as Set, false) == [aAcct, bAcct] as Set
  }

  @Unroll
  def "later predicate providers are not asked when no account needs them (#scenario)"() {
    given:
    FiatRoleConfig fiatRoleConfig = Mock(FiatRoleConfig) {
      isOrMode() >> false
    }
    Front50Service front50 = Mock(Front50Service) {
      getAllServiceAccounts() >> accounts
    }
    def throwing = Mock(ServiceAccountPredicateProvider)
    def provider = new DefaultServiceAccountResourceProvider(
        front50, [new DefaultServiceAccountPredicateProvider(fiatRoleConfig), throwing])

    when:
    def result = provider.getAllRestricted("userId", [] as Set, isAdmin)

    then:
    0 * throwing.get(*_)
    result == expected as Set

    where:
    scenario                            | accounts         | isAdmin || expected
    "admin granted by first provider"   | [aAcct, bAcct]   | true    || [aAcct, bAcct]
    "no accounts with memberOf"         | [cAcct]          | false   || []
    "no accounts at all"                | []               | false   || []
  }

  def "later predicate providers are built once and only when an earlier one rejects"() {
    given:
    def rejectsAll = { userId, userRoles, isAdmin ->
      return { ServiceAccount svcAcct -> false } as Predicate
    } as ServiceAccountPredicateProvider
    def grantsB = Mock(ServiceAccountPredicateProvider)
    def provider = new DefaultServiceAccountResourceProvider(front50Service, [rejectsAll, grantsB])

    when:
    def result = provider.getAllRestricted("userId", [] as Set, false)

    then:
    1 * grantsB.get("userId", _ as Set, false) >>
        ({ ServiceAccount svcAcct -> svcAcct.name == "b" } as Predicate)
    result == [bAcct] as Set
  }

  @Unroll
  def "list and set variants of the default predicate agree (or mode: #orMode, admin: #isAdmin)"() {
    given:
    FiatRoleConfig fiatRoleConfig = Mock(FiatRoleConfig) {
      isOrMode() >> orMode
    }
    def predicateProvider = new DefaultServiceAccountPredicateProvider(fiatRoleConfig)
    def accounts = [aAcct, bAcct, cAcct]

    expect:
    [[], ["a"], ["b"], ["a", "b"], ["c"]].every { roles ->
      def fromList = predicateProvider.get("userId", roles as List<String>, isAdmin)
      def fromSet = predicateProvider.get("userId", roles as Set<String>, isAdmin)
      accounts.every { fromList.test(it) == fromSet.test(it) }
    }

    where:
    orMode | isAdmin
    false  | false
    false  | true
    true   | false
    true   | true
  }
}
