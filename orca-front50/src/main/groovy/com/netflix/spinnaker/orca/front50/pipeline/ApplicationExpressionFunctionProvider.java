/*
 * Copyright 2020 Netflix, Inc.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.expressions.ExpressionFunctionProvider;
import com.netflix.spinnaker.kork.expressions.SpelHelperFunctionException;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.Application;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class ApplicationExpressionFunctionProvider implements ExpressionFunctionProvider {
  // Static because it's needed during expression eval (which is a static)
  private static Front50Service front50Service = null;
  private static ObjectMapper objectMapper = null;

  ApplicationExpressionFunctionProvider(
      ObjectMapper objectMapper, Optional<Front50Service> front50Service) {
    front50Service.ifPresent(
        service -> ApplicationExpressionFunctionProvider.front50Service = service);

    ApplicationExpressionFunctionProvider.objectMapper = objectMapper;
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
            "applicationMetadata",
            "Get application metadata for a specific application",
            new FunctionParameter(
                String.class, "applicationName", "Application to get metadata for")));
  }

  /**
   * Function to retrieve application metadata for a specific application
   *
   * @param applicationName the application name to look up metadata for
   * @return returns a map with application metadata
   */
  public static Map<String, Object> applicationMetadata(String applicationName) {
    if (front50Service == null) {
      throw new SpelHelperFunctionException(
          "front50 service is missing. It's required when using applicationMetadata function");
    }

    try {
      RetrySupport retrySupport = new RetrySupport();
      Application application =
          retrySupport.retry(() -> front50Service.get(applicationName), 3, 1000, true);

      return objectMapper.convertValue(application, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new SpelHelperFunctionException(
          String.format(
              "Application metadata for application '%s' could not be retrieved", applicationName),
          e);
    }
  }
}
