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

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary.prometheus;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.AbstractEditCanaryServiceIntegrationCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.account.CanaryUtils;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.canary.prometheus.PrometheusCanaryServiceIntegration;

@Parameters(separators = "=")
public class EditCanaryPrometheusCommand extends AbstractEditCanaryServiceIntegrationCommand {

  @Override
  protected String getServiceIntegration() {
    return "Prometheus";
  }

  @Parameter(
      names = "--metadata-caching-interval-ms",
      description =
          "Number of milliseconds to wait in between caching the names of available metric types (for use in building canary configs; *Default*: `60000`).")
  private Long metadataCachingIntervalMS;

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

    PrometheusCanaryServiceIntegration prometheusCanaryServiceIntegration =
        (PrometheusCanaryServiceIntegration)
            CanaryUtils.getServiceIntegrationByClass(
                canary, PrometheusCanaryServiceIntegration.class);

    prometheusCanaryServiceIntegration.setMetadataCachingIntervalMS(
        isSet(metadataCachingIntervalMS)
            ? metadataCachingIntervalMS
            : prometheusCanaryServiceIntegration.getMetadataCachingIntervalMS());

    if (originalHash == canary.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setOperation(Daemon.setCanary(currentDeployment, !noValidate, canary))
        .setFailureMesssage(
            "Failed to edit canary analysis Prometheus service integration settings.")
        .setSuccessMessage(
            "Successfully edited canary analysis Prometheus service integration settings.")
        .get();
  }
}
