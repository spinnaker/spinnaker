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
import com.hubspot.jinjava.interpret.Context.Library;
import com.hubspot.jinjava.interpret.FatalTemplateErrorsException;
import com.hubspot.jinjava.interpret.InterpretException;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.interpret.TemplateError;
import com.hubspot.jinjava.interpret.TemplateError.ErrorItem;
import com.hubspot.jinjava.interpret.TemplateError.ErrorReason;
import com.hubspot.jinjava.interpret.TemplateError.ErrorType;
import com.hubspot.jinjava.interpret.errorcategory.BasicTemplateErrorCategory;
import com.hubspot.jinjava.lib.tag.Tag;
import com.hubspot.jinjava.loader.ResourceLocator;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.filters.Base64Filter;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.filters.FriggaFilter;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.filters.JsonFilter;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.tags.ModuleTag;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.tags.PipelineIdTag;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Error;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.parser.ParserException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class JinjaRenderer implements Renderer {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private Jinjava jinja;

  private RenderedValueConverter renderedValueConverter;

  public JinjaRenderer(ObjectMapper pipelineTemplateObjectMapper, Front50Service front50Service, List<Tag> jinjaTags) {
    this(new JsonRenderedValueConverter(pipelineTemplateObjectMapper), pipelineTemplateObjectMapper, front50Service, jinjaTags);
  }

  public JinjaRenderer(RenderedValueConverter renderedValueConverter, ObjectMapper pipelineTemplateObjectMapper, Front50Service front50Service, List<Tag> jinjaTags) {
    this.renderedValueConverter = renderedValueConverter;

    JinjavaConfig config = new JinjavaConfig();
    config.getDisabled().put(Library.TAG, new HashSet<>(Arrays.asList("from", "import", "include")));
    jinja = new Jinjava(config);
    jinja.setResourceLocator(new NoopResourceLocator());
    jinja.getGlobalContext().registerTag(new ModuleTag(this, pipelineTemplateObjectMapper));
    jinja.getGlobalContext().registerTag(new PipelineIdTag(front50Service));
    if (jinjaTags != null) {
      jinjaTags.forEach(tag -> jinja.getGlobalContext().registerTag(tag));
    }

    jinja.getGlobalContext().registerFilter(new FriggaFilter());
    jinja.getGlobalContext().registerFilter(new JsonFilter(pipelineTemplateObjectMapper));
    jinja.getGlobalContext().registerFilter(new Base64Filter(this));

    log.info("PipelineTemplates: Using JinjaRenderer");
  }

  @Override
  public String render(String template, RenderContext context) {
    String rendered;
    try {
      rendered = jinja.render(template, context.getVariables());
    } catch (FatalTemplateErrorsException fte) {
      log.error("Failed rendering jinja template", fte);
      throw new TemplateRenderException("failed rendering jinja template", fte, unwrapJinjaTemplateErrorException(fte, context.getLocation()));
    } catch (InterpretException e) {
      log.warn("Caught supertype InterpretException instead of " + e.getClass().getSimpleName());
      log.error("Failed rendering jinja template", e);

      throw TemplateRenderException.fromError(
        new Error()
          .withMessage("failed rendering jinja template")
          .withLocation(context.getLocation()),
        e
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
    String renderedValue = render(template, context);
    try {
      return renderedValueConverter.convertRenderedValue(renderedValue);
    } catch (ParserException e) {
      throw TemplateRenderException.fromError(
        new Error()
          .withMessage("Failed converting rendered value to YAML")
          .withLocation(context.getLocation())
          .withDetail("source", template)
          .withCause(e.getMessage()),
        e
      );
    }
  }

  private static class NoopResourceLocator implements ResourceLocator {
    @Override
    public String getString(String fullName, Charset encoding, JinjavaInterpreter interpreter) throws IOException {
      return null;
    }
  }

  private static Errors unwrapJinjaTemplateErrorException(FatalTemplateErrorsException e, String location) {
    Errors errors = new Errors();
    for (TemplateError templateError : e.getErrors()) {
      if (templateError.getException() instanceof TemplateRenderException) {
        TemplateRenderException tre = (TemplateRenderException) templateError.getException();
        errors.addAll(tre.getErrors());
        continue;
      }

      Error error = new Error()
        .withMessage(templateError.getMessage())
        .withSeverity(templateError.getSeverity().equals(ErrorType.FATAL) ? Severity.FATAL : Severity.WARN)
        .withLocation(location);
      errors.add(error);

      if (templateError.getReason() != ErrorReason.OTHER) {
        error.withDetail("reason", templateError.getReason().name());
      }
      if (templateError.getItem() != ErrorItem.OTHER) {
        error.withDetail("item", templateError.getItem().name());
      }
      if (templateError.getFieldName() != null) {
        error.withDetail("fieldName", templateError.getFieldName());
      }
      if (templateError.getLineno() > 0) {
        error.withDetail("lineno", Integer.toString(templateError.getLineno()));
      }
      if (templateError.getCategory() != BasicTemplateErrorCategory.UNKNOWN) {
        error.withDetail("category", templateError.getCategory().toString());
        templateError.getCategoryErrors().forEach((s, s2) -> error.withDetail("categoryError:" + s, s2));
      }

      // This gross little bit is necessary for bubbling up any errors that
      // might've occurred while rendering a nested module.
      if (templateError.getException() != null && templateError.getException().getCause() instanceof TemplateRenderException) {
        error.withNested(((TemplateRenderException) templateError.getException().getCause()).getErrors());
      }
    }
    return errors;
  }

}
