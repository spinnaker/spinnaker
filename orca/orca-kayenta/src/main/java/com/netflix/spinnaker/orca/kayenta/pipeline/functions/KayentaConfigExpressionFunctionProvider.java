/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kayenta.pipeline.functions;

import com.google.common.base.Strings;
import com.netflix.spinnaker.kork.api.expressions.ExpressionFunctionProvider;
import com.netflix.spinnaker.kork.expressions.SpelHelperFunctionException;
import com.netflix.spinnaker.orca.kayenta.KayentaCanaryConfig;
import com.netflix.spinnaker.orca.kayenta.KayentaService;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class KayentaConfigExpressionFunctionProvider implements ExpressionFunctionProvider {

  // Static because it's needed during expression eval (which is a static)
  private static KayentaService kayentaService;

  public KayentaConfigExpressionFunctionProvider(KayentaService kayentaService) {
    this.kayentaService = kayentaService;
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
            "canaryConfigNameToId",
            "Look up the canary config ID for the given config name and app",
            new FunctionParameter(String.class, "name", "The name of the config"),
            new FunctionParameter(String.class, "app", "The name of the app")));
  }

  /**
   * SpEL expression used to convert the name of a canary config to the ID.
   *
   * @param name Name of the config.
   * @param app Application which owns the config.
   * @return The ID of the config which corresponds to the name and app provided.
   */
  public static String canaryConfigNameToId(String name, String app) {
    if (Strings.isNullOrEmpty(name)) {
      throw new SpelHelperFunctionException(
          "Config name is a required field for the canaryConfigNameToId function.");
    }
    if (Strings.isNullOrEmpty(app)) {
      throw new SpelHelperFunctionException(
          "App is a required field for the canaryConfigNameToId function.");
    }

    List<KayentaCanaryConfig> configs = kayentaService.getAllCanaryConfigs();
    Optional<KayentaCanaryConfig> found =
        configs.stream()
            .filter(c -> c.getApplications().contains(app))
            .filter(c -> c.getName().equals(name))
            .findFirst();
    if (found.isPresent()) {
      return found.get().getId();
    }

    throw new SpelHelperFunctionException(
        "Unable to find config with name " + name + " for app " + app + ".");
  }
}
