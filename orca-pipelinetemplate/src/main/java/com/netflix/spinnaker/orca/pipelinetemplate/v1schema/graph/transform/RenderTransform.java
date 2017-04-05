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

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.IllegalTemplateConfigurationException;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Variable;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.DefaultRenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderUtil;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RenderTransform implements PipelineTemplateVisitor {

  TemplateConfiguration templateConfiguration;

  Renderer renderer;

  Registry registry;

  Map<String, Object> trigger;

  private final Timer renderTemplateTimer;

  public RenderTransform(TemplateConfiguration templateConfiguration, Renderer renderer, Registry registry, Map<String, Object> trigger) {
    this.templateConfiguration = templateConfiguration;
    this.renderer = renderer;
    this.registry = registry;
    this.trigger = trigger;
    this.renderTemplateTimer = registry.timer("server.renderPipelineTemplate");
  }

  @Override
  public void visitPipelineTemplate(PipelineTemplate pipelineTemplate) {
    long start = registry.clock().monotonicTime();
    render(pipelineTemplate);
    long end = registry.clock().monotonicTime();
    renderTemplateTimer.record(end - start, TimeUnit.NANOSECONDS);
  }

  private void render(PipelineTemplate template) {
    RenderContext context = new DefaultRenderContext(templateConfiguration.getPipeline().getApplication(), template, trigger);

    template.getVariables().stream()
      .filter(Variable::hasDefaultValue)
      .forEach(v -> context.getVariables().put(v.getName(), v.getDefaultValue()));

    context.getVariables().putAll(templateConfiguration.getPipeline().getVariables());

    // We only render the stages here, whereas modules will be rendered only if used within stages.
    renderStages(template.getStages(), context, "template");
    renderStages(templateConfiguration.getStages(), context, "configuration");
  }

  @SuppressWarnings("unchecked")
  private void renderStages(List<StageDefinition> stages, RenderContext context, String locationNamespace) {
    if (stages == null) {
      return;
    }

    for (StageDefinition stage : stages) {
      Object rendered;
      try {
        rendered = RenderUtil.deepRender(renderer, stage.getConfig(), context);
      } catch (TemplateRenderException e) {
        throw new TemplateRenderException(new Errors.Error()
          .withMessage("Failed rendering stage")
          .withCause(e.getMessage())
          .withLocation(String.format("%s:stages.%s", locationNamespace, stage.getId()))
        );
      }

      if (!(rendered instanceof Map)) {
        throw new IllegalTemplateConfigurationException(new Errors.Error()
          .withMessage("A stage's rendered config must be a map")
          .withCause("Received type " + rendered.getClass().toString())
          .withLocation(String.format("%s:stages.%s", locationNamespace, stage.getId()))
        );
      }
      stage.setConfig((Map<String, Object>) rendered);

      stage.setName(renderStageProperty(stage.getName(), context, getStagePropertyLocation(locationNamespace, stage.getId(), "name")));
      stage.setComments(renderStageProperty(stage.getComments(), context, getStagePropertyLocation(locationNamespace, stage.getId(), "comments")));
    }
  }

  private String renderStageProperty(String input, RenderContext context, String location) {
    try {
      String rendered = (String) RenderUtil.deepRender(renderer, input, context);
      return rendered;
    } catch (TemplateRenderException e) {
      throw new TemplateRenderException(new Errors.Error()
        .withMessage("Failed rendering stage property")
        .withCause(e.getMessage())
        .withLocation(location)
      );
    }
  }

  private static String getStagePropertyLocation(String namespace, String stageId, String propertyName) {
    return String.format("%s:stages.%s.%s", namespace, stageId, propertyName);
  }
}
