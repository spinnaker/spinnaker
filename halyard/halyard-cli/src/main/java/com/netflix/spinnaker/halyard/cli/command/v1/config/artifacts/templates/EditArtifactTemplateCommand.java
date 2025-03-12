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

package com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.templates;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.ArtifactTemplate;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class EditArtifactTemplateCommand extends AbstractHasArtifactTemplateCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Edit an artifact template";

  @Parameter(
      names = "--template-path",
      description = "The path to the Jinja template to use for artifact extraction")
  private String templatePath;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String templateName = getArtifactTemplate();
    ArtifactTemplate template =
        new ArtifactTemplate().setName(templateName).setTemplatePath(templatePath);

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to edit artifact template " + templateName + ".")
        .setSuccessMessage("Successfully edited artifact template " + templateName + ".")
        .setOperation(
            Daemon.setArtifactTemplate(currentDeployment, templateName, !noValidate, template))
        .get();
  }
}
