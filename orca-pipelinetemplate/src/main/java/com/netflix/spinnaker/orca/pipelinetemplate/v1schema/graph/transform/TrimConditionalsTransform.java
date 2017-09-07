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
