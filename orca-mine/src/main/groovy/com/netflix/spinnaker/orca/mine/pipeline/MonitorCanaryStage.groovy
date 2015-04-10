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

import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.mine.tasks.CleanupCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.CompleteCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.MonitorCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.RegisterCanaryTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class MonitorCanaryStage extends LinearStage {
  public static final String MAYO_CONFIG_TYPE = "canary"

  @Autowired CanaryStage canaryStage

  MonitorCanaryStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    injectBefore(stage, "Deploy Canary", canaryStage, stage.context)
    [
      buildStep(stage, "registerCanary", RegisterCanaryTask),
      buildStep(stage, "monitorCanary", MonitorCanaryTask),
      buildStep(stage, "cleanupCanary", CleanupCanaryTask),
      buildStep(stage, "monitorCleanup", MonitorKatoTask),
      buildStep(stage, "completeCanary", CompleteCanaryTask)
    ]
  }
}
