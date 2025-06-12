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

import com.google.common.base.Splitter;
import com.hubspot.jinjava.interpret.Context;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.interpret.TemplateSyntaxException;
import com.hubspot.jinjava.lib.tag.Tag;
import com.hubspot.jinjava.tree.TagNode;
import com.hubspot.jinjava.util.HelperStringTokenizer;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Error;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PipelineIdTag implements Tag {
  private static final String APPLICATION = "application";
  private static final String NAME = "name";
  private static final Splitter ON_EQUALS = Splitter.on("=");

  private final Front50Service front50Service;

  public PipelineIdTag(Front50Service front50Service) {
    this.front50Service = front50Service;
  }

  @Override
  public String interpret(TagNode tagNode, JinjavaInterpreter interpreter) {
    List<String> helper =
        new HelperStringTokenizer(tagNode.getHelpers()).splitComma(true).allTokens();
    if (helper.isEmpty()) {
      throw new TemplateSyntaxException(
          tagNode.getMaster().getImage(),
          "Tag 'pipelineId' expects at least a pipeline name: " + helper,
          tagNode.getLineNumber());
    }

    Map<String, String> paramPairs = new HashMap<>();
    helper.forEach(
        p -> {
          List<String> parts = ON_EQUALS.splitToList(p);
          if (parts.size() != 2) {
            throw new TemplateSyntaxException(
                tagNode.getMaster().getImage(),
                "Tag 'pipelineId' expects parameters to be in a 'key=value' format: " + helper,
                tagNode.getLineNumber());
          }

          paramPairs.put(parts.get(0), parts.get(1));
        });

    Context context = interpreter.getContext();

    String application =
        paramPairs
            .getOrDefault(APPLICATION, (String) context.get(APPLICATION))
            .replaceAll("^[\"\']|[\"\']$", "");
    application = checkContext(application, context);

    String name = paramPairs.get(NAME).replaceAll("^[\"\']|[\"\']$", "");
    name = checkContext(name, context);

    final String appName = application;
    List<Map<String, Object>> pipelines =
        AuthenticatedRequest.allowAnonymous(
            () ->
                Optional.ofNullable(
                        Retrofit2SyncCall.execute(front50Service.getPipelines(appName, false)))
                    .orElse(Collections.emptyList()));
    Map<String, Object> result = findPipeline(pipelines, application, name);
    return (String) result.get("id");
  }

  private String checkContext(String param, Context context) {
    Object var = context.get(param);

    if (var != null) {
      return (String) var;
    }

    return param;
  }

  private Map<String, Object> findPipeline(
      List<Map<String, Object>> pipelines, String application, String pipelineName) {
    return pipelines.stream()
        .filter(p -> p.get(NAME).equals(pipelineName))
        .findFirst()
        .orElseThrow(
            () ->
                TemplateRenderException.fromError(
                    new Error()
                        .withMessage(
                            String.format(
                                "Failed to find pipeline ID with name '%s' in application '%s'",
                                pipelineName, application))));
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
