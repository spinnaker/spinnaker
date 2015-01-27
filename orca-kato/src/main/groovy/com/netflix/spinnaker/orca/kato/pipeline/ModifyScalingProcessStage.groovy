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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceLinearStageSupport
import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.kato.tasks.scalingprocess.ResumeScalingProcessTask
import com.netflix.spinnaker.orca.kato.tasks.scalingprocess.SuspendScalingProcessTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
@CompileStatic
class ModifyScalingProcessStage extends TargetReferenceLinearStageSupport {

  static final String MAYO_CONFIG_TYPE = "modifyScalingProcess"

  ModifyScalingProcessStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  protected List<Step> buildSteps(Stage stage) {
    composeTargets(stage)

    def data = stage.mapTo(StageData)
    switch (data.action) {
      case StageAction.suspend:
        return [
          buildStep(stage, "suspend", SuspendScalingProcessTask),
          buildStep(stage, "monitor", MonitorKatoTask)
        ]
      case StageAction.resume:
        return [
          buildStep(stage, "resume", ResumeScalingProcessTask),
          buildStep(stage, "monitor", MonitorKatoTask)
        ]
    }
    throw new RuntimeException("No action specified!")
  }

  enum StageAction {
    suspend, resume
  }

  static class StageData {
    StageAction action
  }
}
