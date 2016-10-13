/*
 * Copyright 2015 Netflix, Inc.
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


package com.netflix.spinnaker.orca.clouddriver.pipeline

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.DisableServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.EnableServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.RollbackServerGroupStage
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import spock.lang.Shared
import spock.lang.Specification

class RollbackServerGroupStageSpec extends Specification {
  @Shared
  def enableServerGroupStage = new EnableServerGroupStage()

  @Shared
  def disableServerGroupStage = new DisableServerGroupStage()

  @Shared
  def resizeServerGroupStage = new ResizeServerGroupStage()

  def "should inject enable, resize and disable stages corresponding to the server group being restored and rollbacked"() {
    given:
    def autowireCapableBeanFactory = Stub(AutowireCapableBeanFactory) {
      autowireBean(_) >> { RollbackServerGroupStage.ExplicitRollback rollback ->
        rollback.enableServerGroupStage = enableServerGroupStage
        rollback.disableServerGroupStage = disableServerGroupStage
        rollback.resizeServerGroupStage = resizeServerGroupStage
      }
    }

    def rollbackServerGroupStage = new RollbackServerGroupStage()
    rollbackServerGroupStage.autowireCapableBeanFactory = autowireCapableBeanFactory

    def stage = new PipelineStage(new Pipeline(), "rollbackServerGroup", [
      rollbackType: "EXPLICIT",
      rollbackContext: [
        restoreServerGroupName: "servergroup-v001",
        rollbackServerGroupName: "servergroup-v002"
      ],
      credentials: "test",
      cloudProvider: "aws"
    ])

    when:
    def tasks = rollbackServerGroupStage.buildTaskGraph(stage)
    def allStages = rollbackServerGroupStage.aroundStages(stage)
    def beforeStages = allStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
    def afterStages = allStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
    tasks.iterator().size() == 0
    beforeStages.isEmpty()
    afterStages.size() == 3
    afterStages[0].context == stage.context + [
      serverGroupName: "servergroup-v001"
    ]
    afterStages[1].context == stage.context + [
      action: "scale_to_server_group",
      source: new ResizeStrategy.Source(null, null, null, null, "servergroup-v002", "test", "aws"),
      asgName: "servergroup-v001"
    ]
    afterStages[2].context == stage.context + [
      serverGroupName: "servergroup-v002"
    ]
  }
}
