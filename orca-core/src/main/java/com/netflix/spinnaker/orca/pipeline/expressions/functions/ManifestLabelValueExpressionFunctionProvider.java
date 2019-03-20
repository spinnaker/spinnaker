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

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.expressions.ExpressionFunctionProvider;
import com.netflix.spinnaker.orca.pipeline.expressions.SpelHelperFunctionException;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.*;

import static java.lang.String.format;

@Component
public class ManifestLabelValueExpressionFunctionProvider implements ExpressionFunctionProvider {
  @Nullable
  @Override
  public String getNamespace() {
    return null;
  }

  @NotNull
  @Override
  public Collection<FunctionDefinition> getFunctions() {
    return Collections.singletonList(
      new FunctionDefinition("manifestLabelValue", Arrays.asList(
        new FunctionParameter(Execution.class, "execution", "The execution to search for stages within"),
        new FunctionParameter(String.class, "stageName", "Name of a deployManifest stage to find"),
        new FunctionParameter(String.class, "kind", "The kind of manifest to find"),
        new FunctionParameter(String.class, "labelKey", "The key of the label to find")
      ))
    );
  }

  /**
   * Gets value of given label key in manifest of given kind deployed by stage of given name
   * @param execution #root.execution
   * @param stageName the name of a `deployManifest` stage to find
   * @param kind the kind of manifest to find
   * @param labelKey the key of the label to find
   * @return the label value
   */
  public static String manifestLabelValue(Execution execution, String stageName, String kind, String labelKey) {
    List<String> validKinds = Arrays.asList("Deployment", "ReplicaSet");
    if (!validKinds.contains(kind)) {
      throw new IllegalArgumentException("Only Deployments and ReplicaSets are valid kinds for this function");
    }

    if (labelKey == null) {
      throw new IllegalArgumentException("A labelKey is required for this function");
    }

    Optional<Stage> stage = execution.getStages()
      .stream()
      .filter(s -> s.getName().equals(stageName) && s.getType().equals("deployManifest") && s.getStatus() == ExecutionStatus.SUCCEEDED)
      .findFirst();

    if (!stage.isPresent()) {
      throw new SpelHelperFunctionException("A valid Deploy Manifest stage name is required for this function");
    }

    List<Map> manifests = (List<Map>) stage.get().getContext().get("manifests");

    if (manifests == null || manifests.size() == 0) {
      throw new SpelHelperFunctionException("No manifest could be found in the context of the specified stage");
    }

    Optional<Map> manifestOpt = manifests.stream()
      .filter(m -> m.get("kind").equals(kind))
      .findFirst();

    if (!manifestOpt.isPresent()) {
      throw new SpelHelperFunctionException(format("No manifest of kind %s could be found on the context of the specified stage", kind));
    }

    Map manifest = manifestOpt.get();
    String labelPath = format("$.spec.template.metadata.labels.%s", labelKey);
    String labelValue;

    try {
      labelValue = JsonPath.read(manifest, labelPath);
    } catch (PathNotFoundException e) {
      throw new SpelHelperFunctionException("No label of specified key found on matching manifest spec.template.metadata.labels");
    }

    return labelValue;
  }
}
