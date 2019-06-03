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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractAddBaseImageCommand extends AbstractHasBaseImageCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "add";

  protected abstract BaseImage buildBaseImage(String baseImageId);

  @Parameter(
      names = "--short-description",
      description = BakeryCommandProperties.SHORT_DESCRIPTION_DESCRIPTION)
  private String shortDescription;

  @Parameter(
      names = "--detailed-description",
      description = BakeryCommandProperties.DETAILED_DESCRIPTION_DESCRIPTION)
  private String detailedDescription;

  @Parameter(
      names = "--package-type",
      description = BakeryCommandProperties.PACKAGE_TYPE_DESCRIPTION)
  private String packageType;

  @Parameter(
      names = "--template-file",
      description = BakeryCommandProperties.TEMPLATE_FILE_DESCRIPTION)
  private String templateFile;

  public String getShortDescription() {
    return "Add a base image for the " + getProviderName() + " provider's bakery.";
  }

  @Override
  protected void executeThis() {
    String baseImageId = getBaseImageId();
    BaseImage baseImage = buildBaseImage(baseImageId);

    BaseImage.ImageSettings imageSettings = baseImage.getBaseImage();
    if (imageSettings == null) {
      throw new RuntimeException(
          "Provider "
              + getProviderName()
              + " must provide image settings when building a base image. This is a bug with this provider's implementation of halyard.");
    }

    imageSettings.setId(getBaseImageId());
    imageSettings.setShortDescription(shortDescription);
    imageSettings.setDetailedDescription(detailedDescription);
    imageSettings.setPackageType(packageType);
    imageSettings.setTemplateFile(templateFile);

    String providerName = getProviderName();
    String currentDeployment = getCurrentDeployment();

    new OperationHandler<Void>()
        .setSuccessMessage(
            "Successfully added base image " + baseImageId + " to " + providerName + "'s bakery.")
        .setFailureMesssage(
            "Failed to add base image " + baseImageId + " to " + providerName + "'s bakery.")
        .setOperation(Daemon.addBaseImage(currentDeployment, providerName, !noValidate, baseImage))
        .get();
  }
}
