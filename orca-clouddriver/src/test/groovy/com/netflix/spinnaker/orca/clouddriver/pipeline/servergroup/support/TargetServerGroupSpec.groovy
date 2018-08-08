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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Unroll

class TargetServerGroupSpec extends Specification {

  def "get location"() {
    given:
      TargetServerGroup tsg = new TargetServerGroup(region: "north-pole", otherProp: "abc")

    when:
      Location got = tsg.getLocation()

    then:
      got
      got.type == Location.Type.REGION
      got.value == "north-pole"
      tsg.otherProp == "abc"
  }

  @Unroll
  def "get location with exactLocationType"() {
    given:
    TargetServerGroup tsg = new TargetServerGroup(
      type: cloudProvider,
      zone: "north-pole-1",
      namespace: "santa-prod",
      region: "north-pole",
      otherProp: "abc"
    )

    when:
    Location got = tsg.getLocation(exactLocationType)

    then:
    got
    got.type == expectedLocationType
    got.value == expectedLocationValue
    tsg.otherProp == "abc"

    where:
    exactLocationType       | cloudProvider | expectedLocationType    | expectedLocationValue
    Location.Type.ZONE      | "aws"         | Location.Type.ZONE      | "north-pole-1"
    Location.Type.NAMESPACE | "aws"         | Location.Type.NAMESPACE | "santa-prod"
    Location.Type.REGION    | "aws"         | Location.Type.REGION    | "north-pole"
    Location.Type.ZONE      | "gce"         | Location.Type.ZONE      | "north-pole-1"
    Location.Type.NAMESPACE | "gce"         | Location.Type.NAMESPACE | "santa-prod"
    Location.Type.REGION    | "gce"         | Location.Type.REGION    | "north-pole"
  }

  @Unroll
  def "dynamically bound stage"() {

    when:
      def stage = new Stage(context: context, execution: new Execution(Execution.ExecutionType.PIPELINE, "app"))

    then:
      TargetServerGroup.isDynamicallyBound(stage) == want

    where:
      context                         || want
      [:]                             || false
      [target: "current_asg"]         || false
      [asgName: "test-app-v001"]      || false
      [target: "current_asg_dynamic"] || true
  }

  @Unroll
  def "params from stage"() {
    when:
      def context = [
        serverGroupName: serverGroupName,
        cloudProvider  : provider,
        cluster        : cluster,
        regions        : regions,
        region         : region,
        target         : target,
        zones          : zones,
      ]
      def stage = new Stage(context: context, execution: new Execution(Execution.ExecutionType.PIPELINE, "app"))
      def p = TargetServerGroup.Params.fromStage(stage)

    then:
      p
      p.locations == locations
      p.app == "test"
      p.cluster == "test-app"

    where:
      serverGroupName | target        | cluster    | zones            | regions        | region       | provider | locations
      "test-app-v001" | null          | null       | ["north-pole-1"] | null           | null         | "gce"    | [new Location(type: Location.Type.ZONE, value:"north-pole-1")]

      "test-app-v001" | "current_asg" | "test-app" | null             | ["north-pole"] | null         | null     | [new Location(type: Location.Type.REGION, value:"north-pole")]
      "test-app-v001" | "current_asg" | "test-app" | null             | ["north-pole"] | null         | null     | [new Location(type: Location.Type.REGION, value:"north-pole")]
      "test-app-v001" | "current_asg" | "test-app" | ["north-pole-1"] | ["north-pole"] | null         | "gce"    | [new Location(type: Location.Type.REGION, value:"north-pole")]
      "test-app-v001" | "current_asg" | "test-app" | ["north-pole-1"] | ["north-pole"] | null         | "aws"    | [new Location(type: Location.Type.REGION, value:"north-pole")]

      "test-app-v001" | "current_asg" | "test-app" | null             | null           | "north-pole" | null     | [new Location(type: Location.Type.REGION, value:"north-pole")]
      "test-app-v001" | "current_asg" | "test-app" | null             | null           | "north-pole" | null     | [new Location(type: Location.Type.REGION, value:"north-pole")]
      "test-app-v001" | "current_asg" | "test-app" | ["north-pole-1"] | null           | "north-pole" | "gce"    | [new Location(type: Location.Type.REGION, value:"north-pole")]
      "test-app-v001" | "current_asg" | "test-app" | ["north-pole-1"] | null           | "north-pole" | "aws"    | [new Location(type: Location.Type.REGION, value:"north-pole")]

  }
}
