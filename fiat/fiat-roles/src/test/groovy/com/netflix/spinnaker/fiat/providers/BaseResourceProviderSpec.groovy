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

import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.fiat.model.resources.Resource
import com.netflix.spinnaker.fiat.model.resources.ResourceType
import com.netflix.spinnaker.fiat.model.resources.Role
import groovy.transform.EqualsAndHashCode
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class BaseResourceProviderSpec extends Specification {

  private static Authorization R = Authorization.READ
  private static Authorization W = Authorization.WRITE
  private static Authorization E = Authorization.EXECUTE

  TestResource noReqGroups
  TestResource reqGroup1
  TestResource reqGroup1and2

  def setup() {
    noReqGroups = new TestResource()
        .setName("noReqGroups")
    reqGroup1 = new TestResource()
        .setName("reqGroup1")
        .setPermissions(new Permissions.Builder().add(R, "group1").build())
    reqGroup1and2 = new TestResource()
        .setName("reqGroup1and2")
        .setPermissions(new Permissions.Builder().add(R, "group1")
                                                 .add(W, "group2")
                                                 .build())
  }

  def "should get all unrestricted"() {
    setup:
    @Subject provider = new TestResourceResourceProvider()

    when:
    provider.resources = [noReqGroups]
    def result = provider.getAllUnrestricted()

    then:
    result.size() == 1
    def expected = noReqGroups
    result.first() == expected

    when:
    provider.resources = [reqGroup1]
    provider.clearCache()
    result = provider.getAllUnrestricted()

    then:
    result.isEmpty()
  }

  def "should get restricted"() {
    setup:
    @Subject provider = new TestResourceResourceProvider()

    when:
    provider.resources = [noReqGroups]
    def result = provider.getAllRestricted("userId", [new Role("group1")] as Set, false)

    then:
    result.isEmpty()

    when:
    provider.resources = [reqGroup1]
    provider.clearCache()
    result = provider.getAllRestricted("userId", [new Role("group1")] as Set, false)

    then:
    result.size() == 1
    result.first() == reqGroup1

    when:
    provider.resources = [reqGroup1and2]
    provider.clearCache()
    result = provider.getAllRestricted("userId", [new Role("group1")] as Set, false)

    then:
    result.size() == 1
    result.first() == reqGroup1and2

    when: "use additional groups that grants additional authorizations."
    result = provider.getAllRestricted("userId", [new Role("group1"), new Role("group2")] as Set, false)

    then:
    result.size() == 1
    result.first() == reqGroup1and2

    when:
    provider.getAllRestricted(null, null, false)

    then:
    thrown IllegalArgumentException
  }

  @Unroll
  def "restricted lookup for roles #roles (admin: #isAdmin) matches filtering every resource"() {
    setup:
    def plain = new PlainResource(name: "plain")
    Set<Resource> resources = [
        plain,
        new TestResource().setName("unrestricted"),
        restricted("readOnly", [(R): ["group1"]]),
        restricted("writeOnly", [(W): ["group2"]]),
        restricted("executeOnly", [(E): ["group3"]]),
        restricted("readAndWrite", [(R): ["group1"], (W): ["group2"]]),
        restricted("sharedGroup", [(R): ["group1", "group2"], (W): ["group2"]]),
        restricted("mixedCase", [(R): ["  MiXeD  "]]),
        restricted("unrelated", [(R): ["other"], (W): ["another"]]),
    ] as Set
    def provider = new TestProvider<Resource>(resources: resources)
    Set<Role> userRoles = roles.collect { new Role(it) } as Set

    when:
    def result = provider.getAllRestricted("userId", userRoles, isAdmin)

    then:
    result == bruteForceRestricted(resources, userRoles, isAdmin)
    result*.name as Set == expectedNames as Set
    !result.contains(plain)

    where:
    roles                          | isAdmin || expectedNames
    []                             | false   || []
    []                             | true    || ["readOnly", "writeOnly", "executeOnly", "readAndWrite", "sharedGroup", "mixedCase", "unrelated"]
    ["group1"]                     | true    || ["readOnly", "writeOnly", "executeOnly", "readAndWrite", "sharedGroup", "mixedCase", "unrelated"]
    ["group1"]                     | false   || ["readOnly", "readAndWrite", "sharedGroup"]
    ["group2"]                     | false   || ["writeOnly", "readAndWrite", "sharedGroup"]
    ["group3"]                     | false   || ["executeOnly"]
    ["GROUP1"]                     | false   || ["readOnly", "readAndWrite", "sharedGroup"]
    ["mixed"]                      | false   || ["mixedCase"]
    ["MIXED"]                      | false   || ["mixedCase"]
    ["group1", "group2"]           | false   || ["readOnly", "writeOnly", "readAndWrite", "sharedGroup"]
    ["group1", "group2", "group3"] | false   || ["readOnly", "writeOnly", "executeOnly", "readAndWrite", "sharedGroup"]
    ["nobody"]                     | false   || []
  }

  def "restricted lookup matches filtering every resource across many resources and roles"() {
    setup:
    def random = new Random(42)
    def groups = (0..<30).collect { "group$it".toString() }
    Set<Resource> resources = (0..<500).collect { i ->
      def builder = new Permissions.Builder()
      Authorization.values().each { auth ->
        random.nextInt(4).times { builder.add(auth, groups[random.nextInt(groups.size())]) }
      }
      new TestResource().setName("resource$i").setPermissions(builder.build())
    } as Set
    def provider = new TestProvider<Resource>(resources: resources)

    expect:
    (0..<200).every {
      Set<Role> userRoles = groups.findAll { random.nextInt(8) == 0 }.collect { new Role(it) } as Set
      boolean isAdmin = random.nextInt(10) == 0
      provider.getAllRestricted("userId", userRoles, isAdmin) ==
          bruteForceRestricted(resources, userRoles, isAdmin)
    }
  }

  def "reloading or clearing the cache rebuilds the group index"() {
    setup:
    def first = restricted("first", [(R): ["group1"]])
    def second = restricted("second", [(R): ["group1"]])
    def third = restricted("third", [(W): ["group2"]])
    def provider = new TestProvider<Resource>(resources: [first] as Set)
    Set<Role> group1 = [new Role("group1")] as Set
    Set<Role> group2 = [new Role("group2")] as Set

    expect:
    provider.getAllRestricted("userId", group1, false) == [first] as Set

    when: "the underlying resources change but the cache has not been refreshed"
    provider.resources = [second, third] as Set

    then:
    provider.getAllRestricted("userId", group1, false) == [first] as Set
    provider.getAllRestricted("userId", group2, false).isEmpty()

    when:
    provider.reloadCache()

    then:
    provider.getAll() == [second, third] as Set
    provider.getAllRestricted("userId", group1, false) == [second] as Set
    provider.getAllRestricted("userId", group2, false) == [third] as Set
    provider.getAllRestricted("userId", [] as Set, true) == [second, third] as Set

    when:
    provider.resources = [first, third] as Set
    provider.clearCache()

    then:
    provider.getAllRestricted("userId", group1, false) == [first] as Set
    provider.getAllRestricted("userId", group2, false) == [third] as Set
    provider.getAll() == [first, third] as Set
  }

  def "provider exceptions from loadAll are propagated"() {
    setup:
    def provider = new TestProvider<Resource>() {
      @Override
      protected Set<Resource> loadAll() throws ProviderException {
        throw new ProviderException(BaseResourceProvider, new RuntimeException("boom"))
      }
    }

    when:
    provider.getAllRestricted("userId", [new Role("group1")] as Set, false)

    then:
    thrown ProviderException
  }

  private TestResource restricted(String name, Map<Authorization, List<String>> permissions) {
    def builder = new Permissions.Builder()
    permissions.each { auth, groups -> groups.each { builder.add(auth, it) } }
    return new TestResource().setName(name).setPermissions(builder.build())
  }

  /** The filter BaseResourceProvider.getAllRestricted applied before the group index existed. */
  private static Set<Resource> bruteForceRestricted(Set<Resource> resources, Set<Role> userRoles, boolean isAdmin) {
    return resources
        .findAll { it instanceof Resource.AccessControlled }
        .findAll { ((Resource.AccessControlled) it).permissions.isRestricted() }
        .findAll { !((Resource.AccessControlled) it).permissions.getAuthorizations(userRoles).isEmpty() || isAdmin }
        as Set
  }

  class TestResourceResourceProvider extends BaseResourceProvider<TestResource> {
    Set<TestResource> resources = new HashSet<>()

    @Override
    protected Set<TestResource> loadAll() throws ProviderException {
      return resources
    }
  }

  static class TestProvider<T extends Resource> extends BaseResourceProvider<T> {
    Set<T> resources = new HashSet<>()

    @Override
    protected Set<T> loadAll() throws ProviderException {
      return resources
    }
  }

  @Builder(builderStrategy = SimpleStrategy, prefix = "set")
  @EqualsAndHashCode
  class TestResource implements Resource.AccessControlled {
    final ResourceType resourceType = ResourceType.APPLICATION // Irrelevant for testing.
    String name
    Permissions permissions = Permissions.EMPTY
  }

  @EqualsAndHashCode
  static class PlainResource implements Resource {
    final ResourceType resourceType = ResourceType.ROLE // Irrelevant for testing.
    String name
  }
}
