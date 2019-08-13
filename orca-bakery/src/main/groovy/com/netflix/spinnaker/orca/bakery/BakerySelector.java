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

package com.netflix.spinnaker.orca.bakery;

import static com.netflix.spinnaker.orca.bakery.BakerySelector.ConfigFields.*;

import com.netflix.spinnaker.kork.web.selector.v2.SelectableService;
import com.netflix.spinnaker.kork.web.selector.v2.SelectableService.Parameter;
import com.netflix.spinnaker.orca.bakery.api.BakeryService;
import com.netflix.spinnaker.orca.bakery.config.BakeryConfigurationProperties;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.*;
import java.util.function.Function;

public class BakerySelector {
  // Temporary flag in stage context allowing to specify if the bakery should be selectable
  private static final String SELECT_BAKERY = "selectBakery";

  private SelectableService<BakeryService> selectableService;
  private final BakeryService defaultService;
  private final Map<String, Object> defaultConfig;
  private final boolean selectBakery;

  public BakerySelector(
      BakeryService defaultBakeryService,
      BakeryConfigurationProperties bakeryConfigurationProperties,
      Function<String, BakeryService> getBakeryServiceByUrlFx) {
    this.defaultService = defaultBakeryService;
    this.defaultConfig = getDefaultConfig(bakeryConfigurationProperties);
    this.selectableService =
        getSelectableService(bakeryConfigurationProperties.getBaseUrls(), getBakeryServiceByUrlFx);
    this.selectBakery = bakeryConfigurationProperties.isSelectorEnabled();
  }

  /**
   * Selects a bakery based on {@link SelectableFields} from the context
   *
   * @param stage bake stage
   * @return a bakery service with associated configuration
   */
  public SelectableService.SelectedService<BakeryService> select(Stage stage) {
    if (!shouldSelect(stage)) {
      return new SelectableService.SelectedService<>(defaultService, defaultConfig, null);
    }

    final String application = stage.getExecution().getApplication();
    final String user =
        Optional.ofNullable(stage.getExecution().getAuthentication())
            .map(Execution.AuthenticationDetails::getUser)
            .orElse("unknown");
    final List<Parameter> parameters = new ArrayList<>();

    stage
        .getContext()
        .forEach(
            (key, value) -> {
              Optional<String> paramName =
                  SelectableFields.contextFields.stream().filter(f -> f.equals(key)).findFirst();
              paramName.ifPresent(
                  name ->
                      parameters.add(
                          new Parameter()
                              .withName(name)
                              .withValues(Collections.singletonList(value))));
            });

    parameters.add(
        new Parameter()
            .withName(SelectableFields.authenticatedUser)
            .withValues(Collections.singletonList(user)));
    parameters.add(
        new Parameter()
            .withName(SelectableFields.application)
            .withValues(Collections.singletonList(application)));
    return selectableService.byParameters(parameters);
  }

  private Map<String, Object> getDefaultConfig(BakeryConfigurationProperties properties) {
    final Map<String, Object> config = new HashMap<>();
    config.put(roscoApisEnabled, properties.isRoscoApisEnabled());
    config.put(allowMissingPackageInstallation, properties.isAllowMissingPackageInstallation());
    config.put(extractBuildDetails, properties.isExtractBuildDetails());
    return config;
  }

  private SelectableService<BakeryService> getSelectableService(
      List<SelectableService.BaseUrl> baseUrls,
      Function<String, BakeryService> getBakeryServiceByUrlFx) {
    if (baseUrls == null) {
      return null;
    }

    return new SelectableService<>(
        baseUrls, defaultService, defaultConfig, getBakeryServiceByUrlFx);
  }

  private boolean shouldSelect(Stage stage) {
    if (selectableService == null || selectableService.getServices().size() < 2) {
      return false;
    }

    if (selectBakery) {
      return true;
    }

    return (Boolean) stage.getContext().getOrDefault(SELECT_BAKERY, false);
  }

  interface SelectableFields {
    // Allows to shard the bakery based on the current user
    String authenticatedUser = "authenticatedUser";

    // Allows to shard the bakery based on current application
    String application = "application";

    // Allows to shard the bakery based on common bake stage fields
    List<String> contextFields =
        Arrays.asList("cloudProvider", "region", "baseOS", "cloudProviderType");
  }

  interface ConfigFields {
    String roscoApisEnabled = "roscoApisEnabled";
    String allowMissingPackageInstallation = "allowMissingPackageInstallation";
    String extractBuildDetails = "extractBuildDetails";
  }
}
