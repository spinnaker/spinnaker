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
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Parameters()
public class BomVersionCommand extends NestableCommand {
  @Parameter(description = "The version whose Bill of Materials (BOM) to lookup.", arity = 1)
  List<String> versions = new ArrayList<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "bom";

  @Getter(AccessLevel.PUBLIC)
  private String description = "Get the Bill of Materials (BOM) for the specified version.";

  @Override
  public String getMainParameter() {
    return "VERSION";
  }

  public String getVersion() {
    switch (versions.size()) {
      case 0:
        throw new IllegalArgumentException("No version name supplied");
      case 1:
        return versions.get(0);
      default:
        throw new IllegalArgumentException("More than one version supplied");
    }
  }

  @Override
  protected void executeThis() {
    new OperationHandler<BillOfMaterials>()
        .setFormat(AnsiFormatUtils.Format.YAML)
        .setOperation(Daemon.getBillOfMaterials(getVersion()))
        .setFailureMesssage("Failed to get Bill of Materials for version " + getVersion())
        .get();
  }
}
