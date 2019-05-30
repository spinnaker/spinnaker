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
package com.netflix.spinnaker.orca.pipelinetemplate;

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipelinetemplate.loader.TemplateLoader;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.TemplateMerge;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration.TemplateSource;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.DefaultRenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PipelineTemplateService {

  private final TemplateLoader templateLoader;

  private final ExecutionRepository executionRepository;

  private final Renderer renderer;

  private final ObjectMapper mapper = OrcaObjectMapper.getInstance();

  @Autowired
  public PipelineTemplateService(
      TemplateLoader templateLoader, ExecutionRepository executionRepository, Renderer renderer) {
    this.templateLoader = templateLoader;
    this.executionRepository = executionRepository;
    this.renderer = renderer;
  }

  public PipelineTemplate resolveTemplate(
      TemplateSource templateSource,
      @Nullable String executionId,
      @Nullable String pipelineConfigId) {
    if (containsJinja(templateSource.getSource())
        && !(executionId == null && pipelineConfigId == null)) {
      try {
        Execution pipeline = retrievePipelineOrNewestExecution(executionId, pipelineConfigId);
        String renderedSource = render(templateSource.getSource(), pipeline);
        if (StringUtils.isNotBlank(renderedSource)) {
          templateSource.setSource(renderedSource);
        }
      } catch (NoSuchElementException e) {
        // Do nothing
      }
    }
    List<PipelineTemplate> templates = templateLoader.load(templateSource);
    return TemplateMerge.merge(templates);
  }

  /**
   * If {@code executionId} is set, it will be retrieved. Otherwise, {@code pipelineConfigId} will
   * be used to find the newest pipeline execution for that configuration.
   *
   * @param executionId An explicit pipeline execution id.
   * @param pipelineConfigId A pipeline configuration id. Ignored if {@code executionId} is set.
   * @return The pipeline
   * @throws IllegalArgumentException if neither executionId or pipelineConfigId are provided
   * @throws ExecutionNotFoundException if no execution could be found
   */
  public Execution retrievePipelineOrNewestExecution(
      @Nullable String executionId, @Nullable String pipelineConfigId)
      throws ExecutionNotFoundException {
    if (executionId != null) {
      // Use an explicit execution
      return executionRepository.retrieve(PIPELINE, executionId);
    } else if (pipelineConfigId != null) {
      // No executionId set - use last execution
      ExecutionRepository.ExecutionCriteria criteria =
          new ExecutionRepository.ExecutionCriteria().setPageSize(1);
      try {
        return executionRepository
            .retrievePipelinesForPipelineConfigId(pipelineConfigId, criteria)
            .toSingle()
            .toBlocking()
            .value();
      } catch (NoSuchElementException e) {
        throw new ExecutionNotFoundException(
            "No pipeline execution could be found for config id "
                + pipelineConfigId
                + ": "
                + e.getMessage());
      }
    } else {
      throw new IllegalArgumentException("Either executionId or pipelineConfigId have to be set.");
    }
  }

  private String render(String templateString, Execution pipeline) {
    DefaultRenderContext rc =
        new DefaultRenderContext(
            pipeline.getApplication(), null, mapper.convertValue(pipeline.getTrigger(), Map.class));
    return renderer.render(templateString, rc);
  }

  private static boolean containsJinja(String string) {
    return string.contains("{%") || string.contains("{{");
  }
}
