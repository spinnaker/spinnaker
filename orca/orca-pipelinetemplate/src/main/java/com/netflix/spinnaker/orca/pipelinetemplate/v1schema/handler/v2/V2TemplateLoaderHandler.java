/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.handler.v2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import com.netflix.spinnaker.orca.pipelinetemplate.handler.Handler;
import com.netflix.spinnaker.orca.pipelinetemplate.handler.HandlerChain;
import com.netflix.spinnaker.orca.pipelinetemplate.handler.PipelineTemplateContext;
import com.netflix.spinnaker.orca.pipelinetemplate.handler.v2.V2PipelineTemplateContext;
import com.netflix.spinnaker.orca.pipelinetemplate.loader.v2.V2TemplateLoader;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.v2.V2DefaultRenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.v2.V2RenderUtil;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2TemplateConfiguration;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class V2TemplateLoaderHandler implements Handler {

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
      new TypeReference<Map<String, Object>>() {};
  private V2TemplateLoader templateLoader;
  private ContextParameterProcessor contextParameterProcessor;
  private ObjectMapper objectMapper;

  public V2TemplateLoaderHandler(
      V2TemplateLoader templateLoader,
      ContextParameterProcessor contextParameterProcessor,
      ObjectMapper objectMapper) {
    this.templateLoader = templateLoader;
    this.contextParameterProcessor = contextParameterProcessor;
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(@NotNull HandlerChain chain, @NotNull PipelineTemplateContext context) {
    V2TemplateConfiguration config =
        objectMapper.convertValue(context.getRequest().getConfig(), V2TemplateConfiguration.class);

    Map<String, Object> trigger = context.getRequest().getTrigger();
    // Allow the config's source to be dynamically resolved from trigger payload.
    // TODO(jacobkiefer): Reevaluate whether we should enable dynamically resolved templates.
    renderPipelineTemplateSource(config, trigger);

    // If a template source isn't provided by the configuration, we're assuming that the
    // configuration is fully-formed.
    V2PipelineTemplate template = new V2PipelineTemplate();
    if (config.getTemplate() == null) {
      List<V2PipelineTemplate.Variable> variables =
          config.getVariables().entrySet().stream()
              .map(
                  v -> {
                    return V2PipelineTemplate.Variable.builder()
                        .name(v.getKey())
                        .defaultValue(v.getValue())
                        .build();
                  })
              .collect(Collectors.toList());
      template.setVariables(variables);
    } else {
      template = templateLoader.load(config.getTemplate());
    }

    RenderContext renderContext =
        V2RenderUtil.createDefaultRenderContext(template, config, trigger);
    renderTemplateVariables(renderContext, template);

    context.setSchemaContext(new V2PipelineTemplateContext(config, template));
  }

  private void renderTemplateVariables(
      RenderContext renderContext, V2PipelineTemplate pipelineTemplate) {
    if (pipelineTemplate.getVariables() == null) {
      return;
    }

    List<V2PipelineTemplate.Variable> renderedVars =
        pipelineTemplate.getVariables().stream()
            .map(v -> renderSingleVariable(renderContext, v))
            .collect(Collectors.toList());
    pipelineTemplate.setVariables(renderedVars);
  }

  private V2PipelineTemplate.Variable renderSingleVariable(
      RenderContext renderContext, V2PipelineTemplate.Variable v) {
    Object value = v.getDefaultValue();
    if (v.isNullable() && value == null) {
      return v;
    } else {
      Map<String, Object> resolvedVar =
          contextParameterProcessor.process(
              objectMapper.convertValue(v, MAP_TYPE_REFERENCE), renderContext.getVariables(), true);
      return objectMapper.convertValue(resolvedVar, V2PipelineTemplate.Variable.class);
    }
  }

  private void renderPipelineTemplateSource(
      V2TemplateConfiguration tc, Map<String, Object> trigger) {
    if (trigger == null || tc.getTemplate() == null) {
      return;
    }
    V2DefaultRenderContext renderContext =
        new V2DefaultRenderContext(tc.getApplication(), null, trigger);

    Map<String, Object> processedTemplate =
        contextParameterProcessor.process(
            objectMapper.convertValue(tc.getTemplate(), MAP_TYPE_REFERENCE),
            renderContext
                .getVariables(), // Lift trigger and application out of 'variables' namespace.
            true);
    tc.setTemplate(objectMapper.convertValue(processedTemplate, Artifact.class));
  }
}
