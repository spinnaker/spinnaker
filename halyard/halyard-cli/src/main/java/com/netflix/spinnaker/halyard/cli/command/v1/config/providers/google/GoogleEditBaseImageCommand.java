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
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.AbstractEditBaseImageCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleBaseImage;

@Parameters(separators = "=")
public class GoogleEditBaseImageCommand extends AbstractEditBaseImageCommand<GoogleBaseImage> {
  @Override
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
      arity = 1,
      description = GoogleCommandProperties.IS_IMAGE_FAMILY_DESCRIPTION)
  private Boolean isImageFamily = null;

  @Override
  protected BaseImage editBaseImage(GoogleBaseImage baseImage) {
    GoogleBaseImage.GoogleImageSettings imageSettings = baseImage.getBaseImage();
    imageSettings =
        imageSettings != null ? imageSettings : new GoogleBaseImage.GoogleImageSettings();
    imageSettings.setImageFamily(
        isSet(isImageFamily) ? isImageFamily : imageSettings.isImageFamily());
    baseImage.setBaseImage(imageSettings);

    GoogleBaseImage.GoogleVirtualizationSettings virtualizationSettings =
        baseImage.getVirtualizationSettings();
    virtualizationSettings =
        virtualizationSettings != null
            ? virtualizationSettings
            : new GoogleBaseImage.GoogleVirtualizationSettings();
    virtualizationSettings.setSourceImage(
        isSet(sourceImage) ? sourceImage : virtualizationSettings.getSourceImage());
    virtualizationSettings.setSourceImageFamily(
        isSet(sourceImageFamily)
            ? sourceImageFamily
            : virtualizationSettings.getSourceImageFamily());
    baseImage.setVirtualizationSettings(virtualizationSettings);

    return baseImage;
  }
}
