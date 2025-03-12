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

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class DeleteArtifactTemplateCommand extends AbstractHasArtifactTemplateCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "delete";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Delete an artifact template";

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String templateName = getArtifactTemplate();

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to delete artifact template " + templateName + ".")
        .setSuccessMessage("Successfully deleted artifact template " + templateName + ".")
        .setOperation(Daemon.deleteArtifactTemplate(currentDeployment, templateName, !noValidate))
        .get();
  }
}
