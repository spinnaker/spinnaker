/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate;

import com.netflix.spinnaker.kork.web.exceptions.ValidationException;
import com.netflix.spinnaker.orca.extensionpoint.pipeline.ExecutionPreprocessor;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class V2Util {
  public static Map<String, Object> planPipeline(ContextParameterProcessor contextParameterProcessor,
                                                 List<ExecutionPreprocessor> pipelinePreprocessors,
                                                 Map<String, Object> pipeline) {
    // TODO(jacobkiefer): Excise the logic in OperationsController that requires plan to avoid resolving artifacts.
    pipeline.put("plan", true); // avoid resolving artifacts

    Map<String, Object> finalPipeline = pipeline;
    List<ExecutionPreprocessor> preprocessors = pipelinePreprocessors
      .stream()
      .filter(p -> p.supports(finalPipeline, ExecutionPreprocessor.Type.PIPELINE))
      .collect(Collectors.toList());
    for (ExecutionPreprocessor pp : preprocessors) {
      pipeline = pp.process(pipeline);
    }

    List<Map<String, Object>> pipelineErrors = (List<Map<String, Object>>) pipeline.get("errors");
    if (pipelineErrors != null && !pipelineErrors.isEmpty()) {
      throw new ValidationException(
        "Pipeline template is invalid", pipelineErrors);
    }

    Map<String, Object> augmentedContext = new HashMap<>();
    augmentedContext.put("trigger", pipeline.get("trigger"));
    augmentedContext.put("templateVariables", pipeline.getOrDefault("templateVariables", Collections.EMPTY_MAP));
    Map<String, Object> spelEvaluatedPipeline = contextParameterProcessor.process(
      pipeline, augmentedContext, true);

    Map<String, Object> expressionEvalSummary =
      (Map<String, Object>) spelEvaluatedPipeline.get("expressionEvaluationSummary");
    if (expressionEvalSummary != null) {
      List<String> failedTemplateVars = expressionEvalSummary.entrySet()
        .stream()
        .map(e -> e.getKey())
        .filter(v -> v.startsWith("templateVariables."))
        .map(v -> v.replace("templateVariables.", ""))
        .collect(Collectors.toList());

      if (failedTemplateVars.size() > 0) {
        throw new ValidationException(
          "Missing template variable values for the following variables: %s", failedTemplateVars);
      }
    }

    return spelEvaluatedPipeline;
  }
}
