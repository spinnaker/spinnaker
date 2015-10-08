/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.support

import com.netflix.spinnaker.orca.clouddriver.pipeline.support.TargetServerGroup
import com.netflix.spinnaker.orca.pipeline.model.AbstractStage
import spock.lang.Specification
import spock.lang.Unroll

class TargetServerGroupSpec extends Specification {

  static class TestStage extends AbstractStage {}

  @Unroll
  def "dynamically bound stage"() {

    when:
      def stage = new TestStage(context: context)

    then:
      TargetServerGroup.isDynamicallyBound(stage) == want

    where:
      context                         || want
      [:]                             || false
      [target: "current_asg"]         || false
      [asgName: "test-app-v001"]      || false
      [target: "current_asg_dynamic"] || true
  }

  def "params from stage"() {
    when:
      def context = [
        asgName      : asgName,
        cloudProvider: provider,
        cluster      : cluster,
        regions      : regions,
        target       : target,
        zones        : zones,
      ]
      def stage = new TestStage(context: context)
      def p = TargetServerGroup.Params.fromStage(stage)

    then:
      p
      p.locations == locations
      p.app == "test"
      p.cluster == "test-app"

    where:
      asgName         | target        | cluster    | zones            | regions        | provider || locations
      "test-app-v001" | null          | null       | ["north-pole-1"] | null           | null     || ["north-pole-1"]
      null            | "current_asg" | "test-app" | null             | ["north-pole"] | null     || ["north-pole"]
      null            | "current_asg" | "test-app" | ["north-pole-1"] | ["north-pole"] | "gce"    || ["north-pole-1"]
      null            | "current_asg" | "test-app" | ["north-pole-1"] | ["north-pole"] | "aws"    || ["north-pole"]

  }
}
