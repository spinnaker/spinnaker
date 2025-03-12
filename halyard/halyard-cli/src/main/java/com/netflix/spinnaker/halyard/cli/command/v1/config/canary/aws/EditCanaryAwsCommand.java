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

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary.aws;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.AbstractEditCanaryServiceIntegrationCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.account.CanaryUtils;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.canary.aws.AwsCanaryServiceIntegration;

@Parameters(separators = "=")
public class EditCanaryAwsCommand extends AbstractEditCanaryServiceIntegrationCommand {

  @Override
  protected String getServiceIntegration() {
    return "AWS";
  }

  @Parameter(
      names = "--s3-enabled",
      arity = 1,
      description = "Whether or not to enable S3 as a persistent store (*Default*: `false`).")
  private Boolean s3Enabled;

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

    AwsCanaryServiceIntegration awsCanaryServiceIntegration =
        (AwsCanaryServiceIntegration)
            CanaryUtils.getServiceIntegrationByClass(canary, AwsCanaryServiceIntegration.class);

    awsCanaryServiceIntegration.setS3Enabled(
        isSet(s3Enabled) ? s3Enabled : awsCanaryServiceIntegration.isS3Enabled());

    if (awsCanaryServiceIntegration.isS3Enabled()) {
      awsCanaryServiceIntegration
          .getAccounts()
          .forEach(
              a ->
                  a.getSupportedTypes()
                      .add(AbstractCanaryServiceIntegration.SupportedTypes.CONFIGURATION_STORE));
      awsCanaryServiceIntegration
          .getAccounts()
          .forEach(
              a ->
                  a.getSupportedTypes()
                      .add(AbstractCanaryServiceIntegration.SupportedTypes.OBJECT_STORE));
    } else {
      awsCanaryServiceIntegration
          .getAccounts()
          .forEach(
              a ->
                  a.getSupportedTypes()
                      .remove(AbstractCanaryServiceIntegration.SupportedTypes.CONFIGURATION_STORE));
      awsCanaryServiceIntegration
          .getAccounts()
          .forEach(
              a ->
                  a.getSupportedTypes()
                      .remove(AbstractCanaryServiceIntegration.SupportedTypes.OBJECT_STORE));
    }

    if (originalHash == canary.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setOperation(Daemon.setCanary(currentDeployment, !noValidate, canary))
        .setFailureMesssage("Failed to edit canary analysis AWS service integration settings.")
        .setSuccessMessage("Successfully edited canary analysis AWS service integration settings.")
        .get();
  }
}
