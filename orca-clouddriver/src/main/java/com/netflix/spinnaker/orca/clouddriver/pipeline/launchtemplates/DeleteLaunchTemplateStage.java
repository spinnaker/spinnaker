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

package com.netflix.spinnaker.orca.clouddriver.pipeline.launchtemplates;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.launchtemplates.DeleteLaunchTemplateTask;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
public class DeleteLaunchTemplateStage implements StageDefinitionBuilder {
  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull @NotNull TaskNode.Builder builder) {
    builder
        .withTask("deleteLaunchTemplate", DeleteLaunchTemplateTask.class)
        .withTask("monitorDeleteLaunchTemplate", MonitorKatoTask.class);
  }

  @Data
  public static class DeleteLaunchTemplateRequest {
    private String credentials;
    private String cloudProvider;
    private String region;
    private Set<String> launchTemplateIds;
  }
}
