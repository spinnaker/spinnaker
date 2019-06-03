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
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.ArtifactTemplate;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class ListArtifactTemplatesCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "list";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "List an artifact templates";

  private List<ArtifactTemplate> getArtifactTemplates() {
    String currentDeployment = getCurrentDeployment();
    return new OperationHandler<List<ArtifactTemplate>>()
        .setFailureMesssage("Failed to get artifact templates.")
        .setOperation(Daemon.getArtifactTemplates(currentDeployment, !noValidate))
        .get();
  }

  @Override
  protected void executeThis() {
    List<ArtifactTemplate> templates = getArtifactTemplates();
    if (templates.isEmpty()) {
      AnsiUi.success("No configured artifact templates.");
    } else {
      AnsiUi.success("Artifact templates:");
      templates.forEach(template -> AnsiUi.listItem(template.getName()));
    }
  }
}
