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
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class PublishProfileCommand extends NestableCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "profile";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription =
      "Publish a base halconfig profile for a specific Spinnaker artifact.";

  @Parameter(
      names = "--bom-path",
      converter = LocalFileConverter.class,
      required = true,
      description = "The path to the BOM owning the artifact to publish.")
  private String bomPath;

  @Parameter(
      names = "--profile-path",
      converter = LocalFileConverter.class,
      required = true,
      description = "The path to the artifact profile to publish.")
  private String profilePath;

  @Parameter(
      description = "The name of the artifact whose profile is being published (e.g. clouddriver).")
  String artifact;

  @Override
  public String getMainParameter() {
    return "artifact-name";
  }

  public String getArtifactName() {
    if (artifact == null) {
      throw new IllegalArgumentException("No artifact name supplied");
    }
    return artifact;
  }

  @Override
  protected void executeThis() {
    new OperationHandler<Void>()
        .setFailureMesssage(
            "Failed to publish your profile for artifact " + getArtifactName() + ".")
        .setSuccessMessage(
            "Successfully published your profile for artifact " + getArtifactName() + ".")
        .setOperation(Daemon.publishProfile(bomPath, getArtifactName(), profilePath))
        .get();
  }
}
