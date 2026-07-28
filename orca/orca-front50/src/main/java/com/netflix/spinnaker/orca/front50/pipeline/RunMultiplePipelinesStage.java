/*
 * Copyright 2022 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.front50.pipeline;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.tasks.MonitorMultiplePipelinesTask;
import com.netflix.spinnaker.orca.front50.tasks.ParsePipelinesYamlTask;
import com.netflix.spinnaker.orca.front50.tasks.RunMultiplePipelinesTask;
import com.netflix.spinnaker.orca.front50.tasks.SaveOutputsForDetailsTask;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

/**
 * Triggers a set of child pipelines described by a YAML document, honoring their declared
 * dependency ordering. Originally contributed as the Armory.RunMultiplePipelines plugin
 * (https://github.com/armory-plugins/spinnaker-multiple-pipelines).
 */
@Component
public class RunMultiplePipelinesStage implements StageDefinitionBuilder {

  public static final String PIPELINE_CONFIG_TYPE = "runMultiplePipelines";

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask("parsePipelinesYamlTask", ParsePipelinesYamlTask.class);
    builder.withLoop(
        sub -> {
          sub.withTask("runMultiplePipelines", RunMultiplePipelinesTask.class);
          sub.withTask("monitorMultiplePipelinesTask", MonitorMultiplePipelinesTask.class);
        });
    builder.withTask("saveOutputsForDetailsTask", SaveOutputsForDetailsTask.class);
  }
}
