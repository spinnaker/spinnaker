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
import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.HandlebarsException;
import com.github.jknack.handlebars.Template;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.helper.ConditionHelper;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.helper.JsonHelper;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.helper.ModuleHelper;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.helper.UnknownIdentifierHelper;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.helper.WithMapKeyHelper;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;

/**
 * Runs a two-phase render, first to process the handlebars template, then a second
 * phase to convert the resulted JSON string into a Java object.
 */
public class HandlebarsRenderer implements Renderer {

  private final Logger log = LoggerFactory.getLogger(getClass());

  Handlebars handlebars;

  ObjectMapper pipelineTemplateObjectMapper;

  public HandlebarsRenderer(ObjectMapper pipelineTemplateObjectMapper) {
    this.pipelineTemplateObjectMapper = pipelineTemplateObjectMapper;

    handlebars = new Handlebars()
      .with(EscapingStrategy.NOOP)
      .registerHelperMissing(new UnknownIdentifierHelper())
      .registerHelper("json", new JsonHelper(pipelineTemplateObjectMapper))
      .registerHelper("module", new ModuleHelper(this, pipelineTemplateObjectMapper))
      .registerHelper("withMapKey", new WithMapKeyHelper())
    ;
    ConditionHelper.register(handlebars);
  }

  @Override
  public String render(String template, RenderContext configuration) {
    log.debug("rendering '" + template + "'");

    Template tmpl;
    try {
      tmpl = handlebars.compileInline(template);
    } catch (IOException e) {
      throw new TemplateRenderException("could not compile handlebars template", e);
    }

    Context context = Context.newContext(configuration);

    try {
      return tmpl.apply(context);
    } catch (IOException e) {
      throw new TemplateRenderException("could not apply context to template", e);
    } catch (HandlebarsException e) {
      throw new TemplateRenderException(e.getMessage(), e.getCause());
    }
  }

  @Override
  public Object renderGraph(String template, RenderContext context) {
    String rendered = render(template, context);

    // Short-circuit primitive values.
    // TODO rz - having trouble getting jackson to parse primitive values outside of unit tests
    if (NumberUtils.isNumber(rendered)) {
      if (rendered.contains(".")) {
        return NumberUtils.createDouble(rendered);
      }
      try {
        return NumberUtils.createInteger(rendered);
      } catch (NumberFormatException ignored) {
        return NumberUtils.createLong(rendered);
      }
    } else if (rendered.equals("true") || rendered.equals("false")) {
      return Boolean.parseBoolean(rendered);
    } else if (rendered.startsWith("{{") || (!rendered.startsWith("{") && !rendered.startsWith("["))) {
      return rendered;
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
}
