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

package com.netflix.spinnaker.orca.batch.stages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import com.netflix.spinnaker.orca.batch.ExecutionListenerProvider;
import com.netflix.spinnaker.orca.batch.StageBuilder;
import com.netflix.spinnaker.orca.batch.StageBuilderProvider;
import com.netflix.spinnaker.orca.pipeline.BranchingStageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import org.springframework.context.ApplicationContext;

@Deprecated
public class SpringBatchStageBuilderProvider implements StageBuilderProvider {
  private final ApplicationContext applicationContext;
  private final Collection<StageBuilder> stageBuilders = new ArrayList<>();
  private final ExecutionListenerProvider executionListenerProvider;

  public SpringBatchStageBuilderProvider(ApplicationContext applicationContext,
                                         Collection<StageBuilder> stageBuilders,
                                         Collection<StageDefinitionBuilder> stageDefinitionBuilders,
                                         ExecutionListenerProvider executionListenerProvider) {
    this.applicationContext = applicationContext;
    this.executionListenerProvider = executionListenerProvider;
    this.stageBuilders.addAll(stageBuilders);
    this.stageBuilders.addAll(
      stageDefinitionBuilders
      .stream()
      .map(s -> {
        if (s instanceof BranchingStageDefinitionBuilder) {
          BranchingStageDefinitionBuilder branchingStageDefinitionBuilder = (BranchingStageDefinitionBuilder) s;
          if (s.getType().equals("deploy") || s.getType().equals("deployCanary")) {
            return new ParallelDeployStageDefinitionBuilder(branchingStageDefinitionBuilder, this);
          }
          return new ParallelStageDefinitionBuilder(branchingStageDefinitionBuilder, this);
        } else if (s.getType().equals("rollingPush")) {
          return new RollingPushStageDefinitionBuilder(s, executionListenerProvider);
        }
        return new LinearStageDefinitionBuilder(s, this);
      })
      .collect(Collectors.toList())
    );
  }

  @PostConstruct
  void init() {
    stageBuilders.forEach(stageBuilder -> {
      applicationContext.getAutowireCapableBeanFactory().autowireBean(stageBuilder);
      stageBuilder.setApplicationContext(applicationContext);
    });
  }

  @Override
  public Collection<StageBuilder> all() {
    return stageBuilders;
  }

  @Override
  public StageBuilder wrap(StageDefinitionBuilder stageDefinitionBuilder) {
    if (stageDefinitionBuilder == null) {
      return null;
    }

    StageBuilder stageBuilder = new LinearStageDefinitionBuilder(stageDefinitionBuilder, this);
    applicationContext.getAutowireCapableBeanFactory().autowireBean(stageBuilder);
    stageBuilder.setApplicationContext(applicationContext);

    return stageBuilder;
  }
}
