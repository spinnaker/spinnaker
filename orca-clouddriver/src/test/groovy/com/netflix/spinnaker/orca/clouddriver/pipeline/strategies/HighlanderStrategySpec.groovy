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

package com.netflix.spinnaker.orca.clouddriver.pipeline.strategies

import com.netflix.spinnaker.orca.clouddriver.pipeline.ShrinkClusterStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification

class HighlanderStrategySpec extends Specification {

  def ShrinkClusterStage shrinkClusterStage = new ShrinkClusterStage()

  def "should compose flow"() {
    given:
      def ctx = [
          account          : "testAccount",
          application      : "unit",
          stack            : "tests",
          cloudProvider    : cloudProvider,
          region           : "north",
          availabilityZones: [
              north: ["pole-1a"]
          ]
      ]
      def stage = new PipelineStage(new Pipeline(), "whatever", ctx)
      def strat = new HighlanderStrategy(shrinkClusterStage: shrinkClusterStage)

    when:
      strat.composeFlow(stage)

    then:
      stage.afterStages.size() == 1
      stage.afterStages.last().stageBuilder == shrinkClusterStage
      stage.afterStages.last().context[locationType] == [locationValue]

    where:
      cloudProvider | locationType | locationValue
      "aws"         | "regions"    | "north"
      "gce"         | "zones"      | "pole-1a"
  }
}
