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
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.JinjavaConfig;
import com.hubspot.jinjava.interpret.InterpretException;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.loader.ResourceLocator;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.filters.FriggaFilter;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.tags.ModuleTag;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Error;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;

public class JinjaRenderer implements Renderer {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private Jinjava jinja;

  private RenderedValueConverter renderedValueConverter;

  public JinjaRenderer(ObjectMapper pipelineTemplateObjectMapper) {
    this(new JsonRenderedValueConverter(pipelineTemplateObjectMapper), pipelineTemplateObjectMapper);
  }

  public JinjaRenderer(RenderedValueConverter renderedValueConverter, ObjectMapper pipelineTemplateObjectMapper) {
    this.renderedValueConverter = renderedValueConverter;

    JinjavaConfig config = new JinjavaConfig();
    jinja = new Jinjava(config);
    jinja.setResourceLocator(new NoopResourceLocator());
    jinja.getGlobalContext().registerTag(new ModuleTag(this, pipelineTemplateObjectMapper));
    jinja.getGlobalContext().registerFilter(new FriggaFilter());

    log.info("PipelineTemplates: Using JinjaRenderer");
  }

  @Override
  public String render(String template, RenderContext context) {
    String rendered;
    try {
      rendered = jinja.render(template, context.getVariables());
    } catch (InterpretException e) {
      log.error("Failed rendering jinja template", e);
      throw new TemplateRenderException(new Error()
        .withMessage("failed rendering jinja template")
        .withCause(e.getMessage())
        .withLocation(context.getLocation())
      );
    }

    rendered = rendered.trim();

    if (!template.equals(rendered)) {
      log.debug("rendered '" + template + "' -> '" + rendered + "'");
    }

    return rendered;
  }

  @Override
  public Object renderGraph(String template, RenderContext context) {
    // TODO rz - Need to catch exceptions out of this value converter & give more detailed information if it's a nested
    // value. For example, if iterating over module output, the line number reported by yamlsnake will be for the final
    // template, not the input, so it's difficult to correlate what actually is broken.
    String renderedValue = render(template, context);
    return renderedValueConverter.convertRenderedValue(renderedValue);
  }

  private static class NoopResourceLocator implements ResourceLocator {
    @Override
    public String getString(String fullName, Charset encoding, JinjavaInterpreter interpreter) throws IOException {
      return null;
    }
  }
}
