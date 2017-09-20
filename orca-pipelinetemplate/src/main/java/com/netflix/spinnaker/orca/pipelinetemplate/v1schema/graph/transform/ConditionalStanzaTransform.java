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
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.Conditional;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.DefaultRenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConditionalStanzaTransform implements PipelineTemplateVisitor {

  private TemplateConfiguration templateConfiguration;

  private Map<String, Object> trigger;

  public ConditionalStanzaTransform(TemplateConfiguration templateConfiguration, Map<String, Object> trigger) {
    this.templateConfiguration = templateConfiguration;
    this.trigger = trigger;
  }

  @Override
  public void visitPipelineTemplate(PipelineTemplate pipelineTemplate) {
    trimConditionals(pipelineTemplate.getStages(), pipelineTemplate);
    trimConditionals(templateConfiguration.getStages(), pipelineTemplate);
  }

  private <T extends Conditional> void trimConditionals(List<T> stages, PipelineTemplate template) {
    Optional.ofNullable(stages).ifPresent( allStages -> allStages
      .stream()
      .filter(stage -> stage.getWhen() != null && !stage.getWhen().isEmpty())
      .forEach(stage -> {
        RenderContext context = new DefaultRenderContext(templateConfiguration.getPipeline().getApplication(), template, trigger);
        context.getVariables().putAll(templateConfiguration.getPipeline().getVariables());
        // Conditionals have already been rendered
        for (String conditional : stage.getWhen()) {
          if (!Boolean.parseBoolean(conditional)) {
            stage.setRemove();
          }
        }
      }));
  }
}
