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
 *
 */

package com.netflix.spinnaker.clouddriver.safety

import com.netflix.frigga.Names
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.clouddriver.exceptions.TrafficGuardException
import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.model.SimpleInstance
import com.netflix.spinnaker.clouddriver.model.SimpleServerGroup
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.moniker.Moniker
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class TrafficGuardSpec extends Specification {

  ClusterProvider clusterProvider = Mock() {
    getCloudProviderId() >> "aws"
  }

  Front50Service front50Service = Mock(Front50Service)
  Registry registry = new NoopRegistry()
  DynamicConfigService dynamicConfigService = Mock(DynamicConfigService)

  @Shared String location = "us-east-1"
  @Shared Moniker moniker = new Moniker(app: "app", stack: "foo", cluster: "app-foo", sequence: 1)
  @Shared String targetName = "app-foo-v001"
  @Shared String otherName = "app-foo-v000"
  @Shared Map<String, Object> application = [:]

  @Subject
  TrafficGuard trafficGuard = new TrafficGuard(
    Collections.singletonList(clusterProvider),
    Optional.of(front50Service),
    registry,
    dynamicConfigService
  )

  void setup() {
    application.clear()
  }

  def makeServerGroup(String name, int up, int down = 0, Map overrides = [:]) {
    Set<SimpleInstance> instances = []

    if (up > 0) {
      instances.addAll((1..up).collect { new SimpleInstance(healthState: HealthState.Up) })
    }
    if (down > 0) {
      instances.addAll((1..down).collect { new SimpleInstance(healthState: HealthState.OutOfService )})
    }

    ServerGroup serverGroup =  new SimpleServerGroup([
      region   : 'us-east-1',
      name     : name,
      disabled : false,
      instances: instances,
      capacity : new ServerGroup.Capacity(min: 0, max: 4, desired: 3)
    ] + overrides)
    return serverGroup
  }

  Cluster makeCluster(List<ServerGroup> serverGroups) {
    return new Cluster.SimpleCluster(
      serverGroups: serverGroups
    )
  }

  void "should ignore disabled traffic guards"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo", enabled: false])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, "test", location, "aws", "x")

    then:
    1 * clusterProvider.getCluster("app", "test", "app-foo", false) >> makeCluster([
      makeServerGroup(targetName, 1),
      makeServerGroup(otherName, 0, 1, [disabled: true])
    ])
    1 * front50Service.getApplication("app") >> application
  }

  void "should throw exception when target server group is the only one enabled in cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, "test", location, "aws", "x")

    then:
    def e = thrown(TrafficGuardException)
    e.message.startsWith("This cluster ('app-foo' in test/us-east-1) has traffic guards enabled.")
    1 * front50Service.getApplication("app") >> application
    1 * clusterProvider.getCluster("app", "test", "app-foo", false) >> makeCluster([
      makeServerGroup(targetName, 1),
      makeServerGroup(otherName, 0, 1, [disabled: true])
    ])
  }

  void "should throw exception when target server group can not be found in cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval("app-foo-v999", "test", location, "aws", "x")

    then:
    def e = thrown(TrafficGuardException)
    e.message.startsWith("Could not find server group 'app-foo-v999'")
    1 * clusterProvider.getCluster("app", "test", "app-foo", false) >> makeCluster([
        makeServerGroup(targetName, 1),
        makeServerGroup(otherName, 0, 1, [disabled: true])
    ])
  }

  void "should throw exception when capacity ratio less than configured minimum"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, "test", location, "aws", "x")

    then:
    thrown(TrafficGuardException)
    1 * front50Service.getApplication("app") >> application
    1 * clusterProvider.getCluster("app", "test", "app-foo", false) >> makeCluster([
        makeServerGroup(targetName, 2),
        makeServerGroup(otherName, 1)
    ])

    // configure a minimum desired ratio of 40%, which means going from 3 to 1 instances (33%) is not ok
    1 * dynamicConfigService.getConfig(Double.class, TrafficGuard.MIN_CAPACITY_RATIO, 0d) >> 0.4d
  }

  void "should not throw exception when capacity ratio more than configured minimum"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, "test", location, "aws", "x")

    then:
    notThrown(TrafficGuardException)
    1 * front50Service.getApplication("app") >> application
    1 * clusterProvider.getCluster("app", "test", "app-foo", false) >> makeCluster([
        makeServerGroup(targetName, 2),
        makeServerGroup(otherName, 1)
    ])

    // configure a minimum desired ratio of 25%, which means going from 3 to 1 instances (33%) is ok
    1 * dynamicConfigService.getConfig(Double.class, TrafficGuard.MIN_CAPACITY_RATIO, 0d) >> 0.25d
  }

  void "should throw exception when disabling multiple server groups leads to reduced capacity"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    // simulate the case where we have a main server group with 100 instances and a debugging one with 1 instance
    // then a red/black operation can lead to the newest (small) one being cloned and everything else disabled
    List<ServerGroup> serverGroupsGoingAway =
      [makeServerGroup("app-foo-v000", 100),
       makeServerGroup("app-foo-v001", 1)]

    when:
    trafficGuard.verifyTrafficRemoval(
      serverGroupsGoingAway,
      serverGroupsGoingAway + [makeServerGroup("app-foo-v002", 1)],
      "test", "x")

    then:
    thrown(TrafficGuardException)
    1 * front50Service.getApplication("app") >> application
    1 * dynamicConfigService.getConfig(Double.class, TrafficGuard.MIN_CAPACITY_RATIO, 0d) >> 0.40d
  }

  void "should bypass capacity check for pinned server groups"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])
    List<ServerGroup> serverGroupsGoingAway =
      [makeServerGroup("app-foo-v000", 3, 0, [capacity: new ServerGroup.Capacity(min: 3, max: 3, desired: 3)]),
       makeServerGroup("app-foo-v001", 3, 0, [capacity: new ServerGroup.Capacity(min: 3, max: 3, desired: 3)])]

    when:
    trafficGuard.verifyTrafficRemoval(
      serverGroupsGoingAway,
      serverGroupsGoingAway + [makeServerGroup("app-foo-v002", 1)],
      "test", "x")

    then:
    notThrown(TrafficGuardException)
    1 * front50Service.getApplication("app") >> application
  }

  void "should still make sure that capacity does not drop to 0 for pinned server groups"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])
    List<ServerGroup> serverGroupsGoingAway =
      [makeServerGroup("app-foo-v000", 3, 0, [capacity: new ServerGroup.Capacity(min: 3, max: 3, desired: 3)]),
       makeServerGroup("app-foo-v001", 3, 0, [capacity: new ServerGroup.Capacity(min: 3, max: 3, desired: 3)])]

    when:
    trafficGuard.verifyTrafficRemoval(
      serverGroupsGoingAway,
      serverGroupsGoingAway + [makeServerGroup("app-foo-v002", 0)],
      "test", "x")

    then:
    def e = thrown(TrafficGuardException)
    e.message.contains("would leave the cluster with no instances up")
    1 * front50Service.getApplication("app") >> application
  }

  @Unroll
  def "should still apply capacity check when pinned server groups don't qualify"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(
      serverGroupsGoingAway,
      serverGroupsGoingAway + [makeServerGroup("app-foo-v002", 1)],
      "test", "x")

    then:
    def e = thrown(TrafficGuardException)
    e.message.contains("would leave the cluster with 1 instance up")
    1 * front50Service.getApplication("app") >> application
    1 * dynamicConfigService.getConfig(Double.class, TrafficGuard.MIN_CAPACITY_RATIO, 0d) >> 0.4d

    where:
    serverGroupsGoingAway << [
      // only one pinned server group going away
      [makeServerGroup("app-foo-v000", 100, 0, [capacity: new ServerGroup.Capacity(min: 100, max: 100, desired: 100)])],

      // only some of the server groups going away are pinned
      [makeServerGroup("app-foo-v000", 10, 0, [capacity: new ServerGroup.Capacity(min: 10, max: 10, desired: 10)]),
       makeServerGroup("app-foo-v001", 10, 0, [capacity: new ServerGroup.Capacity(min: 10, max: 100, desired: 10)])],

      // the pinned server groups have different sizes
      [makeServerGroup("app-foo-v000", 10, 0, [capacity: new ServerGroup.Capacity(min: 1, max: 1, desired: 1)]),
       makeServerGroup("app-foo-v001", 10, 0, [capacity: new ServerGroup.Capacity(min: 100, max: 100, desired: 100)])]
    ]
  }

  void "should not throw exception during a regular shrink/disable cluster-wide operation"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    // simulate the case where we have a main server group with 100 instances and a debugging one with 1 instance
    // then a red/black operation can lead to the newest (small) one being cloned and everything else disabled
    List<ServerGroup> serverGroupsGoingAway =
      [makeServerGroup("app-foo-v000", 0, 100),
       makeServerGroup("app-foo-v001", 100, 0)]

    when:
    trafficGuard.verifyTrafficRemoval(
      serverGroupsGoingAway,
      serverGroupsGoingAway + [makeServerGroup("app-foo-v002", 100)],
      "test", "x")

    then:
    notThrown(TrafficGuardException)
    1 * front50Service.getApplication("app") >> application
    1 * dynamicConfigService.getConfig(Double.class, TrafficGuard.MIN_CAPACITY_RATIO, 0d) >> 0.40d
  }

  void "should be able to destroy multiple empty or disabled server groups as one operation"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])
    List<ServerGroup> serverGroupsGoingAway =
      [makeServerGroup("app-foo-v000", 0),
       makeServerGroup("app-foo-v001", 0, 3)]

    when:
    trafficGuard.verifyTrafficRemoval(
      serverGroupsGoingAway,
      serverGroupsGoingAway,
      "test", "x")

    then:
    notThrown(TrafficGuardException)
    1 * front50Service.getApplication("app") >> application
  }

  void "should throw exception when target server group is the only one in cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, "test", location, "aws", "x")

    then:
    thrown(TrafficGuardException)
    1 * front50Service.getApplication("app") >> application
    1 * clusterProvider.getCluster("app", "test", "app-foo", false) >> makeCluster([
      makeServerGroup(targetName, 1)
    ])
  }

  void "should validate location when looking for other enabled server groups in cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, "test", location, "aws", "x")

    then:
    thrown(TrafficGuardException)
    1 * front50Service.getApplication("app") >> application
    1 * clusterProvider.getCluster("app", "test", "app-foo", false) >> makeCluster([
      makeServerGroup(targetName, 1),
      makeServerGroup(otherName, 1, 0, [region: 'us-west-1'])
    ])
  }

  void "should not throw exception when cluster has no active instances"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, "test", location, "aws", "x")

    then:
    noExceptionThrown()
    1 * front50Service.getApplication("app") >> application
    1 * clusterProvider.getCluster("app", "test", "app-foo", false) >> makeCluster([
      makeServerGroup(targetName, 0, 1)
    ])
  }

  void "should validate existence of cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, "test", location, "aws", "x")

    then:
    def e = thrown(TrafficGuardException)
    e.message.startsWith('Could not find cluster')
    1 * clusterProvider.getCluster("app", "test", "app-foo", false) >> null
  }

  void "should not throw if another server group is enabled and has instances"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, "test", location, "aws", "x")

    then:
    notThrown(TrafficGuardException)
    1 * front50Service.getApplication("app") >> application
    1 * clusterProvider.getCluster("app", "test", "app-foo", false) >> makeCluster([
      makeServerGroup(targetName, 1),
      makeServerGroup(otherName, 1)
    ])
  }

  void "should throw if another server group is enabled but no instances are 'Up'"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval(targetName, "test", location, "aws", "x")

    then:
    thrown(TrafficGuardException)
    1 * front50Service.getApplication("app") >> application
    1 * clusterProvider.getCluster("app", "test", "app-foo", false) >> makeCluster([
      makeServerGroup(targetName, 1),
      makeServerGroup(otherName, 0, 1)
    ])
  }

  @Unroll
  void "hasDisableLock should match on wildcards in stack, detail, account, location"() {
    given:
    addGuard([account: guardAccount, stack: guardStack, detail: guardDetail, location: guardLocation])

    when:
    boolean result = trafficGuard.hasDisableLock(new Moniker(app: cluster, cluster: cluster), account, location)

    then:
    result == expected
    1 * front50Service.getApplication("app") >> application

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
    1 * front50Service.getApplication("app") >> null
  }

  void "hasDisableLock returns false on applications with no guards configured"() {
    when:
    boolean result = trafficGuard.hasDisableLock(new Moniker(app: "app", cluster: "app"), "test", location)

    then:
    !application.containsKey("trafficGuards")
    result == false
    1 * front50Service.getApplication("app") >> {
      throw new RetrofitError(null, null, new Response("http://stash.com", 404, "test reason", [], null), null, null, null, null)
    }
  }

  void "throws exception if application retrieval throws an exception"() {
    when:
    Exception thrownException = new RuntimeException("bad read")
    trafficGuard.hasDisableLock(new Moniker(app: "app", cluster: "app"), "test", location)

    then:
    thrown(RuntimeException)
    1 * front50Service.getApplication("app") >> {
      throw thrownException
    }
  }

  void "hasDisableLock returns false on applications with empty guards configured"() {
    when:
    application.put("trafficGuards", [])
    boolean result = trafficGuard.hasDisableLock(new Moniker(app: "app", cluster: "app"), "test", location)

    then:
    result == false
    1 * front50Service.getApplication("app") >> application
  }

  @Ignore("verifyInstanceTermination has not been ported yet")
  void "instance termination should fail when last healthy instance in only server group in cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])
    def targetServerGroup = makeServerGroup(targetName, 0, 0,
      [instances: [[name: "i-1", healthState: "Up"], [name: "i-2", healthState: "Down"]]])

    when:
    trafficGuard.verifyInstanceTermination(null, moniker, ["i-1"], "test", location, "aws", "x")

    then:
    thrown(TrafficGuardException)
    1 * front50Service.getApplication("app") >> application
    1 * clusterProvider.getSearchResults("i-1", "instances", "aws") >>
      [[results: [[account: "test", region: location.value, serverGroup: targetName]]]]
    1 * clusterProvider.getTargetServerGroup("test", targetName, location.value, "aws") >> (targetServerGroup)
    1 * clusterProvider.getCluster("app", "test", "app-foo") >>
      [serverGroups: [targetServerGroup]]
  }

  @Ignore("verifyInstanceTermination has not been ported yet")
  void "instance termination should fail when last healthy instance in only active server group in cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])
    def targetServerGroup = makeServerGroup(targetName, 0, 0, [instances: [[name: "i-1", healthState: "Up"], [name: "i-2", healthState: "Down"]]])

    when:
    trafficGuard.verifyInstanceTermination(null, friggaToMoniker(null), ["i-1"], "test", location, "aws", "x")

    then:
    thrown(TrafficGuardException)
    1 * front50Service.get("app") >> application
    1 * clusterProvider.getSearchResults("i-1", "instances", "aws") >>
      [[results: [[account: "test", region: location.value, serverGroup: targetName]]]]
    1 * clusterProvider.getTargetServerGroup("test", targetName, location.value, "aws") >> (targetServerGroup)
    1 * clusterProvider.getCluster("app", "test", "app-foo") >> [
      serverGroups: [
        targetServerGroup,
        makeServerGroup(otherName, 0, 1)
      ]
    ]
  }

  @Ignore("verifyInstanceTermination has not been ported yet")
  void "instance termination should succeed when other server group in cluster contains healthy instance"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])
    def targetServerGroup = makeServerGroup(targetName, 0, 0, [instances: [[name: "i-1", healthState: "Up"], [name: "i-2", healthState: "Down"]]])

    when:
    trafficGuard.verifyInstanceTermination(null, moniker, ["i-1"], "test", location, "aws", "x")

    then:
    notThrown(TrafficGuardException)
    1 * front50Service.getApplication("app") >> application
    1 * clusterProvider.getSearchResults("i-1", "instances", "aws") >>
      [[results: [[account: "test", region: location.value, serverGroup: targetName]]]]
    1 * clusterProvider.getTargetServerGroup("test", targetName, location.value, "aws") >> (targetServerGroup)
    1 * clusterProvider.getCluster("app", "test", "app-foo") >> [
      serverGroups: [
        targetServerGroup,
        makeServerGroup(otherName, 1, 0)
      ]
    ]
  }

  @Ignore("verifyInstanceTermination has not been ported yet")
  void "instance termination should fail when trying to terminate all up instances in the cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])
    def targetServerGroup = makeServerGroup(targetName, 0, 0, [instances: [[name: "i-1", healthState: "Up"], [name: "i-2", healthState: "Up"]]])

    when:
    trafficGuard.verifyInstanceTermination(null, moniker, ["i-1", "i-2"], "test", location, "aws", "x")

    then:
    thrown(TrafficGuardException)
    1 * front50Service.getApplication("app") >> application
    1 * clusterProvider.getSearchResults("i-1", "instances", "aws") >> [[results: [[account: "test", region: location.value, serverGroup: targetName]]]]
    1 * clusterProvider.getSearchResults("i-2", "instances", "aws") >> [[results: [[account: "test", region: location.value, serverGroup: targetName]]]]
    1 * clusterProvider.getTargetServerGroup("test", targetName, location.value, "aws") >> (targetServerGroup)
    1 * clusterProvider.getCluster("app", "test", "app-foo") >> [
      serverGroups: [
        targetServerGroup,
        makeServerGroup(otherName, 0, 1)
      ]
    ]
  }

  @Ignore("verifyInstanceTermination has not been ported yet")
  void "instance termination should succeed when instance is not up, regardless of other instances"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyInstanceTermination(null, moniker, ["i-1"], "test", location, "aws", "x")

    then:
    notThrown(TrafficGuardException)
    0 * front50Service.get("app") >> application
    1 * clusterProvider.getSearchResults("i-1", "instances", "aws") >>
      [[results: [[account: "test", region: location.value, serverGroup: targetName]]]]
    1 * clusterProvider.getTargetServerGroup("test", targetName, location.value, "aws") >>
      (makeServerGroup(targetName, 0, 0, [instances: [[name: "i-1"]]]))
    0 * _
  }

  @Ignore("verifyInstanceTermination has not been ported yet")
  void "should avoid searching for instance ids when server group provided"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyInstanceTermination(targetName, moniker, ["i-1"], "test", location, "aws", "x")

    then:
    notThrown(TrafficGuardException)

    // passes with no front50 check because the instance does not have healthState: Up
    0 * front50Service.getApplication("app") >> application
    1 * clusterProvider.getTargetServerGroup("test", targetName, location.value, "aws") >>
      (makeServerGroup(targetName, 0, 0, [instances: [[name: "i-1"]]]))
    0 * _
  }

  private void addGuard(Map guard) {
    if (!guard.containsKey("enabled")) {
      guard.enabled = true
    }
    application.putIfAbsent("trafficGuards", [])
    application.get("trafficGuards") << guard
  }

  private static Moniker friggaToMoniker(String friggaName) {
    Names names = Names.parseName(friggaName);
    return Moniker.builder()
      .app(names.getApp())
      .stack(names.getStack())
      .detail(names.getDetail())
      .cluster(names.getCluster())
      .sequence(names.getSequence())
      .build();
  }
}
