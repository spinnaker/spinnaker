/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.orca.clouddriver.pipeline.job;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.util.Map;

/**
 * Allows customization of the run job stage by cloud providers without modifying the class itself.
 */
@NonnullByDefault
public interface RunJobStageDecorator {

  /** Whether or not this decorator supports the provided {@code cloudProvider}. */
  boolean supports(String cloudProvider);

  /** Allows modification of the task graph immediately after the runJob task. */
  void afterRunJobTaskGraph(StageExecution stageExecution, TaskNode.Builder builder);

  /**
   * Allows modification of the destroy job stage context (cancellation behavior) before the stage
   * is executed.
   */
  void modifyDestroyJobContext(RunJobStageContext context, Map<String, Object> destroyContext);
}
