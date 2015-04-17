/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rosco.providers

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import org.springframework.beans.factory.annotation.Autowired

abstract class CloudProviderBakeHandler {

  @Autowired
  ImageNameFactory imageNameFactory

  @Autowired
  PackerCommandFactory packerCommandFactory

  /**
   * Build provider-specific key used to determine uniqueness. If a prior (or in-flight) bake exists
   * with the same bake key, it indicates that that image can be re-used instead of initiating a new
   * bake.
   */
  abstract String produceBakeKey(String region, BakeRequest bakeRequest)

  /**
   * Returns true if this cloud provider is the producer of this first line of logs content.
   */
  abstract boolean isProducerOf(String logsContentFirstLine)

  /**
   * Returns the details of a completed bake.
   * TODO(duftler): This is temporary. Remove the scraping logic when a better solution for
   * determining image id/name is in place.
   */
  abstract Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent)

  /**
   * Finds the appropriate virtualization settings in this provider's configuration based on the region and
   * bake request parameters. Throws an IllegalArgumentException if the virtualization settings cannot be
   * found.
   */
  abstract def findVirtualizationSettings(String region, BakeRequest bakeRequest)

  /**
   * Returns a map containing the parameters that should be propagated to the packer template for this provider.
   */
  abstract Map buildParameterMap(String region, def virtualizationSettings, String imageName)

  /**
   * Returns the command that should be prepended to the shell command passed to the container.
   */
  String getBaseCommand() {
    return ""
  }

  /**
   * Returns the name of the template file for this provider.
   */
  abstract String getTemplateFileName()

  /**
   * Build provider-specific script command for packer.
   */
  List<String> producePackerCommand(String region, BakeRequest bakeRequest) {
    def virtualizationSettings = findVirtualizationSettings(region, bakeRequest)

    def (imageName, appVersionStr, packagesParameter) = imageNameFactory.deriveImageNameAndAppVersion(bakeRequest)

    def parameterMap = buildParameterMap(region, virtualizationSettings, imageName)

    // TODO(duftler): Build out proper support for installation of packages.
    parameterMap.packages = packagesParameter

    if (appVersionStr) {
      parameterMap.appversion = appVersionStr
    }

    if (bakeRequest.build_host) {
      parameterMap.build_host = bakeRequest.build_host
    }

    return packerCommandFactory.buildPackerCommand(baseCommand, parameterMap, templateFileName)
  }

}
