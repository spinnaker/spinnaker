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
  private String shortDescription = "Enable and disable Spinnaker feature flags.";

  @Parameter(
      names = "--chaos",
      description =
          "Enable Chaos Monkey support. For this to work, you'll need a running Chaos Monkey deployment. "
              + "Currently, Halyard doesn't configure Chaos Monkey for you; read more instructions here "
              + "https://github.com/Netflix/chaosmonkey/wiki.",
      arity = 1)
  private Boolean chaos = null;

  @Parameter(
      names = "--pipeline-templates",
      description =
          "Enable pipeline template support. Read more at https://github.com/spinnaker/dcd-spec.",
      arity = 1)
  private Boolean pipelineTemplates = null;

  @Parameter(
      names = "--artifacts",
      description =
          "Enable artifact support. Read more at https://spinnaker.io/reference/artifacts/",
      arity = 1)
  private Boolean artifacts = null;

  @Parameter(
      names = "--artifacts-rewrite",
      description =
          "Enable new artifact support. Read more at https://www.spinnaker.io/reference/artifacts-with-artifactsrewrite/",
      arity = 1)
  private Boolean artifactsRewrite = null;

  @Parameter(
      names = "--mine-canary",
      description =
          "Enable canary support. For this to work, you'll need a canary judge configured. "
              + "Currently, Halyard does not configure canary judge for you.",
      arity = 1)
  private Boolean mineCanary = null;

  @Parameter(
      names = "--infrastructure-stages",
      description =
          "Enable infrastructure stages. Allows for creating Load Balancers as part of pipelines.",
      arity = 1)
  private Boolean infrastructureStages = null;

  @Parameter(
      names = "--appengine-container-image-url-deployments",
      description = "Enable appengine deployments using a container image URL from gcr.io.",
      arity = 1)
  private Boolean appengineContainerImageUrlDeployments = null;

  @Parameter(names = "--travis", description = "Enable the Travis CI stage.", arity = 1)
  private Boolean travis = null;

  @Parameter(names = "--wercker", description = "Enable the Wercker CI stage.", arity = 1)
  private Boolean wercker = null;

  @Parameter(
      names = "--gremlin",
      description = "Enable Gremlin fault-injection support.",
      arity = 1)
  private Boolean gremlin = null;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    Features features =
        new OperationHandler<Features>()
            .setOperation(Daemon.getFeatures(currentDeployment, false))
            .setFailureMesssage("Failed to load features.")
            .get();

    int originalHash = features.hashCode();

    features.setChaos(chaos != null ? chaos : features.isChaos());
    features.setPipelineTemplates(
        pipelineTemplates != null ? pipelineTemplates : features.getPipelineTemplates());
    features.setArtifacts(artifacts != null ? artifacts : features.getArtifacts());
    features.setArtifactsRewrite(
        artifactsRewrite != null ? artifactsRewrite : features.getArtifactsRewrite());
    features.setMineCanary(mineCanary != null ? mineCanary : features.getMineCanary());
    features.setInfrastructureStages(
        infrastructureStages != null ? infrastructureStages : features.getInfrastructureStages());
    features.setAppengineContainerImageUrlDeployments(
        appengineContainerImageUrlDeployments != null
            ? appengineContainerImageUrlDeployments
            : features.getAppengineContainerImageUrlDeployments());
    features.setTravis(travis != null ? travis : features.getTravis());
    features.setWercker(wercker != null ? wercker : features.getWercker());
    features.setGremlin(gremlin != null ? gremlin : features.getGremlin());

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
