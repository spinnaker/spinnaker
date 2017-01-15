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
import com.netflix.spinnaker.orca.extensionpoint.pipeline.PipelinePreprocessor;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.TemplateMerge;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.GraphMutator;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
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

  private ObjectMapper pipelineTemplateObjectMapper;

  private RenderEngine renderEngine;

  @Autowired
  public PipelineTemplatePipelinePreprocessor(ObjectMapper pipelineTemplateObjectMapper, RenderEngine renderEngine) {
    this.pipelineTemplateObjectMapper = pipelineTemplateObjectMapper;
    this.renderEngine = renderEngine;
  }

  @Override
  public Map<String, Object> process(Map<String, Object> pipeline) {
    TemplatedPipelineRequest request = pipelineTemplateObjectMapper.convertValue(pipeline, TemplatedPipelineRequest.class);
    if (!request.isTemplatedPipelineRequest()) {
      return pipeline;
    }

    TemplateConfiguration templateConfiguration = request.getRoot();

    // TODO find all templates via request.root.pipeline.template.source
    // list needs to be FIFO, where the first template is the root
    List<PipelineTemplate> templates = Collections.emptyList();

    PipelineTemplate template = TemplateMerge.merge(templates);

    renderEngine.render(template, templateConfiguration);

    GraphMutator mutator = new GraphMutator(templateConfiguration);
    mutator.mutate(template);

    // TODO final validation & marshal to pipeline json

    return pipeline;
  }

  private static class TemplatedPipelineRequest {
    String type;
    TemplateConfiguration root;

    public boolean isTemplatedPipelineRequest() {
      return type.equals("templatedPipeline");
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public TemplateConfiguration getRoot() {
      return root;
    }

    public void setRoot(TemplateConfiguration root) {
      this.root = root;
    }
  }
}
