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

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeOptions.BaseImage
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.jobs.BakeRecipe
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackageNameConverter
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

@Slf4j
abstract class CloudProviderBakeHandler {

  @Value('${rosco.config-dir}')
  String configDir

  @Autowired
  PackerCommandFactory packerCommandFactory

  @Autowired
  DynamicConfigService dynamicConfigService

  @Value('${debian-repository:}')
  String debianRepository

  @Value('${yum-repository:}')
  String yumRepository

  @Value('${chocolatey-repository:}')
  String chocolateyRepository

  @Value('${templates-needing-root:}')
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
   * @return An ImageNameFactory object for this specific cloud provider.
   */
  abstract ImageNameFactory getImageNameFactory()

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
      def keys = [
        'bake',
        cloud_provider_type,
        base_os,
        base_ami,
        ami_name,
        template_file_name,
        var_file_name,
      ].findAll { it }

      // Package name is always part of key, even if it is an empty string
      keys << (package_name ? package_name.tokenize().join('|') : "")
      // If any artifacts to bake were specified, include them in the key as well
      if (package_artifacts) {
        keys << package_artifacts.collect { it.getReference() ?: "" }.join('|')
      }

      String providerSpecificBakeKeyComponent = produceProviderSpecificBakeKeyComponent(region, bakeRequest)
      if (providerSpecificBakeKeyComponent) {
        keys << providerSpecificBakeKeyComponent
      }

      return keys.join(':')
    }
  }

  /**
   * Returns the details of a completed bake.
   * TODO(duftler): This is temporary. Remove the scraping logic when a better solution for
   * determining image id/name is in place.
   */
  abstract Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent)

  /**
   * Returns a decorated artifact for a given bake. Right now this is a generic approach
   * but it could be useful to override this method for specific providers.
   */
  def Artifact produceArtifactDecorationFrom(BakeRequest bakeRequest, BakeRecipe bakeRecipe, Bake bakeDetails, String cloudProvider, String region) {
    Artifact bakedArtifact = Artifact.builder()
      .name(bakeRecipe?.name)
      .type("${cloudProvider}/image")
      .location(region)
      .reference(getArtifactReference(bakeRequest, bakeDetails))
      .metadata([
        build_info_url: bakeRequest?.build_info_url,
        build_number: bakeRequest?.build_number])
      .uuid(bakeDetails.id)
      .build()

    return bakedArtifact
  }

  def String getArtifactReference(BakeRequest bakeRequest, Bake bakeDetails) {
    return bakeDetails.ami ?: bakeDetails.image_name
  }

  /**
   * Deletes the temporary file containing artifacts to bake into the image.  Currently only GCE
   * supports baking artifacts, so this defaults to a no-op.
   */
  void deleteArtifactFile(String bakeId) {
  }

  /**
   * Finds the appropriate virtualization settings in this provider's configuration based on the region and
   * bake request parameters. Throws an IllegalArgumentException if the virtualization settings cannot be
   * found.
   */
  abstract def findVirtualizationSettings(String region, BakeRequest bakeRequest)

  /**
   * Returns a map containing the parameters that should be propagated to the packer template for this provider.
   */
  abstract Map buildParameterMap(String region, def virtualizationSettings, String imageName, BakeRequest bakeRequest, String appVersionStr)

  /**
   * Returns the command that should be prepended to the shell command passed to the container.
   */
  String getBaseCommand(String templateName) {
    if (templatesNeedingRoot) {
      return templatesNeedingRoot.contains(templateName) ? "sudo" : ""
    } else {
      return ""
    }
  }

  /**
   * Returns the name of the template file for this provider.
   */
  abstract String getTemplateFileName(BakeOptions.BaseImage baseImage)

  /**
   * Right now this builds a recipe for packer.
   * In the future this method should be abstract, and have
   * provider-specific implementations.
   */
  BakeRecipe produceBakeRecipe(String region, BakeRequest bakeRequest) {
    def virtualizationSettings = findVirtualizationSettings(region, bakeRequest)

    BakeOptions.Selected selectedOptions = new BakeOptions.Selected(baseImage: findBaseImage(bakeRequest))
    BakeRequest.PackageType packageType = selectedOptions.baseImage.packageType

    List<String> packageNameList = bakeRequest.package_name?.tokenize(" ") ?: []

    def osPackageNames = PackageNameConverter.buildOsPackageNames(packageType, packageNameList)
    def osArtifactNames = bakeRequest.package_artifacts.collect { Artifact a ->
      PackageNameConverter.buildOsPackageName(packageType, a.getName())
    }

    // Use both packages and artifacts to determine the version string and image name
    def appVersionStr = imageNameFactory.buildAppVersionStr(bakeRequest, osPackageNames + osArtifactNames, packageType)
    def imageName = imageNameFactory.buildImageName(bakeRequest, osPackageNames + osArtifactNames)

    // Don't include artifacts when constructing the packagesParameter, as artifacts will be passed
    // separately
    def packagesParameter = imageNameFactory.buildPackagesParameter(packageType, osPackageNames)

    def parameterMap = buildParameterMap(region, virtualizationSettings, imageName, bakeRequest, appVersionStr)

    if (selectedOptions.baseImage.customRepository) {
      parameterMap.repository = selectedOptions.baseImage.customRepository
    } else if (debianRepository && selectedOptions.baseImage.packageType == BakeRequest.PackageType.DEB) {
      parameterMap.repository = debianRepository
    } else if (yumRepository && selectedOptions.baseImage.packageType == BakeRequest.PackageType.RPM) {
      parameterMap.repository = yumRepository
    } else if (chocolateyRepository && selectedOptions.baseImage.packageType == BakeRequest.PackageType.NUPKG) {
      parameterMap.repository = chocolateyRepository
    }

    parameterMap.package_type = selectedOptions.baseImage.packageType.util.packageType
    parameterMap.packages = packagesParameter

    if (bakeRequest.build_host) {
      parameterMap.build_host = bakeRequest.build_host
    }

    if (bakeRequest.upgrade) {
      parameterMap.upgrade = bakeRequest.upgrade
    }

    parameterMap.configDir = configDir

    bakeRequest.extended_attributes?.entrySet()?.forEach { attribute ->
      switch (attribute.getKey()) {
        case ['copy_to', 'share_with']:
          parameterMap.putAll(unrollParameters(attribute))
          break
        default:
          parameterMap.put(attribute.getKey(), attribute.getValue())
      }
    }

    def finalTemplateFileName = bakeRequest.template_file_name ?: getTemplateFileName(selectedOptions.baseImage)
    def finaltemplateFilePath = "$configDir/$finalTemplateFileName"
    def finalVarFileName = bakeRequest.var_file_name ? "$configDir/$bakeRequest.var_file_name" : null
    def baseCommand = getBaseCommand(finalTemplateFileName)
    def packerCommand = packerCommandFactory.buildPackerCommand(baseCommand,
                                                                parameterMap,
                                                                finalVarFileName,
                                                                finaltemplateFilePath)

    return new BakeRecipe(name: imageName, version: appVersionStr, command: packerCommand)
  }

  /**
   * Ability to lookup base image via dynamicConfigService when unset in bakeRequest or rosco.yml
   * Property name:
   *  "${bakeRequest.cloud_provider_type}.base.${bakeRequest.base_os}.${bakeRequest.vm_type}.${bakeRequest.base_label}.$region"
   *  I.e.:
   *  "aws.base.bionic.hvm.release.us-west-2"
   */
  protected String lookupBaseByDynamicProperty(String region, BakeRequest bakeRequest) {
    String property = "${bakeRequest.cloud_provider_type}.base.${bakeRequest.base_os}.${bakeRequest.vm_type}.${bakeRequest.base_label}.$region"
    String base = dynamicConfigService.getConfig(String, property, null)
    if (base == null) {
      log.warn("No base image found for property '$property'")
    }
    return base
  }

  protected Map unrollParameters(Map.Entry entry) {
    String keyPrefix = entry.key + "_"
    Map parameters = new HashMap()
    entry.value.tokenize(",").eachWithIndex { String value, int i ->
      int keyNumber = i + 1
      parameters.put(keyPrefix + keyNumber, value.trim())
    }

    return parameters
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
