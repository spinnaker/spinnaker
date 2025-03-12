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

package com.netflix.spinnaker.halyard.cli.command.v1.versions;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.GlobalOptions;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiPrinter;
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class BomVersionCommand extends AbstractConfigCommand {
  @Parameter(description = "The version whose Bill of Materials (BOM) to lookup.")
  String version;

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "bom";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Get the Bill of Materials (BOM) for the specified version.";

  @Getter(AccessLevel.PUBLIC)
  private String longDescription =
      String.join(
          " ",
          "The Bill of Materials (BOM) is the manifest Halyard and Spinnaker use",
          "to agree on what subcomponent versions comprise a top-level release of",
          "Spinnaker. This command can be used with a main parameter (VERSION) to",
          "get the BOM for a given version of Spinnaker, or without a parameter to",
          "get the BOM for whatever version of Spinnaker you are currently configuring.");

  @Parameter(
      names = "--artifact-name",
      description = "When supplied, print the version of this artifact only.")
  String artifactName;

  @Override
  public String getMainParameter() {
    return "VERSION";
  }

  public String getVersion() {
    if (version == null) {
      return new OperationHandler<String>()
          .setOperation(Daemon.getVersion(getCurrentDeployment(), false))
          .setFailureMesssage("Failed to get version of Spinnaker configured in your halconfig.")
          .get();
    }
    return version;
  }

  @Override
  protected void executeThis() {
    BillOfMaterials bom =
        new OperationHandler<BillOfMaterials>()
            .setOperation(Daemon.getBillOfMaterials(getVersion()))
            .setFailureMesssage("Failed to get Bill of Materials for version " + getVersion())
            .get();

    String result;
    if (artifactName == null) {
      AnsiFormatUtils.Format format = GlobalOptions.getGlobalOptions().getOutput();
      if (format == null) {
        format = AnsiFormatUtils.Format.YAML;
      }
      result = AnsiFormatUtils.format(format, bom);
    } else {
      result = bom.getArtifactVersion(artifactName);
    }

    AnsiPrinter.out.println(result);
  }
}
