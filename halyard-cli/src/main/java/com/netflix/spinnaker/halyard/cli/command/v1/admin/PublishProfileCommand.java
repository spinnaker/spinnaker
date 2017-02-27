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

package com.netflix.spinnaker.halyard.cli.command.v1.admin;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Parameters()
public class PublishProfileCommand extends NestableCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "profile";

  @Getter(AccessLevel.PUBLIC)
  private String description = "Publish a base halconfig profile for a specific Spinnaker artifact.";

  @Parameter(
      names = "--bom-path",
      required = true,
      description = "The path to the BOM owning the artifact to publish."
  )
  private String bomPath;

  @Parameter(
      names = "--profile-path",
      required = true,
      description = "The path to the artifact profile to publish."
  )
  private String profilePath;

  @Parameter(description = "The name of the artifact whose profile is being published (e.g. clouddriver).", arity = 1)
  List<String> artifacts = new ArrayList<>();

  @Override
  public String getMainParameter() {
    return "artifact-name";
  }

  public String getArtifactName() {
    switch (artifacts.size()) {
      case 0:
        throw new IllegalArgumentException("No artifact name supplied");
      case 1:
        return artifacts.get(0);
      default:
        throw new IllegalArgumentException("More than one artifact supplied");
    }
  }

  @Override
  protected void executeThis() {
    Daemon.publishProfile(bomPath, getArtifactName(), profilePath);
    AnsiUi.success("Published your profile for " + getArtifactName());
  }
}
