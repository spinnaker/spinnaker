/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Supports generic modification of a Deploy stage.
 *
 * <p>Common use-cases: - injecting synthetic stages before/after a deploy - injecting steps (will
 * be added prior to the deploy stage steps)
 */
public interface DeployStagePreProcessor {
  boolean supports(StageExecution stage);

  default List<StepDefinition> additionalSteps(StageExecution stage) {
    return Collections.emptyList();
  }

  default List<StageDefinition> beforeStageDefinitions(StageExecution stage) {
    return Collections.emptyList();
  }

  default List<StageDefinition> afterStageDefinitions(StageExecution stage) {
    return Collections.emptyList();
  }

  default List<StageDefinition> onFailureStageDefinitions(StageExecution stage) {
    return Collections.emptyList();
  }

  class StepDefinition {
    String name;
    Class taskClass;

    public StepDefinition() {}

    public StepDefinition(String name, Class taskClass) {
      this.name = name;
      this.taskClass = taskClass;
    }

    public Class getTaskClass() {
      return taskClass;
    }
  }

  class StageDefinition {
    public String name;
    public StageDefinitionBuilder stageDefinitionBuilder;
    public Map context;

    public StageDefinition() {}

    public StageDefinition(
        String name, StageDefinitionBuilder stageDefinitionBuilder, Map context) {
      this.name = name;
      this.stageDefinitionBuilder = stageDefinitionBuilder;
      this.context = context;
    }
  }
}
