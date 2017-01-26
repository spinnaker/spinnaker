/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform;

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition.InjectionRule;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Replaces and injects configuration-defined stages into the pipeline template.
 *
 * If the template does not have any stages, all stages from the configuration
 * will be added. This can be useful in situations where users want to publish
 * pipeline traits, then wire them together through configuration-level stages.
 * This offers more flexibility in addition to inheritance relatively for free.
 */
public class ConfigStageInjectionTransform implements PipelineTemplateVisitor {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private TemplateConfiguration templateConfiguration;

  public ConfigStageInjectionTransform(TemplateConfiguration templateConfiguration) {
    this.templateConfiguration = templateConfiguration;
  }

  @Override
  public void visitPipelineTemplate(PipelineTemplate pipelineTemplate) {
    addAll(pipelineTemplate);
    replaceStages(pipelineTemplate);
    injectStages(pipelineTemplate);
  }

  private void addAll(PipelineTemplate pipelineTemplate) {
    List<StageDefinition> templateStages = pipelineTemplate.getStages();
    if (templateStages.size() == 0) {
      templateStages.addAll(templateConfiguration.getStages());
    }
  }

  private void replaceStages(PipelineTemplate pipelineTemplate) {
    List<StageDefinition> templateStages = pipelineTemplate.getStages();
    for (StageDefinition confStage : templateConfiguration.getStages()) {
      for (int i = 0; i < templateStages.size(); i++) {
        if (templateStages.get(i).getId().equals(confStage.getId())) {
          templateStages.set(i, confStage);
          log.debug(String.format("Template '%s' stage '%s' replaced by config %s", pipelineTemplate.getId(), confStage.getId(), templateConfiguration.getRuntimeId()));
        }
      }
    }
  }

  private void injectStages(PipelineTemplate pipelineTemplate) {
    // Create initial graph via dependsOn.
    createDag(pipelineTemplate.getStages());

    // Handle stage injections.
    injectStages(pipelineTemplate.getStages(), pipelineTemplate.getStages());
    injectStages(templateConfiguration.getStages(), pipelineTemplate.getStages());
  }

  private static void createDag(List<StageDefinition> stages) {
    stages
      .stream()
      .filter(s -> !s.getDependsOn().isEmpty() && s.getInject() == null)
      .forEach(s -> s.setRequisiteStageRefIds(
        stages
          .stream()
          .filter(parentCandidate -> s.getDependsOn().contains(parentCandidate.getId()))
          .map(StageDefinition::getId)
          .collect(Collectors.toList())
      ));
  }

  private static void injectStages(List<StageDefinition> stages, List<StageDefinition> templateStages) {
    stages.stream()
      .filter(s -> s.getInject() != null)
      .forEach(s -> {
        templateStages.add(s);

        InjectionRule rule = s.getInject();

        if (rule.getFirst() != null && rule.getFirst()) {
          injectFirst(s, templateStages);
          return;
        }

        if (rule.getLast() != null && rule.getLast()) {
          injectLast(s, templateStages);
          return;
        }

        if (rule.getBefore() != null) {
          injectBefore(s, rule.getBefore(), templateStages);
          return;
        }

        if (rule.getAfter() != null) {
          injectAfter(s, rule.getAfter(), templateStages);
          return;
        }

        throw new IllegalStateException(String.format("stage did not have any valid injections defined (id: %s)", s.getId()));
      });
  }

  private static void injectFirst(StageDefinition stage, List<StageDefinition> allStages) {
    allStages
      .stream()
      .filter(pts -> pts.getRequisiteStageRefIds().isEmpty())
      .forEach(pts -> pts.getRequisiteStageRefIds().add(stage.getId()));
  }

  private static void injectLast(StageDefinition stage, List<StageDefinition> allStages) {
    int numStages = allStages.size();
    allStages
      .stream()
      .filter(s -> !s.getId().equals(stage.getId()))
      .filter(candidate -> allStages
        .stream()
        .filter(s -> !s.getRequisiteStageRefIds().contains(candidate.getId()))
        .count() == numStages)
      .forEach(parent -> stage.getRequisiteStageRefIds().add(parent.getId()));
  }

  private static void injectBefore(StageDefinition stage, String targetId, List<StageDefinition> allStages) {
    StageDefinition target = getInjectionTarget(stage.getId(), targetId, allStages);

    stage.getRequisiteStageRefIds().addAll(target.getRequisiteStageRefIds());
    target.getRequisiteStageRefIds().clear();
    target.getRequisiteStageRefIds().add(stage.getId());
  }

  private static void injectAfter(StageDefinition stage, String targetId, List<StageDefinition> allStages) {
    StageDefinition target = getInjectionTarget(stage.getId(), targetId, allStages);

    stage.getRequisiteStageRefIds().add(target.getId());
    allStages
      .stream()
      .filter(childCandidate -> !childCandidate.getId().equals(stage.getId()) &&
        childCandidate.getRequisiteStageRefIds().contains(target.getId()))
      .forEach(child -> {
        child.getRequisiteStageRefIds().removeAll(Collections.singleton(target.getId()));
        child.getRequisiteStageRefIds().add(stage.getId());
      });
  }

  private static StageDefinition getInjectionTarget(String stageId, String targetId, List<StageDefinition> allStages) {
    return allStages
      .stream()
      .filter(pts -> pts.getId().equals(targetId))
      .findFirst()
      .orElseThrow(() -> new RuntimeException(
        String.format("could not inject '%s' stage: unknown target stage id '%s'", stageId, targetId)
      ));
  }
}
