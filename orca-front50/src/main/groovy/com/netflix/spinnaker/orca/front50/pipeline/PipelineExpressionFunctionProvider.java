/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.front50.pipeline;

import static java.lang.String.format;

import com.google.common.base.Strings;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.expressions.ExpressionFunctionProvider;
import com.netflix.spinnaker.kork.expressions.SpelHelperFunctionException;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class PipelineExpressionFunctionProvider implements ExpressionFunctionProvider {
  // Static because it's needed during expression eval (which is a static)
  private static Front50Service front50Service = null;

  PipelineExpressionFunctionProvider(Optional<Front50Service> front50Service) {
    front50Service.ifPresent(
        service -> PipelineExpressionFunctionProvider.front50Service = service);
  }

  @Nullable
  @Override
  public String getNamespace() {
    return null;
  }

  @NotNull
  @Override
  public Functions getFunctions() {
    return new Functions(
        new FunctionDefinition(
            "pipelineId",
            new FunctionParameter(
                Execution.class,
                "execution",
                "The execution containing the currently executing stage"),
            new FunctionParameter(
                String.class, "pipelineName", "A valid stage reference identifier")));
  }

  /**
   * Function to convert pipeline name to pipeline ID (within current application)
   *
   * @param execution the current execution
   * @param pipelineName name of the pipeline to lookup
   * @return the id of the pipeline or null if pipeline not found
   */
  public static String pipelineId(Execution execution, String pipelineName) {
    if (Strings.isNullOrEmpty(pipelineName)) {
      throw new SpelHelperFunctionException(
          "pipelineName must be specified for function #pipelineId");
    }

    if (front50Service == null) {
      throw new SpelHelperFunctionException(
          "front50 service is missing. It's required when using #pipelineId function");
    }

    try {
      String currentApplication = execution.getApplication();

      RetrySupport retrySupport = new RetrySupport();
      Map<String, Object> pipeline =
          retrySupport.retry(
              () ->
                  front50Service.getPipelines(currentApplication).stream()
                      .filter(p -> pipelineName.equals(p.getOrDefault("name", null)))
                      .findFirst()
                      .orElse(null),
              3,
              1000,
              true);

      if (pipeline == null) {
        throw new SpelHelperFunctionException(
            format(
                "Pipeline with name '%s' could not be found on application %s",
                pipelineName, currentApplication));
      }

      return (String) pipeline.get("id");
    } catch (SpelHelperFunctionException e) {
      throw e;
    } catch (Exception e) {
      throw new SpelHelperFunctionException("Failed to evaluate #pipelineId function", e);
    }
  }
}
