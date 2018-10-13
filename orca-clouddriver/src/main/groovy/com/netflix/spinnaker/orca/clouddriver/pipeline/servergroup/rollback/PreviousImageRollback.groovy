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
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.clouddriver.FeaturesService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CloneServerGroupStage
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.springframework.beans.factory.annotation.Autowired
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage

class PreviousImageRollback implements Rollback {
  String rollbackServerGroupName
  String imageName
  String imageId
  Integer targetHealthyRollbackPercentage
  Integer delayBeforeDisableSeconds

  @Autowired
  @JsonIgnore
  CloneServerGroupStage cloneServerGroupStage

  @Autowired
  @JsonIgnore
  ObjectMapper objectMapper

  @Autowired
  @JsonIgnore
  OortService oortService

  @Autowired
  @JsonIgnore
  FeaturesService featuresService

  @Autowired
  @JsonIgnore
  RetrySupport retrySupport

  @Override
  List<Stage> buildStages(Stage parentStage) {
    def previousImageRollbackSupport = new PreviousImageRollbackSupport(objectMapper, oortService, featuresService, retrySupport)
    def stages = []

    def parentStageContext = parentStage.context

    def imageName = this.imageName
    def imageId = this.imageId

    if (!imageName) {
      def imageDetails = previousImageRollbackSupport.getImageDetailsFromEntityTags(
        parentStageContext.cloudProvider as String,
        parentStageContext.credentials as String,
        parentStageContext.region as String,
        rollbackServerGroupName
      )

      imageName = imageDetails?.imageName
      imageId = imageDetails?.imageId ?: imageId
    }

    if (!imageName) {
      throw new IllegalStateException("Unable to determine rollback image (serverGroupName: ${rollbackServerGroupName})")
    }

    def names = Names.parseName(rollbackServerGroupName)

    Map cloneServerGroupContext = [
      targetHealthyDeployPercentage: targetHealthyRollbackPercentage,
      imageId                      : imageId,
      imageName                    : imageName,
      amiName                      : imageName,
      strategy                     : "redblack",
      application                  : parentStageContext.moniker?.app ?: names.app,
      stack                        : parentStageContext.moniker?.stack ?: names.stack,
      freeFormDetails              : parentStageContext.moniker?.detail ?: names.detail,
      region                       : parentStageContext.region,
      credentials                  : parentStageContext.credentials,
      cloudProvider                : parentStageContext.cloudProvider,
      delayBeforeDisableSec        : delayBeforeDisableSeconds ?: 0,
      source                       : [
        asgName          : rollbackServerGroupName,
        serverGroupName  : rollbackServerGroupName,
        account          : parentStageContext.credentials,
        region           : parentStageContext.region,
        cloudProvider    : parentStageContext.cloudProvider,
        useSourceCapacity: true
      ]
    ]

    if (parentStageContext.containsKey("sourceServerGroupCapacitySnapshot")) {
      cloneServerGroupContext.source.useSourceCapacity = false
      cloneServerGroupContext.capacity = parentStageContext.sourceServerGroupCapacitySnapshot
    }

    if (parentStageContext.containsKey("interestingHealthProviderNames")) {
      cloneServerGroupContext.interestingHealthProviderNames = parentStageContext.interestingHealthProviderNames
    }

    stages << newStage(
      parentStage.execution, cloneServerGroupStage.type, "clone", cloneServerGroupContext, parentStage, SyntheticStageOwner.STAGE_AFTER
    )

    return stages
  }
}
