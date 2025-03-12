/*
 * Copyright 2017 Microsoft, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.azure;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.AbstractAddBaseImageCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import com.netflix.spinnaker.halyard.config.model.v1.providers.azure.AzureBaseImage;

@Parameters(separators = "=")
public class AzureAddBaseImageCommand extends AbstractAddBaseImageCommand {
  protected String getProviderName() {
    return "azure";
  }

  @Parameter(
      names = "--publisher",
      required = true,
      description = AzureCommandProperties.IMAGE_PUBLISHER_DESCRIPTION)
  private String publisher;

  @Parameter(
      names = "--offer",
      required = true,
      description = AzureCommandProperties.IMAGE_OFFER_DESCRIPTION)
  private String offer;

  @Parameter(
      names = "--sku",
      required = true,
      description = AzureCommandProperties.IMAGE_SKU_DESCRIPTION)
  private String sku;

  @Parameter(
      names = "--image-version", // just using '--version' would conflict with the global parameter
      description = AzureCommandProperties.IMAGE_VERSION_DESCRIPTION)
  private String version;

  @Override
  protected BaseImage buildBaseImage(String baseImageId) {
    AzureBaseImage.AzureOperatingSystemSettings imageSettings =
        (new AzureBaseImage.AzureOperatingSystemSettings())
            .setPublisher(publisher)
            .setOffer(offer)
            .setSku(sku)
            .setVersion(isSet(version) ? version : "latest");

    return (new AzureBaseImage()).setBaseImage(imageSettings);
  }
}
