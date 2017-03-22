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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Runs a two-phase render, first to process the handlebars template, then a second
 * phase to convert the resulted JSON string into a Java object.
 */
public class HandlebarsRenderer implements Renderer {

  private final Logger log = LoggerFactory.getLogger(getClass());

  Handlebars handlebars;

  private RenderedValueConverter renderedValueConverter;

  public HandlebarsRenderer(ObjectMapper pipelineTemplateObjectMapper) {
    this(new JsonRenderedValueConverter(pipelineTemplateObjectMapper), pipelineTemplateObjectMapper);
  }

  public HandlebarsRenderer(RenderedValueConverter renderedValueConverter, ObjectMapper pipelineTemplateObjectMapper) {
    this.renderedValueConverter = renderedValueConverter;

    handlebars = new Handlebars()
      .with(EscapingStrategy.NOOP)
      .registerHelperMissing(new UnknownIdentifierHelper())
      .registerHelper("json", new JsonHelper(pipelineTemplateObjectMapper))
      .registerHelper("module", new ModuleHelper(this, pipelineTemplateObjectMapper))
      .registerHelper("withMapKey", new WithMapKeyHelper())
    ;
    ConditionHelper.register(handlebars);

    log.info("PipelineTemplates: Using HandlebarsRenderer");
  }

  @Override
  public String render(String template, RenderContext configuration) {
    Template tmpl;
    try {
      tmpl = handlebars.compileInline(template);
    } catch (IOException e) {
      throw new TemplateRenderException("could not compile handlebars template", e);
    }

    Context context = Context.newContext(configuration.getVariables());

    String rendered;
    try {
      rendered = tmpl.apply(context);
    } catch (IOException e) {
      log.error("Failed rendering template: " + template, e);
      throw new TemplateRenderException("could not apply context to template", e);
    } catch (HandlebarsException e) {
      log.error("Failed rendering template: " + template, e);
      throw new TemplateRenderException(e.getMessage(), e.getCause());
    }

    // Large handlebar templates can inject a lot of extra whitespace we don't want
    // and can lead to incorrect (or non-existent) template-object expansion
    rendered = rendered.trim().replaceAll("\n", "");

    if (!template.equals(rendered)) {
      log.debug("rendered '" + template + "' -> '" + rendered + "'");
    }

    return rendered;
  }

  @Override
  public Object renderGraph(String template, RenderContext context) {
    return renderedValueConverter.convertRenderedValue(render(template, context));
  }
}
