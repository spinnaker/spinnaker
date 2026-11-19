/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.expressions;

import com.netflix.spinnaker.kork.expressions.ExpressionFunctionProvider;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class CloudResourcesExpressionFunctionProvider implements ExpressionFunctionProvider {
  // Static because it's needed during expression eval (which is a static)
  private static OortService oortService = null;

  public CloudResourcesExpressionFunctionProvider(OortService oortService) {
    CloudResourcesExpressionFunctionProvider.oortService = oortService;
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
            "getK8sManifestsByKindsAndLabels",
            "given kubernetes kinds (comma-separated) and labels, it returns all the manifests that match the search criteria",
            new FunctionParameter(
                String.class, "kinds", "kubernetes resource kinds, comma-separated"),
            new FunctionParameter(String.class, "account", "kubernetes account"),
            new FunctionParameter(String.class, "namespace", "kubernetes namespace"),
            new FunctionParameter(Map.class, "map of labels", "Map of labels to search for")));
  }

  /**
   * Spel function to find all the kubernetes manifests that match the search criteria
   *
   * @param kinds Kubernetes resource kinds, comma separated
   * @param account Kubernetes account
   * @param namespace Kubernetes namespace
   * @param labels Map of labels to search for
   * @return list of manifests, empty if none match or they could not be retrieved
   */
  public static List<Manifest> getK8sManifestsByKindsAndLabels(
      String kinds, String account, String namespace, Map<String, String> labels) {
    if (oortService == null) {
      return List.of();
    }

    List<Manifest> manifests;
    try {
      manifests =
          Retrofit2SyncCall.execute(
              oortService.getManifestsByKindsAndLabels(
                  account, namespace, kinds, labels == null ? Map.of() : labels));
    } catch (Exception e) {
      return List.of();
    }

    return manifests;
  }
}
