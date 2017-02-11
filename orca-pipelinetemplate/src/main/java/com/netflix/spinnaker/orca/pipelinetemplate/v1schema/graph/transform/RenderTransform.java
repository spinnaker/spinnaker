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
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderUtil;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer;

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
    RenderContext context = new RenderContext(templateConfiguration.getPipeline().getApplication(), template, trigger);
    context.putAll(templateConfiguration.getPipeline().getVariables());

    // We only render the stages here, whereas modules will be rendered only if used within stages.
    renderStages(template.getStages(), context);
    renderStages(templateConfiguration.getStages(), context);
  }

  @SuppressWarnings("unchecked")
  private void renderStages(List<StageDefinition> stages, RenderContext context) {
    if (stages == null) {
      return;
    }

    for (StageDefinition stage : stages) {
      Object rendered = RenderUtil.deepRender(renderer, stage.getConfig(), context);
      if (!(rendered instanceof Map)) {
        throw new IllegalTemplateConfigurationException("A stage's rendered config must be a map");
      }
      stage.setConfig((Map<String, Object>) rendered);
    }
  }
}
