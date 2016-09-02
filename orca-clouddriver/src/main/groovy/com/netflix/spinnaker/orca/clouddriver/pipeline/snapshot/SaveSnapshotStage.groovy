/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.snapshot

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.snapshot.SaveSnapshotTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.apache.commons.jxpath.ri.compiler.Step
import org.springframework.stereotype.Component

@Component
class SaveSnapshotStage extends LinearStage {

  public static final String PIPELINE_CONFIG_TYPE = "saveSnapshot"

  SaveSnapshotStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    [
      buildStep(stage, "saveSnapshot", SaveSnapshotTask),
      buildStep(stage, "monitorSnapshot", MonitorKatoTask)
    ]
  }
}
