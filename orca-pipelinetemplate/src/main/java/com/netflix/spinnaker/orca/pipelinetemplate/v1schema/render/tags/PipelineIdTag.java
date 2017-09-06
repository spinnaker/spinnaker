/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import com.hubspot.jinjava.interpret.Context;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.interpret.TemplateSyntaxException;
import com.hubspot.jinjava.lib.tag.Tag;
import com.hubspot.jinjava.tree.TagNode;
import com.hubspot.jinjava.util.HelperStringTokenizer;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Error;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PipelineIdTag implements Tag {
  private static final String APPLICATION = "application";
  private static final String NAME = "name";

  private final Front50Service front50Service;

  public PipelineIdTag(Front50Service front50Service) {
    this.front50Service = front50Service;
  }

  @Override
  public String interpret(TagNode tagNode, JinjavaInterpreter interpreter) {
    List<String> helper = new HelperStringTokenizer(tagNode.getHelpers()).splitComma(true).allTokens();
    if (helper.isEmpty()) {
      throw new TemplateSyntaxException(tagNode.getMaster().getImage(), "Tag 'pipelineId' expects at least a pipeline name: " + helper, tagNode.getLineNumber());
    }

    Map<String, String> paramPairs = new HashMap<>();
    helper.forEach(p -> {
      String[] parts = p.split("=");
      if (parts.length != 2) {
        throw new TemplateSyntaxException(tagNode.getMaster().getImage(), "Tag 'pipelineId' expects parameters to be in a 'key=value' format: " + helper, tagNode.getLineNumber());
      }

      paramPairs.put(parts[0], parts[1]);
    });

    Context context = interpreter.getContext();
    String application = paramPairs.getOrDefault(APPLICATION, (String) context.get(APPLICATION)).replaceAll("^[\"\']|[\"\']$", "");
    String name = paramPairs.get(NAME).replaceAll("^[\"\']|[\"\']$", "");

    if (name == null || application == null) {
      throw new TemplateSyntaxException(tagNode.getMaster().getImage(), "Tag 'pipelineId' is missing required fields: " + helper, tagNode.getLineNumber());
    }

    List<Map<String, Object>> pipelines = Optional.ofNullable(front50Service.getPipelines(application, false)).orElse(Collections.emptyList());

    Map<String, Object> result = pipelines
      .stream()
      .filter(p -> p.get(NAME).equals(name))
      .findFirst()
      .orElseThrow(
        () -> TemplateRenderException.fromError(
          new Error()
            .withMessage(String.format("Failed to find pipeline ID with name '%s' in application '%s'", name, application)
        ))
      );

    return (String) result.get("id");
  }

  @Override
  public String getEndTagName() {
    return null;
  }

  @Override
  public String getName() {
    return "pipelineid";
  }
}
