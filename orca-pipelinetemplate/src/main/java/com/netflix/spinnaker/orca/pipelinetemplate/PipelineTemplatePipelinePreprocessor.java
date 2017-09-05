/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipelinetemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.extensionpoint.pipeline.PipelinePreprocessor;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.IllegalTemplateConfigurationException;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.generator.ExecutionGenerator;
import com.netflix.spinnaker.orca.pipelinetemplate.loader.TemplateLoader;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.TemplateMerge;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.V1SchemaExecutionGenerator;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.GraphMutator;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.DefaultRenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderUtil;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator.V1TemplateConfigurationSchemaValidator;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator.V1TemplateSchemaValidator;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator.V1TemplateSchemaValidator.SchemaValidatorContext;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Error;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * highlevel lifecycle
 *
 * 1. Find all pipeline templates from configuration source.
 * 2. Merge templates together
 * 3. Render all renderable fields in both template and configuration.
 */
@Component
public class PipelineTemplatePipelinePreprocessor implements PipelinePreprocessor {

  private final ObjectMapper pipelineTemplateObjectMapper;
  private final TemplateLoader templateLoader;
  private final Renderer renderer;
  private final Registry registry;

  @Autowired
  public PipelineTemplatePipelinePreprocessor(ObjectMapper pipelineTemplateObjectMapper,
                                              TemplateLoader templateLoader,
                                              Renderer renderer,
                                              Registry registry) {
    this.pipelineTemplateObjectMapper = pipelineTemplateObjectMapper;
    this.templateLoader = templateLoader;
    this.renderer = renderer;
    this.registry = registry;
  }

  @Override
  public Map<String, Object> process(Map<String, Object> pipeline) {
    Errors errors;
    try {
      return processInternal(pipeline);
    } catch (TemplateLoaderException e) {
      errors = new Errors().add(new Error().withMessage("failed loading template").withCause(e.getMessage()));
    } catch (TemplateRenderException e) {
      errors = e.getErrors();
      if (!errors.hasErrors(true)) {
        errors.add(new Error().withMessage("failed rendering template expression").withCause(e.getMessage()));
      }
    } catch (IllegalTemplateConfigurationException e) {
      errors = new Errors().add(
        e.getError() != null ? e.getError() : new Error().withMessage("malformed template configuration").withCause(e.getMessage())
      );
    }
    return errors.toResponse();
  }

  private Map<String, Object> processInternal(Map<String, Object> pipeline) {
    TemplatedPipelineRequest request = pipelineTemplateObjectMapper.convertValue(pipeline, TemplatedPipelineRequest.class);
    if (!request.isTemplatedPipelineRequest()) {
      return pipeline;
    }

    Errors validationErrors = new Errors();

    TemplateConfiguration templateConfiguration = request.getConfig();
    new V1TemplateConfigurationSchemaValidator().validate(templateConfiguration, validationErrors);
    if (validationErrors.hasErrors(request.plan)) {
      return validationErrors.toResponse();
    }

    PipelineTemplate template = getPipelineTemplate(request, templateConfiguration);

    new V1TemplateSchemaValidator().validate(template, validationErrors, new SchemaValidatorContext(!templateConfiguration.getStages().isEmpty()));
    if (validationErrors.hasErrors(request.plan)) {
      return validationErrors.toResponse();
    }

    Map<String, Object> trigger = (HashMap<String, Object>) pipeline.get("trigger");
    GraphMutator graphMutator = new GraphMutator(templateConfiguration, renderer, registry, trigger);
    graphMutator.mutate(template);

    ExecutionGenerator executionGenerator = new V1SchemaExecutionGenerator();
    Map<String, Object> generatedPipeline = executionGenerator.generate(template, templateConfiguration, (String) pipeline.get("id"));

    return generatedPipeline;
  }

  private PipelineTemplate getPipelineTemplate(TemplatedPipelineRequest request, TemplateConfiguration templateConfiguration) {
    if (request.plan && request.template != null) {
      // Allow template inlining to perform plans without first publishing the template somewhere.
      return request.template;
    }

    if (request.getConfig().getPipeline().getTemplate() == null) {
      throw new IllegalTemplateConfigurationException(new Error().withMessage("configuration is missing a template"));
    }

    setTemplateSourceWithJinja(request);
    List<PipelineTemplate> templates = templateLoader.load(templateConfiguration.getPipeline().getTemplate());

    PipelineTemplate pipelineTemplate = TemplateMerge.merge(templates);

    // ensure that any expressions contained with template variables are rendered
    RenderContext context = RenderUtil.createDefaultRenderContext(
      pipelineTemplate, templateConfiguration, request.getTrigger()
    );
    renderTemplateVariables(context, pipelineTemplate);

    return pipelineTemplate;
  }

  private void renderTemplateVariables(RenderContext renderContext, PipelineTemplate pipelineTemplate) {
    if (pipelineTemplate.getVariables() == null) {
      return;
    }

    pipelineTemplate.getVariables().forEach(v -> {
      Object value = v.getDefaultValue();
      if (value != null && value instanceof String) {
        v.setDefaultValue(renderer.renderGraph(value.toString(), renderContext));
      }
    });
  }

  private void setTemplateSourceWithJinja(TemplatedPipelineRequest request) {
    RenderContext context = new DefaultRenderContext(request.getConfig().getPipeline().getApplication(), null, request.getTrigger());
    request.getConfig().getPipeline().getTemplate().setSource(renderer.render(request.getConfig().getPipeline().getTemplate().getSource(), context ));
  }

  private static class TemplatedPipelineRequest {
    String type;
    Map<String, Object> trigger;
    TemplateConfiguration config;
    PipelineTemplate template;
    Boolean plan = false;

    public boolean isTemplatedPipelineRequest() {
      return "templatedPipeline".equals(type);
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public TemplateConfiguration getConfig() {
      return config;
    }

    public void setConfig(TemplateConfiguration config) {
      this.config = config;
    }

    public Map<String, Object> getTrigger() {
      return trigger;
    }

    public void setTrigger(Map<String, Object> trigger) {
      this.trigger = trigger;
    }

    public PipelineTemplate getTemplate() {
      return template;
    }

    public void setTemplate(PipelineTemplate template) {
      this.template = template;
    }

    public Boolean getPlan() {
      return plan;
    }

    public void setPlan(Boolean plan) {
      this.plan = plan;
    }
  }
}
