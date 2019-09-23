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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.ApplySourceServerGroupCapacityStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.CaptureSourceServerGroupCapacityStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.DisableServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.EnableServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import groovy.transform.TimedInterrupt
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

import javax.annotation.Nullable
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage

@Slf4j
class ExplicitRollback implements Rollback {
  String rollbackServerGroupName
  String restoreServerGroupName
  Integer targetHealthyRollbackPercentage
  Integer delayBeforeDisableSeconds
  Boolean disableOnly
  Boolean enableAndDisableOnly

  @JsonIgnore
  private final ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor()

  @Autowired
  @JsonIgnore
  EnableServerGroupStage enableServerGroupStage

  @Autowired
  @JsonIgnore
  DisableServerGroupStage disableServerGroupStage

  @Autowired
  @JsonIgnore
  ResizeServerGroupStage resizeServerGroupStage

  @Autowired
  @JsonIgnore
  CaptureSourceServerGroupCapacityStage captureSourceServerGroupCapacityStage

  @Autowired
  @JsonIgnore
  ApplySourceServerGroupCapacityStage applySourceServerGroupCapacityStage

  @Autowired
  @JsonIgnore
  WaitStage waitStage

  @Autowired
  @JsonIgnore
  OortHelper oortHelper

  @Autowired
  @JsonIgnore
  RetrySupport retrySupport

  @JsonIgnore
  @Override
  List<Stage> buildStages(Stage parentStage) {
    Map disableServerGroupContext = new HashMap(parentStage.context)
    disableServerGroupContext.serverGroupName = rollbackServerGroupName
    def disableServerGroupStage = newStage(
      parentStage.execution, disableServerGroupStage.type, "disable", disableServerGroupContext, parentStage, SyntheticStageOwner.STAGE_AFTER
    )

    if (disableOnly) {
      // no need to do anything but disable the newly deployed (and failing!) server group
      return [
        disableServerGroupStage
      ]
    }

    Map enableServerGroupContext = new HashMap(parentStage.context)
    enableServerGroupContext.targetHealthyDeployPercentage = targetHealthyRollbackPercentage
    enableServerGroupContext.serverGroupName = restoreServerGroupName
    def enableServerGroupStage = newStage(
      parentStage.execution, enableServerGroupStage.type, "enable", enableServerGroupContext, parentStage, SyntheticStageOwner.STAGE_AFTER
    )

    if (enableAndDisableOnly) {
      // ensure previous server group is 100% enabled before disabling the new server group
      return [
        enableServerGroupStage,
        disableServerGroupStage
      ]
    }

    def stages = []

    def resizeStage = buildResizeStage(parentStage)

    // only capture the source capacity (for unpinning) if we are going to resize
    // if the capacity has been previously captured (e.g. as part of a failed deploy), no need to do it again
    if (resizeStage != null && !parentStage.getContext().containsKey("sourceServerGroupCapacitySnapshot")) {
      stages << buildCaptureSourceServerGroupCapacityStage(parentStage, parentStage.mapTo(ResizeStrategy.Source))
    }

    stages << enableServerGroupStage

    if (resizeStage != null) {
      stages << resizeStage
    }

    if (delayBeforeDisableSeconds != null && delayBeforeDisableSeconds > 0) {
      def waitStage = newStage(
        parentStage.execution, waitStage.type, "waitBeforeDisable", [waitTime: delayBeforeDisableSeconds], parentStage, SyntheticStageOwner.STAGE_AFTER
      )
      stages << waitStage
    }

    stages << disableServerGroupStage

    // only restore the min if it was pinned, i.e. there was a resize
    if (resizeStage != null) {
      stages << buildApplySourceServerGroupCapacityStage(parentStage, parentStage.mapTo(ResizeStrategy.Source))
    }
    return stages
  }

  @Nullable TargetServerGroup lookupServerGroup(Stage parentStage, String serverGroupName) {
    def fromContext = parentStage.mapTo(ResizeStrategy.Source)

    try {
      // we use an executor+future to timebox how long we can spend in this remote call
      // because we are in a StartStage message and need to response quickly
      // this is a bit of a hack, the proper long term fix would be to encapsulate this remote call in a task
      executor.submit((Callable) {
        return oortHelper.getTargetServerGroup(
          fromContext.credentials,
          serverGroupName,
          fromContext.location
        )
      }).get(5, TimeUnit.SECONDS)
        .get()  // not sure what would cause the Optional to not be present but we would catch and log it
    } catch(Exception e) {
      log.error('Skipping resize stage because there was an error looking up {}', serverGroupName, e)
      return null
    }
  }

  @Nullable Stage buildResizeStage(Stage parentStage) {
    TargetServerGroup rollbackServerGroup = lookupServerGroup(parentStage, rollbackServerGroupName)
    if (!rollbackServerGroup) {
      return null
    }

    TargetServerGroup restoreServerGroup = lookupServerGroup(parentStage, restoreServerGroupName)
    if (!restoreServerGroup) {
      return null
    }

    // we don't want to scale down restoreServerGroupName if rollbackServerGroupName is smaller for some reason
    ResizeStrategy.Capacity newRestoreCapacity = [
      max: Math.max(rollbackServerGroup.capacity.max, restoreServerGroup.capacity.max),
      desired: Math.max(rollbackServerGroup.capacity.desired, restoreServerGroup.capacity.desired)
    ]

    // let's directly produce a capacity with a pinned min instead of relying on the resize stage
    newRestoreCapacity.min = newRestoreCapacity.desired

    ResizeStrategy.Capacity currentCapacity = restoreServerGroup.capacity
    if (currentCapacity == newRestoreCapacity) {
      log.info('Skipping resize stage because the current capacity of the restore server group {} would be unchanged ({})',
        restoreServerGroupName, newRestoreCapacity)
      return null
    }

    Map resizeServerGroupContext = new HashMap(parentStage.context) + [
      action                       : ResizeStrategy.ResizeAction.scale_exact.toString(),
      capacity                     : newRestoreCapacity.asMap(),
      asgName                      : restoreServerGroupName,
      serverGroupName              : restoreServerGroupName,
      targetLocation               : restoreServerGroup.getLocation(),
      account                      : restoreServerGroup.credentials,
      cloudProvider                : restoreServerGroup.cloudProvider,
      pinMinimumCapacity           : true,
      targetHealthyDeployPercentage: targetHealthyRollbackPercentage
    ]

    return newStage(parentStage.execution, resizeServerGroupStage.type,
      "Resize Server Group: ${restoreServerGroupName} to (min: ${newRestoreCapacity.min}, max: ${newRestoreCapacity.max}, desired: ${newRestoreCapacity.desired})",
      resizeServerGroupContext, parentStage, SyntheticStageOwner.STAGE_AFTER)
  }

  Stage buildCaptureSourceServerGroupCapacityStage(Stage parentStage,
                                                   ResizeStrategy.Source source) {
    Map captureSourceServerGroupCapacityContext = [
      useSourceCapacity: true,
      source           : [
        asgName        : rollbackServerGroupName,
        serverGroupName: rollbackServerGroupName,
        region         : source.region,
        account        : source.credentials,
        cloudProvider  : source.cloudProvider
      ]
    ]
    return newStage(
      parentStage.execution,
      captureSourceServerGroupCapacityStage.type,
      "Snapshot Source Server Group",
      captureSourceServerGroupCapacityContext,
      parentStage,
      SyntheticStageOwner.STAGE_AFTER
    )
  }

  Stage buildApplySourceServerGroupCapacityStage(Stage parentStage,
                                                 ResizeStrategy.Source source) {
    Map applySourceServerGroupCapacityContext = [
      credentials  : source.credentials,
      cloudProvider: source.cloudProvider,
      target       : [
        asgName        : restoreServerGroupName,
        serverGroupName: restoreServerGroupName,
        region         : source.region,
        account        : source.credentials,
        cloudProvider  : source.cloudProvider
      ]
    ]
    return newStage(
      parentStage.execution,
      applySourceServerGroupCapacityStage.type,
      "Restore Min Capacity From Snapshot",
      applySourceServerGroupCapacityContext,
      parentStage,
      SyntheticStageOwner.STAGE_AFTER
    )
  }
}
