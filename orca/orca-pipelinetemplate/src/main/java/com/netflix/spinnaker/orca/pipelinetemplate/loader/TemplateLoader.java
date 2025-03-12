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

package com.netflix.spinnaker.orca.pipelinetemplate.loader;

import static java.lang.String.format;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.TemplateMerge;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderUtil;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TemplateLoader {
  private Collection<TemplateSchemeLoader> schemeLoaders;
  private ObjectMapper objectMapper;

  private Renderer renderer;

  @Autowired
  public TemplateLoader(
      Collection<TemplateSchemeLoader> schemeLoaders,
      ObjectMapper objectMapper,
      Renderer renderer) {
    this.schemeLoaders = schemeLoaders;
    this.objectMapper =
        new ObjectMapper(new YAMLFactory())
            .setConfig(objectMapper.getSerializationConfig())
            .setConfig(objectMapper.getDeserializationConfig());
    this.renderer = renderer;
  }

  /** @return a LIFO list of pipeline templates */
  public List<PipelineTemplate> load(
      TemplateConfiguration.TemplateSource template,
      @Nullable TemplateConfiguration tc,
      @Nullable Map<String, Object> trigger) {
    Map<String, Object> pipelineTemplate = load(template.getSource());
    return load(pipelineTemplate, tc, trigger);
  }

  public List<PipelineTemplate> load(
      Map<String, Object> pipelineTemplate,
      @Nullable TemplateConfiguration tc,
      @Nullable Map<String, Object> trigger) {
    List<Map<String, Object>> pipelineTemplates = new ArrayList<>();

    pipelineTemplates.add(0, pipelineTemplate);

    Set<String> seenTemplateSources = new HashSet<>();
    while (pipelineTemplate.get("source") != null) {
      String source = pipelineTemplate.get("source").toString();
      seenTemplateSources.add(source);

      pipelineTemplate = load(source);
      pipelineTemplates.add(0, pipelineTemplate);

      if (pipelineTemplate.get("source") != null
          && seenTemplateSources.contains(pipelineTemplate.get("source").toString())) {
        throw new TemplateLoaderException(
            format(
                "Illegal cycle detected loading pipeline template '%s'",
                pipelineTemplate.get("source")));
      }
    }

    if (tc != null) {
      List<PipelineTemplate.Variable> variables =
          pipelineTemplates.stream()
              .map(
                  template ->
                      objectMapper.convertValue(
                          template.getOrDefault("variables", Collections.emptyList()),
                          new TypeReference<List<PipelineTemplate.Variable>>() {}))
              .reduce(Collections.emptyList(), TemplateMerge::mergeNamedContent);
      pipelineTemplates = preRenderStagesAndVariables(pipelineTemplates, tc, trigger, variables);
    }

    return pipelineTemplates.stream()
        .map(
            template -> {
              try {
                return objectMapper.convertValue(template, PipelineTemplate.class);
              } catch (IllegalArgumentException e) {
                throw new TemplateLoaderException(e);
              }
            })
        .collect(Collectors.toList());
  }

  /**
   * This method will render Jinja expressions present in the stages block of a pipeline template if
   * (and only if) the entire block is represented as a String. This makes it possible to
   * dynamically create stages in the templates, e.g. by creating Jinja loops.
   */
  private List<Map<String, Object>> preRenderStagesAndVariables(
      List<Map<String, Object>> pipelineTemplates,
      TemplateConfiguration tc,
      Map<String, Object> trigger,
      List<PipelineTemplate.Variable> variables) {
    PipelineTemplate contextTemplate = new PipelineTemplate();
    contextTemplate.setVariables(variables);
    RenderContext context =
        RenderUtil.createDefaultRenderContext(
            contextTemplate, tc, Optional.ofNullable(trigger).orElse(Collections.emptyMap()));
    renderTemplateVariables(context, variables);

    return pipelineTemplates.stream()
        .peek(
            template -> {
              template.put("variables", variables);
              if (template.get("stages") instanceof String) {
                try {
                  String renderedTemplate =
                      renderer.render((String) template.get("stages"), context);
                  List<Map<String, Object>> stages =
                      objectMapper.readValue(renderedTemplate, new TypeReference<>() {});
                  template.put("stages", stages);
                } catch (TemplateRenderException | JsonProcessingException e) {
                  log.warn(
                      "Tried to pre-render stages for pipeline {}/{}, but an error occurred during parsing.",
                      tc.getPipeline().getApplication(),
                      tc.getPipeline().getName(),
                      e);
                }
              }
            })
        .collect(Collectors.toList());
  }

  private Map<String, Object> load(String source) {
    URI uri;
    try {
      uri = new URI(source);
    } catch (URISyntaxException e) {
      throw new TemplateLoaderException(format("Invalid URI '%s'", source), e);
    }

    TemplateSchemeLoader schemeLoader =
        schemeLoaders.stream()
            .filter(l -> l.supports(uri))
            .findFirst()
            .orElseThrow(
                () ->
                    new TemplateLoaderException(
                        format("No TemplateSchemeLoader found for '%s'", uri.getScheme())));

    return schemeLoader.load(uri);
  }

  private void renderTemplateVariables(
      RenderContext renderContext, List<PipelineTemplate.Variable> variables) {
    if (variables == null) {
      return;
    }
    variables.forEach(
        v -> {
          Object value = v.getDefaultValue();
          if (v.isNullable() && value == null) {
            renderContext.getVariables().putIfAbsent(v.getName(), v.getDefaultValue());
          } else if (value instanceof String) {
            v.setDefaultValue(renderer.renderGraph(value.toString(), renderContext));
            renderContext.getVariables().putIfAbsent(v.getName(), v.getDefaultValue());
          }
        });
  }
}
