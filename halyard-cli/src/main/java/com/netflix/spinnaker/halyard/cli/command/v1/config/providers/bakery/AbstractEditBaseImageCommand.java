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
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractEditBaseImageCommand<T extends BaseImage>
    extends AbstractHasBaseImageCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  protected abstract BaseImage editBaseImage(T baseImage);

  @Parameter(names = "--id", description = BakeryCommandProperties.IMAGE_ID_DESCRIPTION)
  private String id;

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
    return "Edit a base image for the " + getProviderName() + " provider's bakery.";
  }

  @Override
  protected void executeThis() {
    String baseImageId = getBaseImageId();
    String providerName = getProviderName();
    String currentDeployment = getCurrentDeployment();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    BaseImage baseImage =
        new OperationHandler<BaseImage>()
            .setFailureMesssage(
                "Failed to get base image " + baseImageId + " in" + providerName + "'s bakery.")
            .setOperation(Daemon.getBaseImage(currentDeployment, providerName, baseImageId, false))
            .get();

    int originalHash = baseImage.hashCode();

    BaseImage.ImageSettings imageSettings = baseImage.getBaseImage();
    if (imageSettings == null) {
      throw new RuntimeException(
          "Image settings cannot be deleted during an edit. This is a bug in the "
              + getProviderName()
              + " provider's implementation of halyard.");
    }

    imageSettings.setId(isSet(id) ? id : imageSettings.getId());
    imageSettings.setShortDescription(
        isSet(shortDescription) ? shortDescription : imageSettings.getShortDescription());
    imageSettings.setDetailedDescription(
        isSet(detailedDescription) ? detailedDescription : imageSettings.getDetailedDescription());
    imageSettings.setPackageType(isSet(packageType) ? packageType : imageSettings.getPackageType());
    imageSettings.setTemplateFile(
        isSet(templateFile) ? templateFile : imageSettings.getTemplateFile());

    baseImage = editBaseImage((T) baseImage);

    if (originalHash == baseImage.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setFailureMesssage(
            "Failed to edit base image " + baseImageId + " in" + providerName + "'s bakery.")
        .setSuccessMessage(
            "Successfully edited base image " + baseImageId + " in" + providerName + "'s bakery.")
        .setOperation(
            Daemon.setBaseImage(
                currentDeployment, providerName, baseImageId, !noValidate, baseImage))
        .get();
  }
}
