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

import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.kato.tasks.shrink.ShrinkClusterTask
import com.netflix.spinnaker.orca.kato.tasks.shrink.WaitForShrunkenClusterTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
class ShrinkClusterStage extends LinearStage {
  static final String MAYO_CONFIG_TYPE = "shrinkCluster"

  ShrinkClusterStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  protected List<Step> buildSteps(Stage stage) {
    if (!stage.context.containsKey("application")) {
      stage.context.application = stage.execution.application
    }
    [
        buildStep("shrink", ShrinkClusterTask),
        buildStep("monitorShrink", MonitorKatoTask),
        buildStep("waitForShrunkenCluster", WaitForShrunkenClusterTask)
    ]
  }
}
