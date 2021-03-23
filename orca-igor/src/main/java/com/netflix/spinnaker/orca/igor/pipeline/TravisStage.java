/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.igor.pipeline;

import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.igor.tasks.StopJenkinsJobTask;
import java.util.Map;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
public class TravisStage extends CIStage {
  public TravisStage(StopJenkinsJobTask stopJenkinsJobTask) {
    super(stopJenkinsJobTask);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    Map<String, String> parameters = (Map<String, String>) stage.getContext().get("parameters");
    parameters.putIfAbsent("travis.buildMessage", createBuildMessage(stage));
    stage.getContext().put("parameters", parameters);
    super.taskGraph(stage, builder);
  }

  private String createBuildMessage(StageExecution stage) {
    PipelineExecution execution = stage.getExecution();
    return String.format(
        "Application: '%s', pipeline: '%s', execution: '%s'",
        execution.getApplication(), execution.getName(), execution.getId());
  }
}
