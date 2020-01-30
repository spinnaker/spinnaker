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

package com.netflix.spinnaker.halyard.cli.command.v1.deploy;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.AbstractRemoteActionCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeployOption;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class ApplyDeployCommand extends AbstractRemoteActionCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "apply";

  private static final String deployParamWarning =
      "WARNING: This is considered an advanced command, and may break your deployment if used incorrectly.\n\n";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription =
      "Deploy or update the currently configured instance of Spinnaker to a selected environment.";

  @Getter(AccessLevel.PUBLIC)
  private String longDescription =
      String.join(
          "",
          "This command deploys Spinnaker, depending on how you've configured your deployment. ",
          "Local deployments are applied to the machine running Halyard, whereas Distributed deployments are applied to a cloud provider. ",
          "Local deployments are subject to downtime during updates, whereas Distributed deployments are deployed and updated via a headless ",
          "'bootstrap' deployment of Spinnaker, and don't suffer downtime.");

  @Parameter(
      names = "--omit-config",
      description =
          deployParamWarning
              + "This guarantees that no configuration will be generated for this deployment. This is useful for staging artifacts "
              + "for later manual configuration.")
  boolean omitConfig;

  @Parameter(
      names = "--flush-infrastructure-caches",
      description =
          deployParamWarning
              + "This flushes infrastructure caches (clouddriver) after the deploy succeeds.")
  boolean flushInfrastructureCaches;

  @Parameter(
      names = "--prep-only",
      description =
          "This does just the prep work, and not the actual deployment. Only useful at the moment if you want to just clone the "
              + "repositories for a localgit setup.")
  boolean prepOnly;

  @Parameter(
      names = "--service-names",
      description = "When supplied, only install or update the specified Spinnaker services.",
      variableArity = true)
  List<String> serviceNames = new ArrayList<>();

  @Parameter(
      names = "--exclude-service-names",
      description = "When supplied, do not install or update the specified Spinnaker services.",
      variableArity = true)
  List<String> excludeServiceNames = new ArrayList<>();

  @Parameter(
      names = "--delete-orphaned-services",
      description =
          "Deletes unused Spinnaker services after the deploy succeeds. "
              + "This flag is not allowed when using the --service-names or --exclude-service-names arg.")
  boolean deleteOrphanedServices;

  @Parameter(
      names = "--wait-for-completion",
      description =
          "When supplied, wait for all containers to be ready before returning (only applies to Kubernetes V2 provider).")
  boolean waitForCompletion;

  @Parameter(
      names = "--wait-for-completion-timeout-minutes",
      description = "Specify timeout for deploy apply command.")
  Integer waitForCompletionTimeoutMinutes;

  @Override
  protected OperationHandler<RemoteAction> getRemoteAction() {
    List<DeployOption> deployOptions = new ArrayList<>();
    if (omitConfig) {
      deployOptions.add(DeployOption.OMIT_CONFIG);
    }
    if (flushInfrastructureCaches) {
      deployOptions.add(DeployOption.FLUSH_INFRASTRUCTURE_CACHES);
    }
    if (deleteOrphanedServices) {
      deployOptions.add(DeployOption.DELETE_ORPHANED_SERVICES);
    }
    if (waitForCompletion) {
      deployOptions.add(DeployOption.WAIT_FOR_COMPLETION);
    }

    OperationHandler<RemoteAction> prepHandler =
        new OperationHandler<RemoteAction>()
            .setFailureMesssage("Failed to prep Spinnaker deployment")
            .setSuccessMessage("Preparation complete... deploying Spinnaker")
            .setOperation(
                Daemon.prepDeployment(
                    getCurrentDeployment(), !noValidate, serviceNames, excludeServiceNames));

    if (prepOnly) {
      return prepHandler;
    } else {
      runRemoteAction(prepHandler);

      return new OperationHandler<RemoteAction>()
          .setFailureMesssage("Failed to deploy Spinnaker.")
          .setSuccessMessage("Run `hal deploy connect` to connect to Spinnaker.")
          .setOperation(
              Daemon.deployDeployment(
                  getCurrentDeployment(),
                  false,
                  deployOptions,
                  serviceNames,
                  excludeServiceNames,
                  waitForCompletionTimeoutMinutes));
    }
  }
}
