/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Capacity
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location.Type
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class TrafficGuardSpec extends Specification {

  OortHelper oortHelper = Mock(OortHelper)
  Front50Service front50Service = Mock(Front50Service)
  Registry registry = new NoopRegistry()
  DynamicConfigService dynamicConfigService = Mock(DynamicConfigService)

  Application application = Stub(Application) {
    details() >> applicationDetails
  }

  @Shared Location location = new Location(Type.REGION, "us-east-1")
  @Shared Moniker moniker = new Moniker(app: "app", stack: "foo", cluster: "app-foo")
  @Shared String targetName = "app-foo-v001"
  @Shared String otherName = "app-foo-v000"
  @Shared Map<String, Object> applicationDetails = [:]

  @Subject
  TrafficGuard trafficGuard = new TrafficGuard(oortHelper, new Optional<>(front50Service), registry, dynamicConfigService)

  void setup() {
    applicationDetails.clear()
  }

  def makeServerGroup(String name, int up, int down = 0, Map overrides = [:]) {
    return [
        account: 'test',
        region: 'us-east-1',
        name: name,
        moniker: MonikerHelper.friggaToMoniker(name),
        isDisabled: false,
        instances: [[healthState: 'Up']] * up + [[healthState: 'OutOfService']] * down,
        capacity: [min: 0, max: 4, desired: 3]
    ] + overrides
  }

  def "pinned should not appear in serialized capacity"() {
    def mapper = new ObjectMapper()
    def capacity = Capacity.builder().min(1).max(1).desired(1).build()

    expect:
    !mapper.writeValueAsString(capacity).contains('pinned')
  }

  void "should ignore disabled traffic guards"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo", enabled: false])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, moniker, "test", location, "aws", "x")

    then: 'we never look up anything in clouddriver if traffic guards are not enabled'
    notThrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
    0 * oortHelper._
  }

  void "should throw exception when target server group is the only one enabled in cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, moniker, "test", location, "aws", "x")

    then:
    def e = thrown(TrafficGuardException)
    e.message.startsWith("This cluster ('app-foo' in test/us-east-1) has traffic guards enabled.")
    1 * front50Service.get("app") >> application
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> [
      serverGroups: [
        makeServerGroup(targetName, 1),
        makeServerGroup(otherName, 0, 1, [isDisabled: true])
      ]
    ]
  }

  void "should throw exception when target server group can not be found in cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval("app-foo-v999", MonikerHelper.friggaToMoniker("app-foo-v999"), "test", location, "aws", "x")

    then:
    def e = thrown(TrafficGuardException)
    e.message.startsWith("Could not find server group 'app-foo-v999'")
    1 * front50Service.get("app") >> application
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> [
      serverGroups: [
        makeServerGroup(targetName, 1),
        makeServerGroup(otherName, 0, 1, [isDisabled: true])
      ]
    ]
  }

  void "should be able to handle a server group in a namespace"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, moniker, "test", location, "aws", "x")

    then:
    notThrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> [
      serverGroups: [
        makeServerGroup(targetName, 2, 0, [namespace: 'us-east-1']),
        makeServerGroup(otherName, 1, 0, [namespace: 'us-east-1'])
      ]
    ]

  }

  void "should throw exception when capacity ratio less than configured minimum"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, moniker, "test", location, "aws", "x")

    then:
    thrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> [
      serverGroups: [
        makeServerGroup(targetName, 2),
        makeServerGroup(otherName, 1)
      ]
    ]

    // configure a minimum desired ratio of 40%, which means going from 3 to 1 instances (33%) is not ok
    1 * dynamicConfigService.getConfig(Double.class, TrafficGuard.MIN_CAPACITY_RATIO, 0d) >> 0.4d
  }

  void "should not throw exception when capacity ratio more than configured minimum"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, moniker, "test", location, "aws", "x")

    then:
    notThrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> [
      serverGroups: [
        makeServerGroup(targetName, 2),
        makeServerGroup(otherName, 1)
      ]
    ]

    // configure a minimum desired ratio of 25%, which means going from 3 to 1 instances (33%) is ok
    1 * dynamicConfigService.getConfig(Double.class, TrafficGuard.MIN_CAPACITY_RATIO, 0d) >> 0.25d
  }

  void "should throw exception when disabling multiple server groups leads to reduced capacity"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    // simulate the case where we have a main server group with 100 instances and a debugging one with 1 instance
    // then a red/black operation can lead to the newest (small) one being cloned and everything else disabled
    List<TargetServerGroup> serverGroupsGoingAway =
      [makeServerGroup("app-foo-v000", 100) as TargetServerGroup,
       makeServerGroup("app-foo-v001", 1) as TargetServerGroup]

    when:
    trafficGuard.verifyTrafficRemoval(
      serverGroupsGoingAway,
      serverGroupsGoingAway + [makeServerGroup("app-foo-v002", 1) as TargetServerGroup],
      "test", "x")

    then:
    thrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
    1 * dynamicConfigService.getConfig(Double.class, TrafficGuard.MIN_CAPACITY_RATIO, 0d) >> 0.40d
  }

  void "should bypass capacity check for pinned server groups"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])
    List<TargetServerGroup> serverGroupsGoingAway =
        [makeServerGroup("app-foo-v000", 3, 0, [capacity: [min: 3, max: 3, desired: 3]]) as TargetServerGroup,
         makeServerGroup("app-foo-v001", 3, 0, [capacity: [min: 3, max: 3, desired: 3]]) as TargetServerGroup]

    when:
    trafficGuard.verifyTrafficRemoval(
        serverGroupsGoingAway,
        serverGroupsGoingAway + [makeServerGroup("app-foo-v002", 1) as TargetServerGroup],
        "test", "x")

    then:
    notThrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
  }

  void "should still make sure that capacity does not drop to 0 for pinned server groups"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])
    List<TargetServerGroup> serverGroupsGoingAway =
        [makeServerGroup("app-foo-v000", 3, 0, [capacity: [min: 3, max: 3, desired: 3]]) as TargetServerGroup,
         makeServerGroup("app-foo-v001", 3, 0, [capacity: [min: 3, max: 3, desired: 3]]) as TargetServerGroup]

    when:
    trafficGuard.verifyTrafficRemoval(
        serverGroupsGoingAway,
        serverGroupsGoingAway + [makeServerGroup("app-foo-v002", 0) as TargetServerGroup],
        "test", "x")

    then:
    def e = thrown(TrafficGuardException)
    e.message.contains("would leave the cluster with no instances up")
    1 * front50Service.get("app") >> application
  }

  @Unroll
  def "should still apply capacity check when pinned server groups don't qualify"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(
        serverGroupsGoingAway,
        serverGroupsGoingAway + [makeServerGroup("app-foo-v002", 1) as TargetServerGroup],
        "test", "x")

    then:
    def e = thrown(TrafficGuardException)
    e.message.contains("would leave the cluster with 1 instance up")
    1 * front50Service.get("app") >> application
    1 * dynamicConfigService.getConfig(Double.class, TrafficGuard.MIN_CAPACITY_RATIO, 0d) >> 0.4d

    where:
    serverGroupsGoingAway << [
        // only one pinned server group going away
        [makeServerGroup("app-foo-v000", 100, 0, [capacity: [min: 100, max: 100, desired: 100]]) as TargetServerGroup],

        // only some of the server groups going away are pinned
        [makeServerGroup("app-foo-v000", 10, 0, [capacity: [min: 10, max: 10, desired: 10]]) as TargetServerGroup,
         makeServerGroup("app-foo-v001", 10, 0, [capacity: [min: 10, max: 100, desired: 10]]) as TargetServerGroup],

        // the pinned server groups have different sizes
        [makeServerGroup("app-foo-v000", 10, 0, [capacity: [min: 1, max: 1, desired: 1]]) as TargetServerGroup,
         makeServerGroup("app-foo-v001", 10, 0, [capacity: [min: 100, max: 100, desired: 100]]) as TargetServerGroup]
    ]
  }

  void "should not throw exception during a regular shrink/disable cluster-wide operation"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    // simulate the case where we have a main server group with 100 instances and a debugging one with 1 instance
    // then a red/black operation can lead to the newest (small) one being cloned and everything else disabled
    List<TargetServerGroup> serverGroupsGoingAway =
      [makeServerGroup("app-foo-v000", 0, 100) as TargetServerGroup,
       makeServerGroup("app-foo-v001", 100, 0) as TargetServerGroup]

    when:
    trafficGuard.verifyTrafficRemoval(
      serverGroupsGoingAway,
      serverGroupsGoingAway + [makeServerGroup("app-foo-v002", 100) as TargetServerGroup],
      "test", "x")

    then:
    notThrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
    1 * dynamicConfigService.getConfig(Double.class, TrafficGuard.MIN_CAPACITY_RATIO, 0d) >> 0.40d
  }

  void "should be able to destroy multiple empty or disabled server groups as one operation"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])
    List<TargetServerGroup> serverGroupsGoingAway =
      [makeServerGroup("app-foo-v000", 0) as TargetServerGroup,
       makeServerGroup("app-foo-v001", 0, 3) as TargetServerGroup]

    when:
    trafficGuard.verifyTrafficRemoval(
      serverGroupsGoingAway,
      serverGroupsGoingAway,
      "test", "x")

    then:
    notThrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
  }

  void "should throw exception when target server group is the only one in cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, moniker, "test", location, "aws", "x")

    then:
    thrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> [
      serverGroups: [makeServerGroup(targetName, 1)]
    ]
  }

  void "should validate location when looking for other enabled server groups in cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, moniker, "test", location, "aws", "x")

    then:
    thrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> [
      serverGroups: [
        makeServerGroup(targetName, 1),
        makeServerGroup(otherName, 1, 0, [region: 'us-west-1'])]
    ]
  }

  void "should not throw exception when cluster has no active instances"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, moniker, "test", location, "aws", "x")

    then:
    noExceptionThrown()
    1 * front50Service.get("app") >> application
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> [
      serverGroups: [makeServerGroup(targetName, 0, 1)]
    ]
  }

  void "should validate existence of cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, moniker, "test", location, "aws", "x")

    then:
    def e = thrown(TrafficGuardException)
    e.message.startsWith('Could not find cluster')
    1 * front50Service.get("app") >> application
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> Optional.empty()
  }

  void "should not throw if another server group is enabled and has instances"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, moniker, "test", location, "aws", "x")

    then:
    notThrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> [
      serverGroups: [
        makeServerGroup(targetName, 1),
        makeServerGroup(otherName, 1)]
    ]
  }

  void "should throw if another server group is enabled but no instances are 'Up'"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, moniker, "test", location, "aws", "x")

    then:
    thrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> [
      serverGroups: [
        makeServerGroup(targetName, 1),
        makeServerGroup(otherName, 0, 1)]
    ]
  }

  @Unroll
  void "hasDisableLock should match on wildcards in stack, detail, account, location"() {
    given:
    addGuard([account: guardAccount, stack: guardStack, detail: guardDetail, location: guardLocation])

    when:
    boolean result = trafficGuard.hasDisableLock(new Moniker(app: cluster, cluster: cluster), account, location)

    then:
    result == expected
    1 * front50Service.get("app") >> application

    where:
    cluster | account | guardStack | guardDetail | guardAccount | guardLocation || expected
    "app"   | "test"  | null       | null        | "test"       | "us-east-1"   || true // exact match
    "app"   | "test"  | "*"        | null        | "test"       | "us-east-1"   || true
    "app"   | "test"  | null       | "*"         | "test"       | "us-east-1"   || true
    "app"   | "test"  | null       | null        | "*"          | "us-east-1"   || true
    "app"   | "test"  | null       | null        | "test"       | "*"           || true
    "app"   | "test"  | "*"        | "*"         | "*"          | "*"           || true
    "app"   | "test"  | null       | null        | "prod"       | "us-east-1"   || false // different account
    "app"   | "test"  | null       | null        | "test"       | "eu-west-1"   || false // different location
    "app"   | "test"  | "foo"      | null        | "test"       | "us-east-1"   || false // different stack
    "app"   | "test"  | null       | "zz"        | "test"       | "us-east-1"   || false // different detail
  }

  void "hasDisableLock returns false on missing applications"() {
    when:
    boolean result = trafficGuard.hasDisableLock(new Moniker(app: "app", cluster: "app"), "test", location)

    then:
    result == false
    1 * front50Service.get("app") >> null
  }

  void "hasDisableLock returns false on applications with no guards configured"() {
    when:
    boolean result = trafficGuard.hasDisableLock(new Moniker(app: "app", cluster: "app"), "test", location)

    then:
    !applicationDetails.containsKey("trafficGuards")
    result == false
    1 * front50Service.get("app") >> {
      throw new RetrofitError(null, null, new Response("http://stash.com", 404, "test reason", [], null), null, null, null, null)
    }
  }

  void "throws exception if application retrieval throws an exception"() {
    when:
    Exception thrownException = new RuntimeException("bad read")
    trafficGuard.hasDisableLock(new Moniker(app: "app", cluster: "app"), "test", location)

    then:
    thrown(RuntimeException)
    1 * front50Service.get("app") >> {
      throw thrownException
    }
  }

  void "hasDisableLock returns false on applications with empty guards configured"() {
    when:
    applicationDetails.put("trafficGuards", [])
    boolean result = trafficGuard.hasDisableLock(new Moniker(app: "app", cluster: "app"), "test", location)

    then:
    result == false
    1 * front50Service.get("app") >> application
  }

  void "instance termination should fail when last healthy instance in only server group in cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])
    def targetServerGroup = makeServerGroup(targetName, 0, 0,
      [instances: [[name: "i-1", healthState: "Up"], [name: "i-2", healthState: "Down"]]])

    when:
    trafficGuard.verifyInstanceTermination(null, moniker, ["i-1"], "test", location, "aws", "x")

    then:
    thrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
    1 * oortHelper.getSearchResults("i-1", "instances", "aws") >>
      [[results: [[account: "test", region: location.value, serverGroup: targetName]]]]
    1 * oortHelper.getTargetServerGroup("test", targetName, location.value, "aws") >> Optional.of(new TargetServerGroup(targetServerGroup))
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >>
      [serverGroups: [targetServerGroup]]
  }

  void "instance termination should fail when last healthy instance in only active server group in cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])
    def targetServerGroup = makeServerGroup(targetName, 0, 0, [instances: [[name: "i-1", healthState: "Up"], [name: "i-2", healthState: "Down"]]])

    when:
    trafficGuard.verifyInstanceTermination(null, MonikerHelper.friggaToMoniker(null), ["i-1"], "test", location, "aws", "x")

    then:
    thrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
    1 * oortHelper.getSearchResults("i-1", "instances", "aws") >>
      [[results: [[account: "test", region: location.value, serverGroup: targetName]]]]
    1 * oortHelper.getTargetServerGroup("test", targetName, location.value, "aws") >> Optional.of(new TargetServerGroup(targetServerGroup))
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> [
      serverGroups: [
        targetServerGroup,
        makeServerGroup(otherName, 0, 1)
      ]
    ]
  }

  void "instance termination should succeed when other server group in cluster contains healthy instance"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])
    def targetServerGroup = makeServerGroup(targetName, 0, 0, [instances: [[name: "i-1", healthState: "Up"], [name: "i-2", healthState: "Down"]]])

    when:
    trafficGuard.verifyInstanceTermination(null, moniker, ["i-1"], "test", location, "aws", "x")

    then:
    notThrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
    1 * oortHelper.getSearchResults("i-1", "instances", "aws") >>
      [[results: [[account: "test", region: location.value, serverGroup: targetName]]]]
    1 * oortHelper.getTargetServerGroup("test", targetName, location.value, "aws") >> Optional.of(new TargetServerGroup(targetServerGroup))
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> [
      serverGroups: [
        targetServerGroup,
        makeServerGroup(otherName, 1, 0)
      ]
    ]
  }

  void "instance termination should fail when trying to terminate all up instances in the cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])
    def targetServerGroup = makeServerGroup(targetName, 0, 0, [instances: [[name: "i-1", healthState: "Up"], [name: "i-2", healthState: "Up"]]])

    when:
    trafficGuard.verifyInstanceTermination(null, moniker, ["i-1", "i-2"], "test", location, "aws", "x")

    then:
    thrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
    1 * oortHelper.getSearchResults("i-1", "instances", "aws") >> [[results: [[account: "test", region: location.value, serverGroup: targetName]]]]
    1 * oortHelper.getSearchResults("i-2", "instances", "aws") >> [[results: [[account: "test", region: location.value, serverGroup: targetName]]]]
    1 * oortHelper.getTargetServerGroup("test", targetName, location.value, "aws") >> Optional.of(new TargetServerGroup(targetServerGroup))
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> [
      serverGroups: [
        targetServerGroup,
        makeServerGroup(otherName, 0, 1)
      ]
    ]
  }

  void "instance termination should succeed when instance is not up, regardless of other instances"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyInstanceTermination(null, moniker, ["i-1"], "test", location, "aws", "x")

    then:
    notThrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
    1 * oortHelper.getSearchResults("i-1", "instances", "aws") >>
      [[results: [[account: "test", region: location.value, serverGroup: targetName]]]]
    1 * oortHelper.getTargetServerGroup("test", targetName, location.value, "aws") >>
      Optional.of(new TargetServerGroup(makeServerGroup(targetName, 0, 0, [instances: [[name: "i-1"]]])))
    0 * _
  }

  void "should avoid searching for instance ids when server group provided"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyInstanceTermination(targetName, moniker, ["i-1"], "test", location, "aws", "x")

    then:
    notThrown(TrafficGuardException)

    1 * front50Service.get("app") >> application
    1 * oortHelper.getTargetServerGroup("test", targetName, location.value, "aws") >>
      Optional.of(new TargetServerGroup(makeServerGroup(targetName, 0, 0, [instances: [[name: "i-1"]]])))
    0 * _
  }

  void "should avoid looking up server group details when traffic guards disabled"() {
    given:
    addGuard([enabled: false, account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyInstanceTermination(targetName, moniker, ["i-1"], "test", location, "aws", "x")

    then:
    notThrown(TrafficGuardException)

    1 * front50Service.get("app") >> application
    0 * _
  }

  private void addGuard(Map guard) {
    if (!guard.containsKey("enabled")) {
      guard.enabled = true
    }
    applicationDetails.putIfAbsent("trafficGuards", [])
    applicationDetails.get("trafficGuards") << guard
  }
}
