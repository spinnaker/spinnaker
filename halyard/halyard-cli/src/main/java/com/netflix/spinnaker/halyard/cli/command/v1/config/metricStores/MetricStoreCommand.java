/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.metricStores;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStores;

@Parameters(separators = "=")
public abstract class MetricStoreCommand extends AbstractConfigCommand {
  public String getCommandName() {
    return getMetricStoreType().getId();
  }

  public abstract MetricStores.MetricStoreType getMetricStoreType();

  public String getShortDescription() {
    return "Configure your " + getMetricStoreType().getId() + " metric store.";
  }

  protected MetricStoreCommand() {
    registerSubcommand(
        new MetricStoreEnableDisableCommandBuilder()
            .setEnable(true)
            .setMetricStoreType(getMetricStoreType())
            .build());

    registerSubcommand(
        new MetricStoreEnableDisableCommandBuilder()
            .setEnable(false)
            .setMetricStoreType(getMetricStoreType())
            .build());
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String metricStoreType = getMetricStoreType().getId();

    new OperationHandler<MetricStore>()
        .setOperation(Daemon.getMetricStore(currentDeployment, metricStoreType, !noValidate))
        .setFailureMesssage("Failed to get " + metricStoreType + " method.")
        .setSuccessMessage("Configured " + metricStoreType + " method: ")
        .setFormat(AnsiFormatUtils.Format.STRING)
        .setUserFormatted(true)
        .get();
  }
}
