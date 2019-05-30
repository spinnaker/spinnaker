/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.orca.cf.pipeline.expressions.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TreeTraversingParser;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.expressions.ExpressionFunctionProvider;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class ServiceKeyExpressionFunctionProvider implements ExpressionFunctionProvider {
  private static final String CREATE_SERVICE_KEY_STAGE_NAME = "createServiceKey";
  private static final ObjectMapper objectMapper = OrcaObjectMapper.getInstance();

  @Nullable
  @Override
  public String getNamespace() {
    return null;
  }

  @NotNull
  @Override
  public Collection<FunctionDefinition> getFunctions() {
    return Collections.singletonList(
        new FunctionDefinition(
            "cfServiceKey",
            Arrays.asList(
                new FunctionParameter(
                    Execution.class,
                    "execution",
                    "The execution within which to search for stages"),
                new FunctionParameter(
                    String.class, "idOrName", "A stage name or stage ID to match"))));
  }

  public static Map<String, Object> cfServiceKey(Execution execution, String idOrName) {
    return execution.getStages().stream()
        .filter(matchesServiceKeyStage(idOrName))
        .findFirst()
        .map(
            stage -> {
              Map<String, Object> serviceKeyDetails = new HashMap<>();

              Optional.ofNullable(stage.getContext().get("kato.tasks"))
                  .ifPresent(
                      k -> {
                        List<Map<String, Object>> katoTasks = (List<Map<String, Object>>) k;
                        try {
                          ServiceKeyKatoTask katoTask =
                              objectMapper.readValue(
                                  new TreeTraversingParser(
                                      objectMapper.valueToTree(katoTasks.get(0)), objectMapper),
                                  ServiceKeyKatoTask.class);
                          serviceKeyDetails.putAll(
                              katoTask.getResultObjects().get(0).getServiceKey());
                        } catch (IOException e) {
                        }
                      });

              return serviceKeyDetails;
            })
        .orElse(Collections.emptyMap());
  }

  private static Predicate<Stage> matchesServiceKeyStage(String idOrName) {
    return stage ->
        CREATE_SERVICE_KEY_STAGE_NAME.equals(stage.getType())
            && stage.getStatus() == ExecutionStatus.SUCCEEDED
            && (Objects.equals(idOrName, stage.getName())
                || Objects.equals(idOrName, stage.getId()));
  }

  @Data
  private static class ServiceKeyKatoTask {
    private List<ServiceKeyResult> resultObjects;

    @Data
    private static class ServiceKeyResult {
      private Map<String, Object> serviceKey;
    }
  }
}
