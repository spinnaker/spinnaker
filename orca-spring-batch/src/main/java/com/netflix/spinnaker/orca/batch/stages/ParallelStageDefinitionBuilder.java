/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.batch.stages;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.batch.StageBuilderProvider;
import com.netflix.spinnaker.orca.pipeline.BranchingStageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.ParallelStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.batch.core.Step;

@Deprecated
public class ParallelStageDefinitionBuilder extends ParallelStage {
  protected final BranchingStageDefinitionBuilder delegate;
  protected final StageBuilderProvider stageBuilderProvider;

  public ParallelStageDefinitionBuilder(BranchingStageDefinitionBuilder delegate,
                                        StageBuilderProvider stageBuilderProvider) {
    super(delegate.getType());
    this.delegate = delegate;
    this.stageBuilderProvider = stageBuilderProvider;
  }

  @Override
  public String parallelStageName(Stage stage, boolean hasParallelFlows) {
    return delegate.parallelStageName(stage, hasParallelFlows);
  }

  @Override
  public Task completeParallel() {
    try {
      return delegate.completeParallelTask();
    } catch (Exception e) {
      throw new RuntimeException("Unable to instantiate task", e);
    }
  }

  @Override
  public List<Map<String, Object>> parallelContexts(Stage stage) {
    return new ArrayList<>(delegate.parallelContexts(stage));
  }

  protected List<Step> buildParallelContextSteps(Stage stage) {
    return LinearStageDefinitionBuilder.buildSteps(
      delegate,
      this,
      stage
    );
  }
}
