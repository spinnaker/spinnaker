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

package com.netflix.spinnaker.halyard.cli.command.v1.config;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.metricStores.EditMetricStoresCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.metricStores.datadog.DatadogCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.metricStores.newrelic.NewrelicCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.metricStores.prometheus.PrometheusCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.metricStores.stackdriver.StackdriverCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStores;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class MetricStoresCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "metric-stores";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription =
      "Configure Spinnaker's metric stores. Metrics stores are used to store metrics for the various "
          + "Spinnaker micro-services. These metrics are not related in any way to canary deployments. The technologies backing both "
          + "are similar, but metrics stores are places to push metrics regarding Spinnaker metrics, whereas canary metrics stores "
          + "are used to pull metrics to analyze deployments. This configuration only affects the publishing of metrics against "
          + "whichever metric stores you enable (it can be more than one).";

  public MetricStoresCommand() {
    registerSubcommand(new EditMetricStoresCommand());
    registerSubcommand(new DatadogCommand());
    registerSubcommand(new NewrelicCommand());
    registerSubcommand(new PrometheusCommand());
    registerSubcommand(new StackdriverCommand());
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    new OperationHandler<MetricStores>()
        .setOperation(Daemon.getMetricStores(currentDeployment, !noValidate))
        .setFailureMesssage("Failed to configure metric stores.")
        .setSuccessMessage("Configured metric stores: ")
        .setFormat(AnsiFormatUtils.Format.STRING)
        .setUserFormatted(true)
        .get();
  }
}
