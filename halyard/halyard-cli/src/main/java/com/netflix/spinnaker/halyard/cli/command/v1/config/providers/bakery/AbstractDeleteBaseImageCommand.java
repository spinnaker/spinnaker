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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

/** Delete a specific PROVIDER base image */
@Parameters(separators = "=")
public abstract class AbstractDeleteBaseImageCommand extends AbstractHasBaseImageCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "delete";

  public String getShortDescription() {
    return "Delete a specific " + getProviderName() + " base image by name.";
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String providerName = getProviderName();
    String baseImageId = getBaseImageId();
    new OperationHandler<Void>()
        .setSuccessMessage(
            "Successfully deleted base image "
                + baseImageId
                + " from "
                + providerName
                + "'s bakery.")
        .setFailureMesssage(
            "Failed to delete base image " + baseImageId + " from " + providerName + "'s bakery.")
        .setOperation(
            Daemon.deleteBaseImage(currentDeployment, providerName, baseImageId, !noValidate))
        .get();
  }
}
