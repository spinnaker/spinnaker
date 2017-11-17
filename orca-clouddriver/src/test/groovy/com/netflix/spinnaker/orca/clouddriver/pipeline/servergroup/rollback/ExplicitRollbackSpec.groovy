/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.rollback

import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.ApplySourceServerGroupCapacityStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.CaptureSourceServerGroupCapacityStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.DisableServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.EnableServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage;

class ExplicitRollbackSpec extends Specification {
  @Shared
  def enableServerGroupStage = new EnableServerGroupStage()

  @Shared
  def disableServerGroupStage = new DisableServerGroupStage()

  @Shared
  def resizeServerGroupStage = new ResizeServerGroupStage()

  @Shared
  def captureSourceServerGroupCapacityStage = new CaptureSourceServerGroupCapacityStage()

  @Shared
  def applySourceServerGroupCapacityStage = new ApplySourceServerGroupCapacityStage()

  @Subject
  def rollback = new ExplicitRollback(
    enableServerGroupStage: enableServerGroupStage,
    disableServerGroupStage: disableServerGroupStage,
    resizeServerGroupStage: resizeServerGroupStage,
    captureSourceServerGroupCapacityStage: captureSourceServerGroupCapacityStage,
    applySourceServerGroupCapacityStage: applySourceServerGroupCapacityStage
  )

  def "should inject enable, resize and disable stages corresponding to the server group being restored and rollbacked"() {
    given:
    rollback.rollbackServerGroupName = "servergroup-v002"
    rollback.restoreServerGroupName = "servergroup-v001"
    rollback.targetHealthyRollbackPercentage = 95

    def stage = stage {
      type = "rollbackServerGroup"
      context = [
        credentials                  : "test",
        cloudProvider                : "aws",
        "region"                     : "us-west-1"
      ]
    }

    when:
    def allStages = rollback.buildStages(stage)
    def beforeStages = allStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
    def afterStages = allStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
    beforeStages.isEmpty()
    afterStages*.type == [
      "enableServerGroup",
      "captureSourceServerGroupCapacity",
      "resizeServerGroup",
      "disableServerGroup",
      "applySourceServerGroupCapacity"
    ]
    afterStages[0].context == stage.context + [
      serverGroupName: "servergroup-v001",
      targetHealthyDeployPercentage: 95
    ]
    afterStages[1].context == [
      source           : [
        asgName        : "servergroup-v002",
        serverGroupName: "servergroup-v002",
        region         : "us-west-1",
        account        : "test",
        cloudProvider  : "aws"
      ],
      useSourceCapacity: true
    ]
    afterStages[2].context == stage.context + [
      action            : "scale_to_server_group",
      source            : new ResizeStrategy.Source(null, null, "us-west-1", null, "servergroup-v002", "test", "aws"),
      asgName           : "servergroup-v001",
      pinMinimumCapacity: true,
      targetHealthyDeployPercentage: 95
    ]
    afterStages[3].context == stage.context + [
      serverGroupName: "servergroup-v002"
    ]
    afterStages[4].context == [
      cloudProvider: "aws",
      target       : [
        asgName        : "servergroup-v001",
        serverGroupName: "servergroup-v001",
        region         : "us-west-1",
        account        : "test",
        cloudProvider  : "aws"
      ],
      credentials: "test"
    ]
  }
}
