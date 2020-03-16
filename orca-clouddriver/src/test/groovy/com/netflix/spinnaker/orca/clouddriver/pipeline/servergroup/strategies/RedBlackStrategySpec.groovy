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

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.DisableClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ScaleDownClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ShrinkClusterStage
import com.netflix.spinnaker.orca.pipeline.WaitStage
import spock.lang.Specification

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class RedBlackStrategySpec extends Specification {

  def dynamicConfigService = Mock(DynamicConfigService)

  def disableClusterStage = new DisableClusterStage(dynamicConfigService)
  def shrinkClusterStage = new ShrinkClusterStage(dynamicConfigService, disableClusterStage)
  def scaleDownClusterStage = new ScaleDownClusterStage(dynamicConfigService)
  def waitStage = new WaitStage()

  def "should compose flow"() {
    given:
      Moniker moniker = new Moniker(app: "unit", stack: "tests")

      def pipeline = pipeline {
        application = "orca"
        stage {
          refId = "1-create"
          type = "createServerGroup"
          context = [
            refId: "stage_createASG"
          ]

          stage {
            refId = "2-deploy1"
            parent
            context = [
              refId                        : "stage",
              account                      : "testAccount",
              application                  : "unit",
              stack                        : "tests",
              moniker                      : moniker,
              cloudProvider                : "aws",
              region                       : "north",
              availabilityZones            : [
                north: ["pole-1a"]
              ]
            ]
          }
        }
      }

      def strat = new RedBlackStrategy(
        shrinkClusterStage: shrinkClusterStage,
        scaleDownClusterStage: scaleDownClusterStage,
        disableClusterStage: disableClusterStage,
        waitStage: waitStage
      )
      def stage = pipeline.stageByRef("2-deploy1")

    when: 'planning without having run createServerGroup'
      def beforeStages = strat.composeBeforeStages(stage)
      def afterStages = strat.composeAfterStages(stage)

    then:
      beforeStages.size() == 0
      afterStages.size() == 0

    when: 'deploying into a new cluster (no source server group)'
      pipeline.stageByRef("1-create").status = ExecutionStatus.RUNNING
      pipeline.stageByRef("1-create").context.source = [
        account: "testAccount",
        region: "north",
        serverGroupName: "unit-tests-v000",
        asgName: "unit-tests-v000"
      ]
      beforeStages = strat.composeBeforeStages(stage)
      afterStages = strat.composeAfterStages(stage)

    then:
      beforeStages.isEmpty()
      afterStages.size() == 1
      afterStages.first().type == disableClusterStage.type
      afterStages.first().context == [
          credentials                   : "testAccount",
          cloudProvider                 : "aws",
          cluster                       : "unit-tests",
          moniker                       : moniker,
          region                        : "north",
          remainingEnabledServerGroups  : 1,
          preferLargerOverNewer         : false,
      ]

    when: 'maxRemainingAsgs is specified'
      pipeline.stageByRef("2-deploy1").context.maxRemainingAsgs = 10
      beforeStages = strat.composeBeforeStages(stage)
      afterStages = strat.composeAfterStages(stage)

    then:
      beforeStages.isEmpty()
      afterStages.size() == 2
      afterStages.first().type == shrinkClusterStage.type
      afterStages.first().context.shrinkToSize == 10
      afterStages.last().type == disableClusterStage.type

    when: 'scaleDown is true'
      pipeline.stageByRef("2-deploy1").context.scaleDown = true
      beforeStages = strat.composeBeforeStages(stage)
      afterStages = strat.composeAfterStages(stage)

    then:
      beforeStages.isEmpty()
      afterStages.size() == 3
      afterStages[0].type == shrinkClusterStage.type
      afterStages[1].type == disableClusterStage.type
      afterStages[2].type == scaleDownClusterStage.type
      afterStages[2].context.allowScaleDownActive == false

    when: 'interestingHealthProviderNames is specified'
      pipeline.stageByRef("2-deploy1").context.interestingHealthProviderNames = ["Google"]
      beforeStages = strat.composeBeforeStages(stage)
      afterStages = strat.composeAfterStages(stage)

    then:
      beforeStages.isEmpty()
      afterStages.size() == 3
      afterStages.first().type == shrinkClusterStage.type
      afterStages.first().context.interestingHealthProviderNames == ["Google"]

    when: 'delayBeforeDisableSec and delayBeforeScaleDownSec are specified'
      pipeline.stageByRef("2-deploy1").context.delayBeforeDisableSec = 5
      pipeline.stageByRef("2-deploy1").context.delayBeforeScaleDownSec = 10
      beforeStages = strat.composeBeforeStages(stage)
      afterStages = strat.composeAfterStages(stage)

    then:
      beforeStages.isEmpty()
      afterStages[0].type == shrinkClusterStage.type
      afterStages[1].type == waitStage.type
      afterStages[1].context.waitTime == 5
      afterStages[2].type == disableClusterStage.type
      afterStages[3].type == waitStage.type
      afterStages[3].context.waitTime == 10
      afterStages[4].type == scaleDownClusterStage.type
  }
}
