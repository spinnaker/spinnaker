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
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.filters.*;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.tags.ModuleTag;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.tags.PipelineIdTag;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.tags.StrategyIdTag;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Error;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.parser.ParserException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JinjaRenderer implements Renderer {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private Jinjava jinja;

  /**
   * Renderer that is tolerant of unknown tokens to support rendering nullable template variables.
   */
  private Jinjava nullableJinja;

  private RenderedValueConverter renderedValueConverter;

  public JinjaRenderer(ObjectMapper pipelineTemplateObjectMapper, Front50Service front50Service, List<Tag> jinjaTags) {
    this(new JsonRenderedValueConverter(pipelineTemplateObjectMapper), pipelineTemplateObjectMapper, front50Service, jinjaTags);
  }

  public JinjaRenderer(RenderedValueConverter renderedValueConverter, ObjectMapper pipelineTemplateObjectMapper, Front50Service front50Service, List<Tag> jinjaTags) {
    if (front50Service == null) {
      log.error("Pipeline templates require front50 to enabled. Set 'front50.enabled: true' in your orca config.");
      return;
    }

    this.renderedValueConverter = renderedValueConverter;

    jinja = createJinjaRenderer(true, pipelineTemplateObjectMapper, front50Service, jinjaTags);
    nullableJinja = createJinjaRenderer(false, pipelineTemplateObjectMapper, front50Service, jinjaTags);

    log.info("PipelineTemplates: Using JinjaRenderer");
  }

  private Jinjava createJinjaRenderer(boolean failOnUnknownTokens,
                                      ObjectMapper pipelineTemplateObjectMapper,
                                      Front50Service front50Service,
                                      List<Tag> jinjaTags) {
    Jinjava jinja = new Jinjava(buildJinjavaConfig(failOnUnknownTokens));
    jinja.setResourceLocator(new NoopResourceLocator());
    jinja.getGlobalContext().registerTag(new ModuleTag(this, pipelineTemplateObjectMapper));
    jinja.getGlobalContext().registerTag(new PipelineIdTag(front50Service));
    jinja.getGlobalContext().registerTag(new StrategyIdTag(front50Service));
    if (jinjaTags != null) {
      jinjaTags.forEach(tag -> jinja.getGlobalContext().registerTag(tag));
    }

    jinja.getGlobalContext().registerFilter(new FriggaFilter());
    jinja.getGlobalContext().registerFilter(new JsonFilter(pipelineTemplateObjectMapper));
    jinja.getGlobalContext().registerFilter(new Base64Filter(this));
    jinja.getGlobalContext().registerFilter(new MaxFilter());
    jinja.getGlobalContext().registerFilter(new MinFilter());
    return jinja;
  }

  @Override
  public String render(String template, RenderContext context) {
    String rendered;
    try {
      rendered = jinja.render(template, context.getVariables());
    } catch (FatalTemplateErrorsException fte) {
      List<TemplateError> templateErrors = (List<TemplateError>) fte.getErrors();

      // Nullable variables aren't rendered properly if we fail on unknown tokens.
      List<String> errorMessages = templateErrors.stream().map(TemplateError::getMessage).collect(Collectors.toList());
      Map<String, Object> contextVariables = context.getVariables();

      Predicate<String> nullableUnknownToken = key -> {
        // Need to ensure the unknown token is a nullable variable.
        Pattern unknownTokenPattern = Pattern.compile(String.format("UnknownTokenException[:\\s+\\w+]+%s", key));
        return contextVariables.containsKey(key) && contextVariables.get(key) == null &&
          errorMessages.stream().anyMatch(msg -> unknownTokenPattern.matcher(msg).find());
      };

      if (contextVariables.keySet().stream().anyMatch(nullableUnknownToken)) {
        log.debug("Nullable variable referenced in template '{}'. Rendering template with unknown token tolerant Jinja renderer.");
        rendered = nullableJinja.render(template, context.getVariables());
      } else {
        log.error("Failed rendering jinja template", fte);
        throw new TemplateRenderException("failed rendering jinja template", fte, unwrapJinjaTemplateErrorException(fte, context.getLocation()));
      }
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

  private static JinjavaConfig buildJinjavaConfig(boolean failOnUnknownTokens) {
    JinjavaConfig.Builder configBuilder = JinjavaConfig.newBuilder();

    configBuilder.withFailOnUnknownTokens(failOnUnknownTokens);

    Map<Library, Set<String>> disabled = new HashMap<>();
    disabled.put(Library.TAG, new HashSet<>(Arrays.asList("from", "import", "include")));
    configBuilder.withDisabled(disabled);

    return configBuilder.build();
  }
}
