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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies

import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ShrinkClusterStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import spock.lang.Specification
import spock.lang.Unroll

class HighlanderStrategySpec extends Specification {

  def ShrinkClusterStage shrinkClusterStage = new ShrinkClusterStage()

  @Unroll
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

      if (interestingHealthProviderNames) {
        ctx.interestingHealthProviderNames = interestingHealthProviderNames
      }

      def stage = new PipelineStage(new Pipeline(), "whatever", ctx)
      def strat = new HighlanderStrategy(shrinkClusterStage: shrinkClusterStage)

    when:
      def syntheticStages = strat.composeFlow(stage)
    def beforeStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
    def afterStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
      beforeStages.isEmpty()
      afterStages.size() == 1
      afterStages.last().type == shrinkClusterStage.type
      afterStages.last().context == [
          credentials                   : "testAccount",
          (locationType)                : locationValue,
          cluster                       : "unit-tests",
          cloudProvider                 : cloudProvider,
          shrinkToSize                  : 1,
          retainLargerOverNewer         : false,
          allowDeleteActive             : true,
          interestingHealthProviderNames: propagatedInterestingHealthProviderNames
      ]

    where:
      cloudProvider | locationType | locationValue | interestingHealthProviderNames | propagatedInterestingHealthProviderNames
      "aws"         | "region"     | "north"       | null                           | null
      "gce"         | "region"     | "north"       | null                           | null
      "gce"         | "region"     | "north"       | ["Google"]                     | ["Google"]
  }
}
