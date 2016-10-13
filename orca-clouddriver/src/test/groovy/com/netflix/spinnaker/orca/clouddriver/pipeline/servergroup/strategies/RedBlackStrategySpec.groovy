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

import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.DisableClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ScaleDownClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ShrinkClusterStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import spock.lang.Specification

class RedBlackStrategySpec extends Specification {

  def ShrinkClusterStage shrinkClusterStage = new ShrinkClusterStage()
  def ScaleDownClusterStage scaleDownClusterStage = new ScaleDownClusterStage()
  def DisableClusterStage disableClusterStage = new DisableClusterStage()

  def "should compose flow"() {
    given:
      def ctx = [
          account          : "testAccount",
          application      : "unit",
          stack            : "tests",
          cloudProvider    : "aws",
          region           : "north",
          availabilityZones: [
              north: ["pole-1a"]
          ]
      ]
      def stage = new PipelineStage(new Pipeline(), "whatever", ctx)
      def strat = new RedBlackStrategy(shrinkClusterStage: shrinkClusterStage,
                                       scaleDownClusterStage: scaleDownClusterStage,
                                       disableClusterStage: disableClusterStage)

    when:
      def syntheticStages = strat.composeFlow(stage)
    def beforeStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
    def afterStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
      beforeStages.isEmpty()
      afterStages.size() == 1
      afterStages.first().type == disableClusterStage.type
      afterStages.first().context == [
          credentials                   : "testAccount",
          cloudProvider                 : "aws",
          cluster                       : "unit-tests",
          region                        : "north",
          remainingEnabledServerGroups  : 1,
          preferLargerOverNewer         : false,
          interestingHealthProviderNames: null
      ]

    when:
      ctx.maxRemainingAsgs = 10
      stage = new PipelineStage(new Pipeline(), "whatever", ctx)
      syntheticStages = strat.composeFlow(stage)
    beforeStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
    afterStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
      beforeStages.isEmpty()
      afterStages.size() == 2
      afterStages.first().type == shrinkClusterStage.type
      afterStages.first().context.shrinkToSize == 10
      afterStages.last().type == disableClusterStage.type

    when:
      ctx.scaleDown = true
      stage = new PipelineStage(new Pipeline(), "whatever", ctx)
      syntheticStages = strat.composeFlow(stage)
    beforeStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
    afterStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
      beforeStages.isEmpty()
      afterStages.size() == 3
      afterStages[0].type == shrinkClusterStage.type
      afterStages[1].type == disableClusterStage.type
      afterStages[2].type == scaleDownClusterStage.type
    afterStages[2].context.allowScaleDownActive == false

  }
}
