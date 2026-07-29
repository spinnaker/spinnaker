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

package com.netflix.spinnaker.orca.front50.tasks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.graph.MutableGraph;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.multiplepipelines.App;
import com.netflix.spinnaker.orca.front50.multiplepipelines.Apps;
import com.netflix.spinnaker.orca.front50.multiplepipelines.RunMultiplePipelinesContext;
import com.netflix.spinnaker.orca.front50.multiplepipelines.UtilityHelper;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ParsePipelinesYamlTask implements Task {

  private final Logger logger = LoggerFactory.getLogger(ParsePipelinesYamlTask.class);
  private final ObjectMapper objectMapper;

  public ParsePipelinesYamlTask(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    logger.info("starting ParsePipelinesYamlTask");
    RunMultiplePipelinesContext context = stage.mapTo(RunMultiplePipelinesContext.class);
    UtilityHelper utilityHelper = new UtilityHelper();

    Apps apps = utilityHelper.getApps(context, objectMapper);
    Map<String, App> mapOfApps =
        objectMapper.convertValue(apps.getApps(), new TypeReference<>() {});

    if (context.isCheckDuplicated() && checkDuplicatedExecution(mapOfApps)) {
      stage.appendErrorMessage(
          "Detected two or more duplicated arguments and calling the same child_pipeline. "
              + "You would have the same execution");
      stage.getContext().put("mapOfApps", mapOfApps);
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(stage.getContext()).build();
    }

    List<App> initialExecutions = new LinkedList<>();
    MutableGraph<App> graph = utilityHelper.getGraphOfApps(mapOfApps, initialExecutions);

    List<List<App>> orderOfExecutions = new LinkedList<>();
    orderOfExecutions.add(initialExecutions);
    utilityHelper.addLevels(orderOfExecutions, graph, new LinkedList<>(), 0);

    logger.info("Map of apps, detected returning success " + mapOfApps.size() + " size");
    stage.getContext().put("orderOfExecutions", orderOfExecutions);
    stage.getContext().put("levelNumber", 0);

    // remove yamlConfig to reduce pipeline body blob being stored
    stage.getContext().remove("yamlConfig");
    stage.getContext().put("ignoreUncompleted", context.isIgnoreUncompleted());
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(stage.getContext()).build();
  }

  private boolean checkDuplicatedExecution(Map<String, App> mapOfApps) {
    LinkedList<App> appList = new LinkedList<>(mapOfApps.values());
    if (appList.size() != appList.stream().distinct().count()) {
      logger.warn(
          "Detected "
              + (appList.size() + 1 - appList.stream().distinct().count())
              + " duplicated arguments and calling the same child_pipeline");
      return true;
    }
    return false;
  }
}
