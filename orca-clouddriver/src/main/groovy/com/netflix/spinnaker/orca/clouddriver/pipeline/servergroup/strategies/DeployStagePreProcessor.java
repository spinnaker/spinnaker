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

import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Supports generic modification of a Deploy stage.
 *
 * Common use-cases:
 * - injecting synthetic stages before/after a deploy
 * - injecting steps (will be added prior to the deploy stage steps)
 */
public interface DeployStagePreProcessor {
  boolean supports(Stage stage);

  default List<StepDefinition> additionalSteps() {
    return Collections.emptyList();
  }

  default List<StageDefinition> beforeStageDefinitions() {
    return Collections.emptyList();
  }

  default List<StageDefinition> afterStageDefinitions() {
    return Collections.emptyList();
  }

  class StepDefinition {
    String name;
    Class taskClass;
  }

  class StageDefinition {
    String name;
    StageDefinitionBuilder stageDefinitionBuilder;
    Map context;
  }
}
