/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.orca.kato.pipeline.support

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ScaleToServerGroupResizeStrategySpec extends Specification {
  Stage stage = new Stage<>(new Pipeline("orca"), "Scale", [:])
  OortHelper oortHelper = Mock(OortHelper)

  @Subject
  def strategy = new ScaleToServerGroupResizeStrategy(oortHelper: oortHelper)

  def "should throw exception if no source is available"() {
    when:
    strategy.capacityForOperation(stage, "test", "s-v001", "aws", null, null)

    then:
    thrown(IllegalStateException)
  }

  @Unroll
  def "should #verb #action"() {
    expect:
    strategy.handles(action) == shouldHandle

    where:
    action << ResizeStrategy.ResizeAction.values()
    shouldHandle = action == ResizeStrategy.ResizeAction.scale_to_server_group
    verb = shouldHandle ? "handle" : "not handle"
  }

  def "should throw exception if source server group does not exist"() {
    given:
    stage.context.source = [
      credentials    : "test",
      serverGroupName: "s-v001",
      region         : "us-west-1",
      cloudProvider  : "aws"
    ]

    when:
    strategy.capacityForOperation(stage, "test", "s-v001", "aws", null, null)

    then:
    1 * oortHelper.getTargetServerGroup("test", "s-v001", "us-west-1", "aws") >> { return Optional.empty() }
    thrown(IllegalStateException)
  }

  @Unroll
  def "should return source server group capacity"() {
    given:
    stage.context = [
      source            : [
        credentials    : "test",
        serverGroupName: "s-v001",
        region         : "us-west-1",
        cloudProvider  : "aws"
      ],
      pinMinimumCapacity: pinMinimumCapacity
    ]

    when:
    def capacity = strategy.capacityForOperation(stage, "test", "s-v001", "aws", null, null)

    then:
    1 * oortHelper.getTargetServerGroup("test", "s-v001", "us-west-1", "aws") >> {
      return Optional.of(new TargetServerGroup(
        capacity: [
          min    : 1,
          max    : 3,
          desired: 3
        ]
      ))
    }

    capacity.min == expectedMinimumCapacity
    capacity.max == 3
    capacity.desired == 3

    where:
    pinMinimumCapacity || expectedMinimumCapacity
    null               || 1
    false              || 1
    true               || 3
  }

  @Unroll
  def "should extract location from region/regions/zone/zones"() {
    given:
    def source = new ResizeStrategy.Source(zones, regions, region, zone, null, null, null)

    expect:
    // priority: region -> regions -> zone -> zones
    source.location == expectedLocation

    where:
    region    | zone    | regions     | zones     || expectedLocation
    "region1" | "zone1" | ["region2"] | ["zone2"] || "region1"
    null      | "zone1" | ["region2"] | ["zone2"] || "region2"
    null      | "zone1" | null        | ["zone2"] || "zone1"
    null      | null    | null        | ["zone2"] || "zone2"
  }
}
