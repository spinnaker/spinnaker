/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform;

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition;

import java.util.stream.Collectors;

public class TrimConditionalsTransform implements PipelineTemplateVisitor {

  @Override
  public void visitPipelineTemplate(PipelineTemplate pipelineTemplate) {
    trimConditionals(pipelineTemplate);
  }

  private void trimConditionals(PipelineTemplate pipelineTemplate) {
    // if stage is conditional, ensure children get linked to parents of conditional stage accordingly
    pipelineTemplate.getStages()
      .stream()
      .filter(StageDefinition::getRemoved)
      .forEach(conditionalStage -> pipelineTemplate.getStages()
        .stream()
        .filter(childStage -> childStage.getDependsOn().removeIf(conditionalStage.getId()::equals))
        .forEach(childStage -> childStage.getDependsOn().addAll(conditionalStage.getDependsOn())));

    pipelineTemplate.setStages(
      pipelineTemplate.getStages()
        .stream()
        .filter(stage -> !stage.getRemoved())
        .collect(Collectors.toList())
    );
  }

}
