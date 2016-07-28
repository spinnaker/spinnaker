/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline;

import com.netflix.spinnaker.orca.clouddriver.tasks.pipeline.MigratePipelineClustersTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.pipeline.UpdateMigratedPipelineTask;
import com.netflix.spinnaker.orca.pipeline.LinearStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.batch.core.Step;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MigratePipelineStage extends LinearStage {

  public static final String PIPELINE_CONFIG_TYPE = "migratePipeline";

  MigratePipelineStage() {
    super(PIPELINE_CONFIG_TYPE);
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    List<Step> steps = new ArrayList<>();
    steps.add(buildStep(stage, "migratePipelineClusters", MigratePipelineClustersTask.class));
    steps.add(buildStep(stage, "monitorMigration", MonitorKatoTask.class));
    if (!Boolean.TRUE.equals(stage.getContext().get("dryRun"))) {
      steps.add(buildStep(stage, "updateMigratedPipeline", UpdateMigratedPipelineTask.class));
    }
    return steps;
  }

}
