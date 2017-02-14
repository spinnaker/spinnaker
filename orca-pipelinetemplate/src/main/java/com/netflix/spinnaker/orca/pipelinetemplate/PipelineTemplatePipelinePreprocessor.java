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
import com.netflix.spinnaker.orca.pipelinetemplate.generator.ExecutionGenerator;
import com.netflix.spinnaker.orca.pipelinetemplate.loader.TemplateLoader;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.TemplateMerge;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.V1SchemaExecutionGenerator;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.GraphMutator;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer;
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
    TemplatedPipelineRequest request = pipelineTemplateObjectMapper.convertValue(pipeline, TemplatedPipelineRequest.class);
    if (!request.isTemplatedPipelineRequest()) {
      return pipeline;
    }

    TemplateConfiguration templateConfiguration = request.getConfig();

    List<PipelineTemplate> templates = templateLoader.load(templateConfiguration.getPipeline().getTemplate());

    PipelineTemplate template = TemplateMerge.merge(templates);
    Map<String, Object> trigger = (HashMap<String, Object>) pipeline.get("trigger");

    GraphMutator graphMutator = new GraphMutator(templateConfiguration, renderer, registry, trigger);
    graphMutator.mutate(template);

    // TODO validation

    ExecutionGenerator executionGenerator = new V1SchemaExecutionGenerator();

    return executionGenerator.generate(template, templateConfiguration);
  }

  private static class TemplatedPipelineRequest {
    String type;
    Map<String, Object> trigger;
    TemplateConfiguration config;

    public boolean isTemplatedPipelineRequest() {
      return type != null && type.equals("templatedPipeline");
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
  }
}
