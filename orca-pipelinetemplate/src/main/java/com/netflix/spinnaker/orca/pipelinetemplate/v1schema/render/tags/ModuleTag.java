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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.tags;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.hubspot.jinjava.interpret.Context;
import com.hubspot.jinjava.interpret.InterpretException;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.interpret.TemplateStateException;
import com.hubspot.jinjava.interpret.TemplateSyntaxException;
import com.hubspot.jinjava.lib.tag.Tag;
import com.hubspot.jinjava.tree.TagNode;
import com.hubspot.jinjava.util.HelperStringTokenizer;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.NamedHashMap;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateModule;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.DefaultRenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderUtil;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Error;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ModuleTag implements Tag {
  private static final Splitter ON_EQUALS = Splitter.on("=");

  private Renderer renderer;
  private ObjectMapper objectMapper;

  public ModuleTag(Renderer renderer, ObjectMapper pipelineTemplateObjectMapper) {
    this.renderer = renderer;
    this.objectMapper = pipelineTemplateObjectMapper;
  }

  @Override
  public String getName() {
    return "module";
  }

  @Override
  public String interpret(TagNode tagNode, JinjavaInterpreter interpreter) {
    List<String> helper = collapseWhitespaceInTokenPairs(
      new HelperStringTokenizer(tagNode.getHelpers()).allTokens()
    );
    if (helper.isEmpty()) {
      throw new TemplateSyntaxException(tagNode.getMaster().getImage(), "Tag 'module' expects ID as first parameter: " + helper, tagNode.getLineNumber());
    }

    Context context = interpreter.getContext();
    String moduleId = helper.get(0);

    PipelineTemplate template = (PipelineTemplate) context.get("pipelineTemplate");
    if (template == null) {
      throw new TemplateStateException("Pipeline template missing from jinja context");
    }

    TemplateModule module = template.getModules().stream()
      .filter(m -> m.getId().equals(moduleId))
      .findFirst()
      .orElseThrow((Supplier<RuntimeException>) () -> new TemplateRenderException(String.format("Module does not exist by ID: %s", moduleId)));

    RenderContext moduleContext = new DefaultRenderContext(
      (String) context.get("application"),
      template,
      (Map<String, Object>) context.get("trigger")
    );
    moduleContext.setLocation("module:" + moduleId);

    // Assign parameters into the context
    Map<String, String> paramPairs = new HashMap<>();
    helper.subList(1, helper.size()).forEach(p -> {
      List<String> parts = ON_EQUALS.splitToList(p);
      if (parts.size() != 2) {
        throw new TemplateSyntaxException(tagNode.getMaster().getImage(), "Tag 'module' expects parameters to be in a 'key=value' format: " + helper, tagNode.getLineNumber());
      }
      paramPairs.put(parts.get(0), parts.get(1));
    });

    List<String> missing = new ArrayList<>();
    for (NamedHashMap var : module.getVariables()) {
      // First try to assign the variable from the context directly
      Object val = interpreter.resolveELExpression(var.getName(), tagNode.getLineNumber());
      if (val == null) {
        // Try to assign from a parameter (using the param value as a context key first, then as a literal)
        if (paramPairs.containsKey(var.getName())) {
          val = Optional.ofNullable(
            interpreter.resolveELExpression(paramPairs.get(var.getName()), tagNode.getLineNumber())
          ).orElse(paramPairs.get(var.getName()));
        }

        // If the val is still null, try to assign from a default value
        if (val == null && var.containsKey("defaultValue")) {
          val = var.get("defaultValue");
        }

        if (val == null) {
          missing.add(var.getName());
          continue;
        }
      }
      moduleContext.getVariables().put(var.getName(), val);
    }

    if (missing.size() > 0) {
      throw TemplateRenderException.fromError(
        new Error()
          .withMessage("Missing required variables in module")
          .withCause("'" + StringUtils.join(missing, "', '") + "' must be defined")
          .withLocation(moduleContext.getLocation())
          .withDetail("source", tagNode.getMaster().getImage())
      );
    }

    Object rendered;
    try {
      rendered = RenderUtil.deepRender(renderer, module.getDefinition(), moduleContext);
    } catch (InterpretException e) {
      throw TemplateRenderException.fromError(
        new Error()
          .withMessage("Failed rendering module")
          .withLocation(moduleContext.getLocation())
          .withDetail("source", tagNode.getMaster().getImage()),
        e
      );
    }

    if (rendered instanceof CharSequence) {
      return (String) rendered;
    }

    try {
      return objectMapper.writeValueAsString(rendered);
    } catch (JsonProcessingException e) {
      throw TemplateRenderException.fromError(
        new Error()
          .withMessage("Failed rendering module as JSON")
          .withLocation(moduleContext.getLocation())
          .withDetail("source", tagNode.getMaster().getImage()),
        e
      );
    }
  }

  @Override
  public String getEndTagName() {
    return null;
  }

  /**
   * Look at this ungodly code. It's gross. Thanks to poor foresight, we tokenize on
   * whitespace, which can break concatenation, and definitely breaks usage of filters.
   * Sooo, we take the tokenized module definition and best-guess our way through collapsing
   * whitespace to arrive at the real key/value pairs that we later parse for populating
   * the module's internal context.
   */
  private static List<String> collapseWhitespaceInTokenPairs(List<String> tokens) {
    List<String> combinedTokens = new ArrayList<>();
    combinedTokens.add(tokens.get(0));

    StringBuilder buffer = new StringBuilder();
    // idx 0 is `moduleName`. Skip that guy.
    for (int i = 1; i < tokens.size(); i++) {
      String token = tokens.get(i);
      if (token.contains("=")) {
        if (buffer.length() > 0) {
          combinedTokens.add(buffer.toString());
        }
        buffer = new StringBuilder();
        combinedTokens.add(token);
      } else {
        String lastToken = combinedTokens.get(combinedTokens.size() - 1);
        if (lastToken.contains("=") && !lastToken.endsWith(",")) {
          buffer.append(combinedTokens.remove(combinedTokens.size() - 1));
        }
        buffer.append(token);
      }
    }

    if (buffer.length() > 0) {
      int i = combinedTokens.size() - 1;
      combinedTokens.set(i, combinedTokens.get(i) + buffer.toString());
    }

    return combinedTokens.stream()
      .map(ModuleTag::removeTrailingCommas)
      .collect(Collectors.toList());
  }

  private static String removeTrailingCommas(String token) {
    if (token.endsWith(",")) {
      return token.substring(0, token.length()-1);
    }
    return token;
  }
}
