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
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired

abstract class TargetReferenceLinearStageSupport extends LinearStage {
  @Autowired
  ObjectMapper objectMapper

  @Autowired
  TargetReferenceSupport targetReferenceSupport

  TargetReferenceLinearStageSupport(String name) {
    super(name)
  }

  void composeTargets(Stage stage) {
    def targets = targetReferenceSupport.getTargetAsgReferences(stage)
    if (!targets) {
      throw new RuntimeException("Could not ascertain targets! ${objectMapper.writeValueAsString(stage.context)}")
    }

    Map<String, Map<String, Object>> descriptions = [:]
    for (target in targets) {
      def region = target.region
      def asg = target.asg

      def description = new HashMap(stage.context)

      if (descriptions.containsKey(asg.name)) {
        ((List<String>)descriptions.get(asg.name).regions) << region
      } else {
        description.asgName = asg.name
        description.regions = [region]
        descriptions[asg.name as String] = description
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
