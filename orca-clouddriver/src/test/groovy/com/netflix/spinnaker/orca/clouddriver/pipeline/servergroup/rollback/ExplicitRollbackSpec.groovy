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

import com.netflix.spinnaker.kork.exceptions.SpinnakerException
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.ApplySourceServerGroupCapacityStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.CaptureSourceServerGroupCapacityStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.DisableServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.EnableServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage;

class ExplicitRollbackSpec extends Specification {
  public static final String rollbackServerGroupName = "servergroup-v002"
  public static final String restoreServerGroupName = "servergroup-v001"
  def enableServerGroupStage = new EnableServerGroupStage()
  def disableServerGroupStage = new DisableServerGroupStage()
  def resizeServerGroupStage = new ResizeServerGroupStage()
  def captureSourceServerGroupCapacityStage = new CaptureSourceServerGroupCapacityStage()
  def applySourceServerGroupCapacityStage = new ApplySourceServerGroupCapacityStage()
  def waitStage = new WaitStage()
  CloudDriverService cloudDriverService = Mock()

  def stage = stage {
    type = "rollbackServerGroup"
    context = [
      credentials                  : "test",
      cloudProvider                : "aws",
      "region"                     : "us-west-1"
    ]
  }

  @Subject
  def rollback = new ExplicitRollback(
    enableServerGroupStage: enableServerGroupStage,
    disableServerGroupStage: disableServerGroupStage,
    resizeServerGroupStage: resizeServerGroupStage,
    captureSourceServerGroupCapacityStage: captureSourceServerGroupCapacityStage,
    applySourceServerGroupCapacityStage: applySourceServerGroupCapacityStage,
    waitStage: waitStage,
    cloudDriverService: cloudDriverService
  )

  def setup() {
    rollback.rollbackServerGroupName = rollbackServerGroupName
    rollback.restoreServerGroupName = restoreServerGroupName
    rollback.targetHealthyRollbackPercentage = 95
  }

  def serverGroup(String name, int desired, Integer min = null, Integer max = null) {
    return new TargetServerGroup([
      serverGroupName: name,
      capacity: [
          min: min == null ? desired : min,
          max: max == null ? desired : max,
          desired: desired
      ],
      region: "us-west-1",
      credentials: "test",
      cloudProvider: "aws",
    ])
  }

  @Unroll
  def "should not have a resize stage if it would result in restoreServerGroup being scaled down"() {
    when:
    def allStages = rollback.buildStages(stage)
    def afterStages = allStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
    1 * cloudDriverService.getTargetServerGroup(_, rollbackServerGroupName, _) >> Optional.of(serverGroup(rollbackServerGroupName, 2))
    1 * cloudDriverService.getTargetServerGroup(_, restoreServerGroupName, _) >> Optional.of(serverGroup(restoreServerGroupName, restoreServerGroupCapacity))

    afterStages*.type == expectedAfterStageTypes

    where:
    restoreServerGroupCapacity || expectedAfterStageTypes
    1                          || ["captureSourceServerGroupCapacity", "enableServerGroup", "resizeServerGroup", "disableServerGroup", "applySourceServerGroupCapacity"]
    2                          || ["enableServerGroup", "disableServerGroup"]
    3                          || ["enableServerGroup", "disableServerGroup"]
  }

  @Unroll
  def "generated resize context for restoreServerGroup should #description"() {
    when:
    def allStages = rollback.buildStages(stage)
    def afterStages = allStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
    1 * cloudDriverService.getTargetServerGroup(_, rollbackServerGroupName, _) >> Optional.of(serverGroup(rollbackServerGroupName, 2, 0, 4))
    1 * cloudDriverService.getTargetServerGroup(_, restoreServerGroupName, _) >> Optional.of(serverGroup(restoreServerGroupName, restoreDesired, restoreMin, restoreMax))

    afterStages[2].context.capacity == expectedCapacity

    where:
    restoreDesired | restoreMin | restoreMax || expectedCapacity             || description
    2              | 0          | 4          || [desired: 2, min: 2, max: 4] || 'pin the min'
    1              | 0          | 4          || [desired: 2, min: 2, max: 4] || 'scale up to rollbackServerGroup.desired'
    3              | 0          | 4          || [desired: 3, min: 3, max: 4] || 'not scale down to 2, just pin the min'
    2              | 0          | 3          || [desired: 2, min: 2, max: 4] || 'inherit max from rollbackServerGroup'
    2              | 0          | 5          || [desired: 2, min: 2, max: 5] || 'preserve its own max'
  }

  def "should inject enable, resize and disable stages corresponding to the server group being restored and rollbacked"() {
    when:
    def allStages = rollback.buildStages(stage)
    def beforeStages = allStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
    def afterStages = allStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
    1 * cloudDriverService.getTargetServerGroup(_, rollbackServerGroupName, _) >> Optional.of(serverGroup(rollbackServerGroupName, 2))
    1 * cloudDriverService.getTargetServerGroup(_, restoreServerGroupName, _) >> Optional.of(serverGroup(restoreServerGroupName, 1))

    beforeStages.isEmpty()
    afterStages*.type == [
      "captureSourceServerGroupCapacity",  // spinnaker/issues/4895, capture capacity snapshot first
      "enableServerGroup",
      "resizeServerGroup",
      "disableServerGroup",
      "applySourceServerGroupCapacity"
    ]
    afterStages[0].context == [
      source           : [
        asgName        : rollbackServerGroupName,
        serverGroupName: rollbackServerGroupName,
        region         : "us-west-1",
        account        : "test",
        cloudProvider  : "aws"
      ],
      useSourceCapacity: true
    ]
    afterStages[1].context == stage.context + [
      serverGroupName: restoreServerGroupName,
      targetHealthyDeployPercentage: 95
    ]
    afterStages[2].context == stage.context + [
      action            : "scale_exact",
      asgName           : restoreServerGroupName,
      serverGroupName   : restoreServerGroupName,
      pinMinimumCapacity: true,
      targetHealthyDeployPercentage: 95,
      targetLocation: Location.region("us-west-1"),
      capacity: [max:2, desired:2, min:2],
      account: "test"
    ]
    afterStages[3].context == stage.context + [
      serverGroupName: rollbackServerGroupName
    ]
    afterStages[4].context == [
      cloudProvider: "aws",
      target       : [
        asgName        : restoreServerGroupName,
        serverGroupName: restoreServerGroupName,
        region         : "us-west-1",
        account        : "test",
        cloudProvider  : "aws"
      ],
      credentials: "test"
    ]
  }

  @Unroll
  def "waitStageExpected=#waitStageExpected before disable stage when delayBeforeDisableSeconds=#delayBeforeDisableSeconds"() {
    given:
    rollback.delayBeforeDisableSeconds = delayBeforeDisableSeconds

    when:
    def allStages = rollback.buildStages(stage)
    def afterStages = allStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
    1 * cloudDriverService.getTargetServerGroup(_, rollbackServerGroupName, _) >> Optional.of(serverGroup(rollbackServerGroupName, 2))
    1 * cloudDriverService.getTargetServerGroup(_, restoreServerGroupName, _) >> Optional.of(serverGroup(restoreServerGroupName, 1))

    afterStages*.type.contains("wait") == waitStageExpected
    afterStages*.type.indexOf("wait") < afterStages*.type.indexOf("disableServerGroup")

    where:
    delayBeforeDisableSeconds || waitStageExpected
    null                      || false
    0                         || false
    1                         || true
  }

  def "server group lookups failures are fatal"() {
    when:
    rollback.buildStages(stage)

    then:
    1 * cloudDriverService.getTargetServerGroup(_, rollbackServerGroupName, _) >> { throw new Exception(":(") }
    thrown(SpinnakerException)
  }
}
