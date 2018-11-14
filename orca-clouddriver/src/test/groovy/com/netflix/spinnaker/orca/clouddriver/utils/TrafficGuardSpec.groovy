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

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.moniker.Moniker
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
        instances: [[healthState: 'Up']] * up + [[healthState: 'OutOfService']] * down
    ] + overrides
  }

  void "should throw exception when target server group is the only one enabled in cluster"() {
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
        makeServerGroup(otherName, 0, 1, [isDisabled: true])
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
    thrown(TrafficGuardException)
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
    1 * oortHelper.getTargetServerGroup("test", targetName, location.value, "aws") >> (targetServerGroup as TargetServerGroup)
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
    1 * oortHelper.getTargetServerGroup("test", targetName, location.value, "aws") >> (targetServerGroup as TargetServerGroup)
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
    1 * oortHelper.getTargetServerGroup("test", targetName, location.value, "aws") >> (targetServerGroup as TargetServerGroup)
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
    1 * oortHelper.getTargetServerGroup("test", targetName, location.value, "aws") >> (targetServerGroup as TargetServerGroup)
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
      (makeServerGroup(targetName, 0, 0, [instances: [[name: "i-1"]]]) as TargetServerGroup)
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
      (makeServerGroup(targetName, 0, 0, [instances: [[name: "i-1"]]]) as TargetServerGroup)
    0 * _
  }

  private void addGuard(Map guard) {
    applicationDetails.putIfAbsent("trafficGuards", [])
    applicationDetails.get("trafficGuards") << guard
  }
}
