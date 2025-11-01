/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.UpsertDisruptionBudgetTask;
import jakarta.validation.constraints.NotNull;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
public class UpsertDisruptionBudgetStage implements StageDefinitionBuilder {
  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull @NotNull TaskNode.Builder builder) {
    builder
        .withTask("upsertDisruptionBudget", UpsertDisruptionBudgetTask.class)
        .withTask("monitorUpsertDisruptionBudget", MonitorKatoTask.class)
        .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask.class);
  }
}
