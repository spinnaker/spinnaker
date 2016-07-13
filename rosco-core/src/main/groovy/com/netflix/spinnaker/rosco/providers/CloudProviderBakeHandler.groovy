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
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeOptions.BaseImage
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

abstract class CloudProviderBakeHandler {

  @Value('${rosco.configDir}')
  String configDir

  @Autowired
  ImageNameFactory imageNameFactory

  @Autowired
  PackerCommandFactory packerCommandFactory

  @Value('${debianRepository:}')
  String debianRepository

  @Value('${yumRepository:}')
  String yumRepository

  @Value('${templatesNeedingRoot:}')
  List<String> templatesNeedingRoot

  /**
   * @return A cloud provider-specific set of defaults.
   */
  abstract def getBakeryDefaults()

  /**
   * @return A BakeOptions object describing what options are available for this specific cloud provider.
   */
  abstract BakeOptions getBakeOptions()

  /**
   * Build provider-specific naming component to use in composing bake key.
   */
  String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
    return null
  }

  /**
   * Build provider-specific key used to determine uniqueness. If a prior (or in-flight) bake exists
   * with the same bake key, it indicates that that image can be re-used instead of initiating a new
   * bake.
   */
  String produceBakeKey(String region, BakeRequest bakeRequest) {
    bakeRequest.with {
      def bakeKey = "bake:$cloud_provider_type:$base_os"

      if (base_ami) {
        bakeKey += ":$base_ami"
      }

      if (ami_name) {
        bakeKey += ":$ami_name"
      }

      String packages = package_name ? package_name.tokenize().join('|') : ""

      bakeKey += ":$packages"

      def providerSpecificBakeKeyComponent = produceProviderSpecificBakeKeyComponent(region, bakeRequest)

      if (providerSpecificBakeKeyComponent) {
        bakeKey += ":$providerSpecificBakeKeyComponent"
      }

      return bakeKey
    }
  }

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
  abstract Map buildParameterMap(String region, def virtualizationSettings, String imageName, BakeRequest bakeRequest)

  /**
   * Returns the command that should be prepended to the shell command passed to the container.
   */
  String getBaseCommand() {
    if (templatesNeedingRoot) {
      return templatesNeedingRoot.contains(getBakeryDefaults().templateFile) ? "sudo" : ""
    } else {
      return ""
    }
  }

  /**
   * Returns the name of the template file for this provider.
   */
  abstract String getTemplateFileName()

  /**
   * Build provider-specific command for packer.
   */
  List<String> producePackerCommand(String region, BakeRequest bakeRequest) {
    def virtualizationSettings = findVirtualizationSettings(region, bakeRequest)

    BakeOptions.Selected selectedOptions = new BakeOptions.Selected(baseImage: findBaseImage(bakeRequest))
    def (imageName, appVersionStr, packagesParameter) = imageNameFactory.deriveImageNameAndAppVersion(bakeRequest, selectedOptions)

    def parameterMap = buildParameterMap(region, virtualizationSettings, imageName, bakeRequest)

    if (debianRepository && selectedOptions.baseImage.packageType == BakeRequest.PackageType.DEB) {
      parameterMap.repository = debianRepository
    } else if (yumRepository && selectedOptions.baseImage.packageType == BakeRequest.PackageType.RPM) {
      parameterMap.repository = yumRepository
    }

    parameterMap.package_type = selectedOptions.baseImage.packageType.packageType
    parameterMap.packages = packagesParameter

    if (appVersionStr) {
      parameterMap.appversion = appVersionStr
    }

    if (bakeRequest.build_host) {
      parameterMap.build_host = bakeRequest.build_host
    }

    if (bakeRequest.upgrade) {
      parameterMap.upgrade = bakeRequest.upgrade
    }

    parameterMap.configDir = configDir

    if (bakeRequest.extended_attributes) {
      if (bakeRequest.extended_attributes.containsKey('share_with')) {
        unrollParameters("share_with_", bakeRequest.extended_attributes.get('share_with'), parameterMap)
      }

      if (bakeRequest.extended_attributes.containsKey('copy_to')) {
        unrollParameters("copy_to_", bakeRequest.extended_attributes.get('copy_to'), parameterMap)
      }

      List attributes = bakeRequest.extended_attributes.keySet().asList()
      parameterMap << bakeRequest.extended_attributes.subMap(
              attributes.findAll { !it.equals('share_with') && !it.equals('copy_to') })
    }

    def finalTemplateFileName = bakeRequest.template_file_name ?: templateFileName

    return packerCommandFactory.buildPackerCommand(baseCommand, parameterMap, "$configDir/$finalTemplateFileName")
  }

  private void unrollParameters(String prefix, String rolledParameter, Map parameterMap) {
    List<String> values = rolledParameter.tokenize(",")
    values.eachWithIndex { value, index, counter = index + 1 ->
      parameterMap.put(prefix + counter, value.trim())
    }
  }

  BaseImage findBaseImage(BakeRequest bakeRequest) {
    def osVirtualizationSettings = getBakeryDefaults().baseImages.find {
      it.baseImage.id == bakeRequest.base_os
    }
    if (!osVirtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for '$bakeRequest.base_os'.")
    }
    return osVirtualizationSettings.baseImage
  }

  /**
   * This allows providers to specify Packer variables names whose values will be masked with '******'
   * in logging output. Subclasses should override this if they need to mask sensitive data sent to Packer.
   * @return
   */
  List<String> getMaskedPackerParameters() {
    return []
  }
}
