/*
 * Copyright (c) 2018 Nike, inc.
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

package com.netflix.kayenta.canaryanalysis.orca.stage;

import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.kayenta.canaryanalysis.orca.task.GenerateCanaryAnalysisResultTask;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

/**
 * The finalizer stage that is intended to run regardless of whether or not there are upstream failures.
 * This stage will generate the aggregated results of the individual canary runs or bubble up errors that occurred.
 */
@Component
public class GenerateCanaryAnalysisResultStage implements StageDefinitionBuilder {

  public static final String STAGE_TYPE = "generateCanaryAnalysisResultStage";
  public static final String STAGE_DESCRIPTION = "Aggregates and evaluates the canary executions and generates the final results.";

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask("generateCanaryAnalysisResultTask", GenerateCanaryAnalysisResultTask.class);
  }

  @Nonnull
  @Override
  public String getType() {
    return STAGE_TYPE;
  }
}
