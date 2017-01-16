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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 * Runs a two-phase render, first to process the handlebars template, then a second
 * phase to convert the resulted JSON string into a Java object.
 */
@Component
public class HandlebarsRenderer implements Renderer {

  private final Logger log = LoggerFactory.getLogger(getClass());

  Handlebars handlebars;

  ObjectMapper pipelineTemplateObjectMapper;

  @Autowired
  public HandlebarsRenderer(Handlebars handlebars, ObjectMapper pipelineTemplateObjectMapper) {
    this.handlebars = handlebars;
    this.pipelineTemplateObjectMapper = pipelineTemplateObjectMapper;
  }

  @Override
  public Object render(String template, RenderContext configuration) {
    log.debug("rendering '" + template + "'");

    Template tmpl;
    try {
      tmpl = handlebars.compileInline(template);
    } catch (IOException e) {
      throw new TemplateRenderException("could not compile handlebars template", e);
    }

    Context context = Context.newContext(configuration);

    String rendered;
    try {
      rendered = tmpl.apply(context);
    } catch (IOException e) {
      throw new TemplateRenderException("could not apply context to template", e);
    }

    JsonNode node;
    try {
      node = pipelineTemplateObjectMapper.readTree(rendered);
    } catch (IOException e) {
      throw new TemplateRenderException("template produced invalid json", e);
    }

    try {
      if (node.isArray()) {
        return pipelineTemplateObjectMapper.readValue(rendered, Collection.class);
      }
      if (node.isObject()) {
        return pipelineTemplateObjectMapper.readValue(rendered, HashMap.class);
      }
      if (node.isBoolean()) {
        return Boolean.parseBoolean(node.asText());
      }
      if (node.isDouble()) {
        return node.doubleValue();
      }
      if (node.canConvertToInt()) {
        return node.intValue();
      }
      if (node.canConvertToLong()) {
        return node.longValue();
      }
      if (node.isTextual()) {
        return node.textValue();
      }
      if (node.isNull()) {
        return null;
      }
    } catch (IOException e) {
      throw new TemplateRenderException("template produced invalid json", e);
    }

    throw new TemplateRenderException("unknown rendered object type");
  }

  private Context buildContext(TemplateConfiguration configuration) {
    HashMap<String, Object> m = new HashMap<>();
    m.put("application", configuration.getPipeline().getApplication());

    for (Entry<String, Object> variable : configuration.getPipeline().getVariables().entrySet()) {
      m.put(variable.getKey(), variable.getValue());
    }

    return Context.newBuilder(m).build();
  }
}
