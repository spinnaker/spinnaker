/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class EditCanaryCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Edit Spinnaker's canary analysis settings.";

  @Parameter(
      names = "--redux-logger-enabled",
      arity = 1,
      description =
          "Whether or not to enable redux logging in the canary module in deck (*Default*: `true`).")
  private Boolean reduxLoggerEnabled;

  @Parameter(
      names = "--default-metrics-account",
      arity = 1,
      description = "Name of metrics account to use by default.")
  private String defaultMetricsAccount;

  @Parameter(
      names = "--default-storage-account",
      arity = 1,
      description = "Name of storage account to use by default.")
  private String defaultStorageAccount;

  @Parameter(
      names = "--default-judge",
      arity = 1,
      description = "Name of canary judge to use by default (*Default*: `NetflixACAJudge-v1.0`).")
  private String defaultJudge;

  @Parameter(
      names = "--default-metrics-store",
      arity = 1,
      description =
          "Name of metrics store to use by default (e.g. atlas, datadog, prometheus, stackdriver).")
  private String defaultMetricsStore;

  @Parameter(
      names = "--stages-enabled",
      arity = 1,
      description = "Whether or not to enable canary stages in deck (*Default*: `true`).")
  private Boolean stagesEnabled;

  @Parameter(
      names = "--atlasWebComponentsUrl",
      arity = 1,
      description = "Location of web components to use for Atlas metric configuration.")
  private String atlasWebComponentsUrl;

  @Parameter(
      names = "--templates-enabled",
      arity = 1,
      description =
          "Whether or not to enable custom filter templates for canary configs in deck (*Default*: `true`).")
  private Boolean templatesEnabled;

  @Parameter(
      names = "--show-all-configs-enabled",
      arity = 1,
      description =
          "Whether or not to show all canary configs in deck, or just those scoped to the current application (*Default*: `true`).")
  private Boolean showAllConfigsEnabled;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    Canary canary =
        new OperationHandler<Canary>()
            .setFailureMesssage("Failed to get canary.")
            .setOperation(Daemon.getCanary(currentDeployment, false))
            .get();

    int originalHash = canary.hashCode();

    canary.setReduxLoggerEnabled(
        isSet(reduxLoggerEnabled) ? reduxLoggerEnabled : canary.isReduxLoggerEnabled());
    canary.setDefaultMetricsAccount(
        isSet(defaultMetricsAccount) ? defaultMetricsAccount : canary.getDefaultMetricsAccount());
    canary.setDefaultStorageAccount(
        isSet(defaultStorageAccount) ? defaultStorageAccount : canary.getDefaultStorageAccount());
    canary.setDefaultJudge(isSet(defaultJudge) ? defaultJudge : canary.getDefaultJudge());
    canary.setDefaultMetricsStore(
        isSet(defaultMetricsStore) ? defaultMetricsStore : canary.getDefaultMetricsStore());
    canary.setStagesEnabled(isSet(stagesEnabled) ? stagesEnabled : canary.isStagesEnabled());
    canary.setAtlasWebComponentsUrl(
        isSet(atlasWebComponentsUrl) ? atlasWebComponentsUrl : canary.getAtlasWebComponentsUrl());
    canary.setTemplatesEnabled(
        isSet(templatesEnabled) ? templatesEnabled : canary.isTemplatesEnabled());
    canary.setShowAllConfigsEnabled(
        isSet(showAllConfigsEnabled) ? showAllConfigsEnabled : canary.isShowAllConfigsEnabled());

    if (originalHash == canary.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setOperation(Daemon.setCanary(currentDeployment, !noValidate, canary))
        .setFailureMesssage("Failed to edit canary analysis settings.")
        .setSuccessMessage("Successfully edited canary analysis settings.")
        .get();
  }
}
