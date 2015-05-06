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

package com.netflix.spinnaker.orca.mine.pipeline

import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class CanaryStage extends LinearStage {
  public static final String MAYO_CONFIG_TYPE = "canary"

  @Autowired DeployCanaryStage deployCanaryStage
  @Autowired MonitorCanaryStage monitorCanaryStage

  CanaryStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    Map canaryStageId = [canaryStageId: stage.id]

    Map deployContext = canaryStageId + stage.context
    Map monitorContext = canaryStageId + [scaleUp: stage.context.scaleUp ?: [:]]

    injectAfter(stage, "Deploy Canary", deployCanaryStage, deployContext)
    injectAfter(stage, "Monitor Canary", monitorCanaryStage, monitorContext)
    []
  }
}
