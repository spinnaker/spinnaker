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
package com.netflix.spinnaker.orca.pipeline.expressions.functions;

import static java.util.Collections.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.expressions.ExpressionFunctionProvider;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class DeployedServerGroupsExpressionFunctionProvider implements ExpressionFunctionProvider {

  private static List<String> DEPLOY_STAGE_NAMES =
      Arrays.asList("deploy", "createServerGroup", "cloneServerGroup", "rollingPush");

  @Nullable
  @Override
  public String getNamespace() {
    return null;
  }

  @NotNull
  @Override
  public Collection<FunctionDefinition> getFunctions() {
    return singletonList(
        new FunctionDefinition(
            "deployedServerGroups",
            Arrays.asList(
                new FunctionParameter(
                    Execution.class, "execution", "The execution to search for stages within"),
                new FunctionParameter(
                    String[].class, "ids", "A list of stage name or stage IDs to search"))));
  }

  public static List<Map<String, Object>> deployedServerGroups(Execution execution, String... id) {
    List<Map<String, Object>> deployedServerGroups = new ArrayList<>();
    execution.getStages().stream()
        .filter(matchesDeployedStage(id))
        .forEach(
            stage -> {
              String region = (String) stage.getContext().get("region");
              if (region == null) {
                Map<String, Object> availabilityZones =
                    (Map<String, Object>) stage.getContext().get("availabilityZones");
                if (availabilityZones != null) {
                  region = availabilityZones.keySet().iterator().next();
                }
              }

              if (region != null) {
                Map<String, Object> deployDetails = new HashMap<>();
                deployDetails.put("account", stage.getContext().get("account"));
                deployDetails.put("capacity", stage.getContext().get("capacity"));
                deployDetails.put("parentStage", stage.getContext().get("parentStage"));
                deployDetails.put("region", region);
                List<Map> existingDetails = (List<Map>) stage.getContext().get("deploymentDetails");
                if (existingDetails != null) {
                  existingDetails.stream()
                      .filter(d -> deployDetails.get("region").equals(d.get("region")))
                      .forEach(deployDetails::putAll);
                }

                List<Map> serverGroups =
                    (List<Map>) ((Map) stage.getContext().get("deploy.server.groups")).get(region);
                if (serverGroups != null) {
                  deployDetails.put("serverGroup", serverGroups.get(0));
                }

                DeploymentContext deploymentContext = stage.mapTo(DeploymentContext.class);
                List<Map<String, Object>> deployments =
                    Optional.ofNullable(deploymentContext.tasks).orElse(emptyList()).stream()
                        .flatMap(
                            task -> Optional.ofNullable(task.results).orElse(emptyList()).stream())
                        .flatMap(
                            result ->
                                Optional.ofNullable(result.deployments).orElse(emptyList())
                                    .stream())
                        .collect(Collectors.toList());
                deployDetails.put("deployments", deployments);

                deployedServerGroups.add(deployDetails);
              }
            });

    return deployedServerGroups;
  }

  static class DeploymentContext {
    @JsonProperty("kato.tasks")
    List<KatoTasks> tasks;
  }

  static class KatoTasks {
    @JsonProperty("resultObjects")
    List<ResultObject> results;
  }

  static class ResultObject {
    @JsonProperty("deployments")
    List<Map<String, Object>> deployments;
  }

  private static Predicate<Stage> matchesDeployedStage(String... id) {
    List<String> idsOrNames = Arrays.asList(id);
    if (!idsOrNames.isEmpty()) {
      return stage ->
          DEPLOY_STAGE_NAMES.contains(stage.getType())
              && stage.getContext().containsKey("deploy.server.groups")
              && stage.getStatus() == ExecutionStatus.SUCCEEDED
              && (idsOrNames.contains(stage.getName()) || idsOrNames.contains(stage.getId()));
    } else {
      return stage ->
          DEPLOY_STAGE_NAMES.contains(stage.getType())
              && stage.getContext().containsKey("deploy.server.groups")
              && stage.getStatus() == ExecutionStatus.SUCCEEDED;
    }
  }
}
