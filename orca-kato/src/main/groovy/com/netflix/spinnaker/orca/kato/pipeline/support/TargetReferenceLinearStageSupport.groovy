/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.kato.pipeline.DetermineTargetReferenceStage
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired

abstract class TargetReferenceLinearStageSupport extends LinearStage {
  @Autowired
  ObjectMapper objectMapper

  @Autowired
  TargetReferenceSupport targetReferenceSupport

  @Autowired
  DetermineTargetReferenceStage determineTargetReferenceStage

  TargetReferenceLinearStageSupport(String name) {
    super(name)
  }

  void composeTargets(Stage stage) {
    if (targetReferenceSupport.isDynamicallyBound(stage)) {
      // We only want to determine the target ASG once per stage, so only inject if this is the root stage, i.e.
      // the one the user configured
      // This may become a bad assumption, or a limiting one, in that we cannot inject a dynamic stage ourselves
      // as part of some other stage that is not itself injecting a determineTargetReferences stage
      if (!stage.parentStageId) {
        injectBefore(stage, "determineTargetReferences", determineTargetReferenceStage, stage.context)
      }
      return
    }

    def targets = targetReferenceSupport.getTargetAsgReferences(stage)
    if (!targets) {
      throw new TargetReferenceNotFoundException("Could not ascertain targets! " +
        "${objectMapper.writeValueAsString(stage.context)}")
    }

    Map<String, Map<String, Object>> descriptions = [:]
    for (target in targets) {
      def region = target.region
      def asg = target.asg

      def description = new HashMap(stage.context)
      // for dynamically configured stages, the ASG will not be present until
      // the determineTargetReferences stage completes
      if (asg) {
        if (descriptions.containsKey(asg.name)) {
          ((List<String>)descriptions.get(asg.name).regions) << region
        } else {
          description.asgName = asg.name
          description.regions = [region]
          descriptions[asg.name as String] = description
        }
      }
    }

    def descriptionList = descriptions.values().toList()
    def first = descriptionList.remove(0)
    stage.context.putAll(first)

    if (descriptionList.size()) {
      for (description in descriptionList) {
        injectAfter(stage, this.type, this, description)
      }
    }
  }
}
