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

import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.IllegalTemplateConfigurationException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Replaces and injects configuration-defined stages into the pipeline template.
 *
 * If the template does not have any stages, all stages from the configuration
 * will be added. This can be useful in situations where users want to publish
 * pipeline traits, then wire them together through configuration-level stages.
 * This offers more flexibility in addition to inheritance relatively for free.
 *
 * When template stages do exist, a configuration-defined stage must either
 * be replacing a stage by the same name, or have an inject stanza. Stages
 * without these semantics will hard-fail, to safe-guard against underlying
 * templates changing in unexpected ways.
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
      List<StageDefinition> illegalStages = templateStages.stream()
        .filter(s -> s.getInject() != null)
        .collect(Collectors.toList());
      if (illegalStages.size() > 0) {
        throw new IllegalTemplateConfigurationException("Stage injection is not allowed when parent templates do not have any stages");
      }

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
    // TODO rz - injection isn't going to be a first-pass feature
  }
}
