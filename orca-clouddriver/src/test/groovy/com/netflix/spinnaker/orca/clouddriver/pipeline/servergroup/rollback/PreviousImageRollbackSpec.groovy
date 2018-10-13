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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.clouddriver.FeaturesService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CloneServerGroupStage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage;

class PreviousImageRollbackSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def cloneServerGroupStage = new CloneServerGroupStage()
  def oortService = Mock(OortService)
  def featuresService = Mock(FeaturesService) {
    _ * areEntityTagsAvailable() >> { return true }
  }
  def retrySupport = Spy(RetrySupport) {
    _ * sleep(_) >> { /* do nothing */ }
  }

  @Subject
  def rollback = new PreviousImageRollback(
    objectMapper: objectMapper,

    cloneServerGroupStage: cloneServerGroupStage,
    oortService: oortService,
    featuresService: featuresService,
    retrySupport: retrySupport
  )

  def stage = stage {
    type = "rollbackServerGroup"
    context = [
      credentials  : "test",
      cloudProvider: "aws",
      region       : "us-west-1"
    ]
  }

  @Unroll
  def "should inject clone stage with #imageSource"() {
    given:
    rollback.rollbackServerGroupName = "application-v002"
    rollback.targetHealthyRollbackPercentage = 95
    rollback.imageName = imageName
    rollback.delayBeforeDisableSeconds = delay

    when:
    def allStages = rollback.buildStages(stage)
    def beforeStages = allStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
    def afterStages = allStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
    (imageName ? 0 : 1) * oortService.getEntityTags(*_) >> {
      // should only call `getEntityTags()` if an image was not explicitly provided to the rollback
      return [[
                tags: [
                  [
                    name : "spinnaker:metadata",
                    value: [
                      previousServerGroup: [
                        imageName: "previous_image_from_entity_tags",
                        imageId  : "previous_image_from_entity_tags_id",
                      ]
                    ]
                  ]
                ]
              ]]

    }

    beforeStages.isEmpty()
    afterStages*.type == [
      "cloneServerGroup",
    ]
    afterStages[0].context == [
      amiName                      : expectedImageName,
      imageId                      : expectedImageId,
      imageName                    : expectedImageName,
      strategy                     : "redblack",
      application                  : "application",
      stack                        : null,
      freeFormDetails              : null,
      targetHealthyDeployPercentage: 95,
      region                       : "us-west-1",
      credentials                  : "test",
      cloudProvider                : "aws",
      delayBeforeDisableSec : expectedDelay,
      source                       : [
        asgName          : "application-v002",
        serverGroupName  : "application-v002",
        account          : "test",
        region           : "us-west-1",
        cloudProvider    : "aws",
        useSourceCapacity: true
      ]
    ]

    where:
    imageName        | imageSource                                          | delay || expectedImageName                 || expectedImageId                      || expectedDelay
    "explicit_image" | "explicitly provided image"                          | null  || "explicit_image"                  || null                                 || 0
    null             | "image fetched from `spinnaker:metadata` entity tag" | 100   || "previous_image_from_entity_tags" || "previous_image_from_entity_tags_id" || 100
  }

  @Unroll
  def "should include interestingHealthProviderNames when present in parent stage context"() {
    given:
    rollback.imageName = "my_image"
    stage.context.putAll(additionalContext)

    when:
    def allStages = rollback.buildStages(stage)
    def rollbackStageContext = allStages[0].context

    then:
    rollbackStageContext.containsKey("interestingHealthProviderNames") == hasInterestingHealthProviderNames

    where:
    additionalContext                            || hasInterestingHealthProviderNames
    [:]                                          || false
    [interestingHealthProviderNames: null]       || true
    [interestingHealthProviderNames: ["Amazon"]] || true
    [interestingHealthProviderNames: []]         || true
  }

  @Unroll
  def "should include capacity when sourceServerGroupCapacitySnapshot present in parent stage context"() {
    given:
    rollback.imageName = "my_image"
    stage.context.putAll(additionalContext)

    when:
    def allStages = rollback.buildStages(stage)
    def rollbackStageContext = allStages[0].context

    then:
    rollbackStageContext.source.useSourceCapacity == expectedUseSourceCapacity
    rollbackStageContext.capacity == expectedCapacity


    where:
    additionalContext                                                  || expectedUseSourceCapacity || expectedCapacity
    [:]                                                                || true                      || null
    [sourceServerGroupCapacitySnapshot: [min: 0, max: 10, desired: 5]] || false                     || [min: 0, max: 10, desired: 5]
  }

  def "should raise exception if no image found"() {
    when:
    rollback.rollbackServerGroupName = "application-v002"
    rollback.buildStages(stage)

    then:
    def e = thrown(IllegalStateException)
    e.message == "Unable to determine rollback image (serverGroupName: application-v002)"
  }
}
