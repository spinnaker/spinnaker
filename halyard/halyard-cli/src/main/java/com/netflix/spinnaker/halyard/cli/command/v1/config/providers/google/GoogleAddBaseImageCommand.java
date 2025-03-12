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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.google;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.AbstractAddBaseImageCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleBaseImage;

@Parameters(separators = "=")
public class GoogleAddBaseImageCommand extends AbstractAddBaseImageCommand {
  protected String getProviderName() {
    return "google";
  }

  @Parameter(
      names = "--source-image",
      description = GoogleCommandProperties.SOURCE_IMAGE_DESCRIPTION)
  private String sourceImage;

  @Parameter(
      names = "--source-image-family",
      description = GoogleCommandProperties.SOURCE_IMAGE_FAMILY_DESCRIPTION)
  private String sourceImageFamily;

  @Parameter(
      names = "--is-image-family",
      description = GoogleCommandProperties.IS_IMAGE_FAMILY_DESCRIPTION)
  private boolean isImageFamily = false;

  @Override
  protected BaseImage buildBaseImage(String baseImageId) {
    GoogleBaseImage baseImage = new GoogleBaseImage();
    GoogleBaseImage.GoogleImageSettings imageSettings = new GoogleBaseImage.GoogleImageSettings();
    imageSettings.setImageFamily(isImageFamily);
    baseImage.setBaseImage(imageSettings);

    GoogleBaseImage.GoogleVirtualizationSettings virtualizationSettings =
        new GoogleBaseImage.GoogleVirtualizationSettings();
    virtualizationSettings.setSourceImage(sourceImage);
    virtualizationSettings.setSourceImageFamily(sourceImageFamily);
    baseImage.setVirtualizationSettings(virtualizationSettings);

    return baseImage;
  }
}
