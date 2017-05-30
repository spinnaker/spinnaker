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
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Features;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class EditFeaturesCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String description = "Enable and disable Spinnaker feature flags.";

  @Parameter(
      names = "--chaos",
      description = "Enable Chaos Monkey support. For this to work, you'll need a running Chaos Monkey deployment. "
          + "Currently, Halyard doesn't configure Chaos Monkey for you; read more instructions here "
          + "https://github.com/Netflix/chaosmonkey/wiki.",
      arity = 1
  )
  private Boolean chaos = null;

  @Parameter(
      names = "--jobs",
      description = "Allow Spinnaker to run containers in Kubernetes and Titus as Job stages in pipelines.",
      arity = 1
  )
  private Boolean jobs = null;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    Features features = new OperationHandler<Features>()
        .setOperation(Daemon.getFeatures(currentDeployment, false))
        .setFailureMesssage("Failed to load features.")
        .get();

    int originalHash = features.hashCode();

    features.setChaos(chaos != null ? chaos : features.isChaos());
    features.setJobs(jobs != null ? jobs : features.isJobs());

    if (originalHash == features.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setOperation(Daemon.setFeatures(currentDeployment, !noValidate, features))
        .setSuccessMessage("Successfully updated features.")
        .setFailureMesssage("Failed to edit features.")
        .get();
  }
}
