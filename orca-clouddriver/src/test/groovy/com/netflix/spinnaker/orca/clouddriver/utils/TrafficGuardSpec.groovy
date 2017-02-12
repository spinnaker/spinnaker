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

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location.Type
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class TrafficGuardSpec extends Specification {

  OortHelper oortHelper = Mock(OortHelper)

  Front50Service front50Service = Mock(Front50Service)

  Application application = Stub(Application) {
    details() >> applicationDetails
  }

  Map otherServerGroup
  Map targetServerGroup
  Location location

  @Shared
  Map<String, Object> applicationDetails = [:]

  @Subject
  TrafficGuard trafficGuard = new TrafficGuard(oortHelper, front50Service)

  void setup() {
    targetServerGroup = [
      account: "test",
      region : "us-east-1",
      name   : "app-foo-v001"
    ]
    otherServerGroup = [
      account   : "test",
      region    : "us-east-1",
      name      : "app-foo-v000",
      isDisabled: true
    ]
    location = new Location(Type.REGION, "us-east-1")
    applicationDetails.clear()
  }

  void "should throw exception when target server group is the only one enabled in cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval("app-foo-v001", "test", location, "aws", "x")

    then:
    thrown(IllegalStateException)
    1 * front50Service.get("app") >> application
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> [
      serverGroups: [targetServerGroup, otherServerGroup]
    ]
  }

  void "should throw exception when target server group is the only one in cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval("app-foo-v001", "test", location, "aws", "x")

    then:
    thrown(IllegalStateException)
    1 * front50Service.get("app") >> application
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> [
      serverGroups: [targetServerGroup]
    ]
  }

  void "should validate location when looking for other enabled server groups in cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])
    otherServerGroup.isDisabled = false
    otherServerGroup.region = "us-west-1"

    when:
    trafficGuard.verifyTrafficRemoval("app-foo-v001", "test", location, "aws", "x")

    then:
    thrown(IllegalStateException)
    1 * front50Service.get("app") >> application
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> [
      serverGroups: [targetServerGroup, otherServerGroup]
    ]
  }

  void "should validate existence of cluster"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])

    when:
    trafficGuard.verifyTrafficRemoval("app-foo-v001", "test", location, "aws", "x")

    then:
    thrown(IllegalStateException)
    1 * front50Service.get("app") >> application
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> Optional.empty()
  }

  void "should not throw if another server group is enabled and has instances"() {
    given:
    addGuard([account: "test", location: "us-east-1", stack: "foo"])
    otherServerGroup.isDisabled = false
    otherServerGroup.instances = [[id: 'a']]

    when:
    trafficGuard.verifyTrafficRemoval("app-foo-v001", "test", location, "aws", "x")

    then:
    notThrown(IllegalStateException)
    1 * front50Service.get("app") >> application
    1 * oortHelper.getCluster("app", "test", "app-foo", "aws") >> [
      serverGroups: [targetServerGroup, otherServerGroup]
    ]
  }

  @Unroll
  void "hasDisableLock should match on wildcards in stack, detail, account, location"() {
    given:
    addGuard([account: guardAccount, stack: guardStack, detail: guardDetail, location: guardLocation])

    when:
    boolean result = trafficGuard.hasDisableLock(cluster, account, location)

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
    boolean result = trafficGuard.hasDisableLock("app", "test", location)

    then:
    result == false
    1 * front50Service.get("app") >> null
  }

  void "hasDisableLock returns false on applications with no guards configured"() {
    when:
    boolean result = trafficGuard.hasDisableLock("app", "test", location)

    then:
    !applicationDetails.containsKey("trafficGuards")
    result == false
    1 * front50Service.get("app") >> application
  }

  void "hasDisableLock returns false on applications with empty guards configured"() {
    when:
    applicationDetails.put("trafficGuards", [])
    boolean result = trafficGuard.hasDisableLock("app", "test", location)

    then:
    result == false
    1 * front50Service.get("app") >> application
  }

  private void addGuard(Map guard) {
    applicationDetails.putIfAbsent("trafficGuards", [])
    applicationDetails.get("trafficGuards") << guard
  }

}
