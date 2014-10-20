/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.redblack

import com.netflix.spinnaker.orca.kato.pipeline.CopyLastAsgStage
import com.netflix.spinnaker.orca.kato.pipeline.DisableAsgStage
import com.netflix.spinnaker.orca.kato.tasks.PreconfigureRedBlackStep
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.Stage
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class RedBlackStageBuilder extends LinearStage {
  private static final String MAYO_NAME = "redBlack"

  @Autowired
  CopyLastAsgStage copyLastAsgStage

  @Autowired
  DisableAsgStage disableAsgStage

  RedBlackStageBuilder() {
    super(MAYO_NAME)
  }

  List<Step> buildSteps(Stage stage) {
    def step1 = buildStep("preconfigureRedBlackStage",PreconfigureRedBlackStep)
    def middleSteps = copyLastAsgStage.buildSteps(stage)
    def closingSteps = disableAsgStage.buildSteps(stage)
    [step1, middleSteps, closingSteps].flatten().collect{
      it.name = it.name.replace(copyLastAsgStage.MAYO_CONFIG_TYPE, MAYO_NAME)
        .replace(disableAsgStage.MAYO_CONFIG_TYPE, MAYO_NAME)
      it
    }
  }

}
