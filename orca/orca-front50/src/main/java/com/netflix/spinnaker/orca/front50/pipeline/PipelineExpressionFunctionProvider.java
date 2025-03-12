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
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
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
            "Lookup pipeline ID given the name of the pipeline in the current application",
            new FunctionParameter(
                PipelineExecution.class,
                "execution",
                "The execution containing the currently executing stage"),
            new FunctionParameter(
                String.class, "pipelineName", "A valid stage reference identifier")),
        new FunctionDefinition(
            "pipelineIdOrNull",
            "Lookup pipeline ID (or null if not found) given the name of the pipeline in the current application",
            new FunctionParameter(
                PipelineExecution.class,
                "execution",
                "The execution containing the currently executing stage"),
            new FunctionParameter(
                String.class, "pipelineName", "A valid stage reference identifier")),
        new FunctionDefinition(
            "pipelineIdInApplication",
            "Lookup pipeline ID (or null if not found) given the name of the pipeline and the name of the application",
            new FunctionParameter(
                PipelineExecution.class,
                "execution",
                "The execution containing the currently executing stage"),
            new FunctionParameter(String.class, "pipelineName", "The name of the pipeline"),
            new FunctionParameter(String.class, "applicationName", "The name of the application")));
  }

  /**
   * Function to convert pipeline name to pipeline ID (within current application)
   *
   * @param execution the current execution
   * @param pipelineName name of the pipeline to lookup
   * @return the id of the pipeline or exception if pipeline not found
   */
  public static String pipelineId(PipelineExecution execution, String pipelineName) {
    if (Strings.isNullOrEmpty(pipelineName)) {
      throw new SpelHelperFunctionException(
          "pipelineName must be specified for function #pipelineId");
    }

    String currentApplication = execution.getApplication();
    Map<String, Object> pipeline =
        searchForPipelineInApplication("pipelineId", currentApplication, pipelineName);

    if (pipeline == null) {
      throw new SpelHelperFunctionException(
          format(
              "Pipeline with name '%s' could not be found on application %s",
              pipelineName, currentApplication));
    }

    return (String) pipeline.get("id");
  }

  /**
   * Function to convert pipeline name to pipeline ID (within current application), will return Null
   * if pipeline ID not found
   *
   * @param execution the current execution
   * @param pipelineName name of the pipeline to lookup
   * @return the id of the pipeline or null if pipeline not found
   */
  public static String pipelineIdOrNull(PipelineExecution execution, String pipelineName) {
    try {
      return pipelineId(execution, pipelineName);
    } catch (SpelHelperFunctionException e) {
      if (e.getMessage().startsWith("Pipeline with name ")) {
        return null;
      }
      throw e;
    }
  }

  /**
   * Function to convert pipeline name/application name to pipeline ID
   *
   * @param execution the current execution
   * @param pipelineName name of the pipeline to lookup
   * @param applicationName name of the application
   * @return the id of the pipeline or null if pipeline not found
   */
  public static String pipelineIdInApplication(
      PipelineExecution execution, String pipelineName, String applicationName) {
    if (Strings.isNullOrEmpty(applicationName)) {
      throw new SpelHelperFunctionException(
          "applicationName must be specified for function #pipelineIdInApplication");
    }

    Map<String, Object> pipeline =
        searchForPipelineInApplication("pipelineIdInApplication", applicationName, pipelineName);

    if (pipeline == null) {
      return null;
    }

    return (String) pipeline.get("id");
  }

  private static Map<String, Object> searchForPipelineInApplication(
      String functionName, String applicationName, String pipelineName) {
    if (front50Service == null) {
      throw new SpelHelperFunctionException(
          String.format(
              "front50 service is missing. It's required when using #%s function", functionName));
    }

    try {
      RetrySupport retrySupport = new RetrySupport();
      return retrySupport.retry(
          () ->
              front50Service.getPipelines(applicationName).stream()
                  .filter(p -> pipelineName.equals(p.getOrDefault("name", null)))
                  .findFirst()
                  .orElse(null),
          3,
          1000,
          true);
    } catch (Exception e) {
      throw new SpelHelperFunctionException(
          String.format("Failed to evaluate #%s function", functionName), e);
    }
  }
}
