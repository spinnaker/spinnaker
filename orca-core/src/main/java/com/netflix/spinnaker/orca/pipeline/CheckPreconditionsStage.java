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

package com.netflix.spinnaker.orca.pipeline;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.PreconditionTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage;
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

@Component
public class CheckPreconditionsStage implements StageDefinitionBuilder {

  public static final String PIPELINE_CONFIG_TYPE = "checkPreconditions";

  private final List<? extends PreconditionTask> preconditionTasks;

  @Autowired
  public CheckPreconditionsStage(List<? extends PreconditionTask> preconditionTasks) {
    this.preconditionTasks = preconditionTasks;
  }

  @Override
  public void taskGraph(
    @Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    if (!isTopLevelStage(stage)) {
      String preconditionType = stage.getContext().get("preconditionType").toString();
      if (preconditionType == null) {
        throw new IllegalStateException(format("no preconditionType specified for stage %s", stage.getId()));
      }
      Task preconditionTask = preconditionTasks
        .stream()
        .filter(it -> it.getPreconditionType().equals(preconditionType))
        .findFirst()
        .orElseThrow(() ->
          new IllegalStateException("no Precondition implementation for type $preconditionType")
        );
      builder.withTask("checkPrecondition", preconditionTask.getClass());
    }
  }

  @Override @Nonnull public List<Stage> parallelStages(@Nonnull Stage stage) {
    if (isTopLevelStage(stage)) {
      return parallelContexts(stage)
        .stream()
        .map(context ->
          newStage(
            stage.getExecution(),
            getType(),
            format("Check precondition (%s)", context.get("preconditionType")),
            context,
            stage,
            STAGE_BEFORE
          )
        )
        .collect(toList());
    } else {
      return emptyList();
    }
  }

  private boolean isTopLevelStage(Stage stage) {
    return stage.getParentStageId() == null;
  }

  @SuppressWarnings("unchecked")
  private Collection<Map<String, Object>> parallelContexts(Stage stage) {
    stage.resolveStrategyParams();
    Map<String, Object> baseContext = new HashMap<>(stage.getContext());
    List<Map<String, Object>> preconditions = (List<Map<String, Object>>) baseContext.remove("preconditions");
    return preconditions
      .stream()
      .map(preconditionConfig -> {
        Map<String, Object> context = new HashMap<>(baseContext);
        context.putAll(preconditionConfig);
        context.put("type", PIPELINE_CONFIG_TYPE);
        context.put("preconditionType", preconditionConfig.get("type"));

        context.putIfAbsent("context", new HashMap<String, Object>());
        ((Map<String, Object>) context.get("context")).putIfAbsent("cluster", baseContext.get("cluster"));
        ((Map<String, Object>) context.get("context")).putIfAbsent("regions", baseContext.get("regions"));
        ((Map<String, Object>) context.get("context")).putIfAbsent("credentials", baseContext.get("credentials"));
        ((Map<String, Object>) context.get("context")).putIfAbsent("zones", baseContext.get("zoned"));

        return context;
      })
      .collect(toList());
  }
}
