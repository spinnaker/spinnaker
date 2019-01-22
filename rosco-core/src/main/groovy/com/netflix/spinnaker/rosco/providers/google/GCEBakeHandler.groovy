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

package com.netflix.spinnaker.rosco.providers.google

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.google.config.RoscoGoogleConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackerArtifactService
import com.netflix.spinnaker.rosco.providers.util.PackerManifest
import com.netflix.spinnaker.rosco.providers.util.PackerManifestService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.atomic.AtomicReference

@Slf4j
@Component
public class GCEBakeHandler extends CloudProviderBakeHandler {

  private static final String IMAGE_NAME_TOKEN = "googlecompute: A disk image was created:"

  private final resolvedBakeryDefaults = new AtomicReference<RoscoGoogleConfiguration.GCEBakeryDefaults>()

  ImageNameFactory imageNameFactory = new ImageNameFactory()

  PackerArtifactService packerArtifactService = new PackerArtifactService()

  PackerManifestService packerManifestService = new PackerManifestService()

  @Autowired
  RoscoGoogleConfiguration.GCEBakeryDefaults gceBakeryDefaults

  @Autowired
  @Deprecated // Deprecated for consistency with other providers. See `gceBakeryDefaults`.
  RoscoGoogleConfiguration.GCEBakeryDefaults deprecatedGCEBakeryDefaults

  @Autowired
  RoscoGoogleConfiguration.GoogleConfigurationProperties googleConfigurationProperties

  @Override
  RoscoGoogleConfiguration.GCEBakeryDefaults getBakeryDefaults() {
    if (resolvedBakeryDefaults.get() == null) {
      def defaults = new RoscoGoogleConfiguration.GCEBakeryDefaults()
      defaults.baseImages = (gceBakeryDefaults?.baseImages ?: []) + (deprecatedGCEBakeryDefaults?.baseImages ?: [])
      defaults.network = gceBakeryDefaults?.network ?: deprecatedGCEBakeryDefaults?.network
      defaults.networkProjectId = gceBakeryDefaults?.networkProjectId ?: deprecatedGCEBakeryDefaults?.networkProjectId
      defaults.subnetwork = gceBakeryDefaults?.subnetwork ?: deprecatedGCEBakeryDefaults?.subnetwork
      defaults.templateFile = gceBakeryDefaults?.templateFile ?: deprecatedGCEBakeryDefaults?.templateFile
      defaults.zone = gceBakeryDefaults?.zone ?: deprecatedGCEBakeryDefaults?.zone
      defaults.useInternalIp = gceBakeryDefaults?.useInternalIp != null ? gceBakeryDefaults?.useInternalIp : deprecatedGCEBakeryDefaults?.useInternalIp
      resolvedBakeryDefaults.compareAndSet(null, defaults)
    }
    return resolvedBakeryDefaults.get();
  }

  @Override
  BakeOptions getBakeOptions() {
    new BakeOptions(
      cloudProvider: BakeRequest.CloudProviderType.gce,
      baseImages: bakeryDefaults?.baseImages?.collect { it.baseImage }
    )
  }

  @Override
  String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
    return resolveAccount(bakeRequest).name
  }

  @Override
  def findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    def virtualizationSettings = bakeryDefaults?.baseImages?.find {
      it.baseImage.id == bakeRequest.base_os
    }?.virtualizationSettings

    if (!virtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for '$bakeRequest.base_os'.")
    }

    if (bakeRequest.base_ami) {
      virtualizationSettings = virtualizationSettings.clone()
      virtualizationSettings.sourceImage = bakeRequest.base_ami
    }

    return virtualizationSettings
  }

  @Override
  Map buildParameterMap(String region, def gceVirtualizationSettings, String imageName, BakeRequest bakeRequest, String appVersionStr) {
    RoscoGoogleConfiguration.ManagedGoogleAccount managedGoogleAccount = resolveAccount(bakeRequest)

    def parameterMap = [
      gce_project_id  : managedGoogleAccount.project,
      gce_zone        : bakeryDefaults?.zone,
      gce_network     : bakeryDefaults?.network,
      gce_target_image: imageName
    ]

    if (gceVirtualizationSettings.sourceImage) {
      parameterMap.gce_source_image = gceVirtualizationSettings.sourceImage
    } else if (gceVirtualizationSettings.sourceImageFamily) {
      parameterMap.gce_source_image_family = gceVirtualizationSettings.sourceImageFamily
    } else {
      throw new IllegalArgumentException("No source image or source image family found for '$bakeRequest.base_os'.")
    }

    if (managedGoogleAccount.jsonPath) {
      parameterMap.gce_account_file = managedGoogleAccount.jsonPath
    }

    if (bakeryDefaults?.networkProjectId) {
      parameterMap.gce_network_project_id = bakeryDefaults.networkProjectId
    }

    if (bakeryDefaults?.subnetwork) {
      parameterMap.gce_subnetwork = bakeryDefaults.subnetwork
    }

    if (bakeryDefaults?.useInternalIp != null) {
      parameterMap.gce_use_internal_ip = bakeryDefaults?.useInternalIp
    }

    if (bakeRequest.build_info_url) {
      parameterMap.build_info_url = bakeRequest.build_info_url
    }

    if (appVersionStr) {
      parameterMap.appversion = appVersionStr
    }

    parameterMap.artifactFile = packerArtifactService.writeArtifactsToFile(bakeRequest.request_id, bakeRequest.package_artifacts)?.toString()

    parameterMap.manifestFile = packerManifestService.getManifestFileName(bakeRequest.request_id)

    return parameterMap
  }

  @Override
  void deleteArtifactFile(String bakeId) {
    packerArtifactService.deleteArtifactFile(bakeId)
  }

  @Override
  String getTemplateFileName(BakeOptions.BaseImage baseImage) {
    return baseImage.templateFile ?: bakeryDefaults?.templateFile
  }

  @Override
  Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    String imageName

    if (packerManifestService.manifestExists(bakeId)) {
      log.info("Using manifest file to determine baked artifact for bake $bakeId")
      PackerManifest.PackerBuild packerBuild = packerManifestService.getBuild(bakeId)
      imageName = packerBuild.getArtifactId()
    } else {
      // TODO(duftler): Presently scraping the logs for the image name. Would be better to not be reliant on the log
      // format not changing. Resolve this by storing bake details in redis.
      log.info("Scraping logs to determine baked artifact for bake $bakeId")
      logsContent.eachLine { String line ->
        if (line =~ IMAGE_NAME_TOKEN) {
          imageName = line.split(" ").last()
        }
      }
    }

    return new Bake(id: bakeId, image_name: imageName)
  }

  private RoscoGoogleConfiguration.ManagedGoogleAccount resolveAccount(BakeRequest bakeRequest) {
    RoscoGoogleConfiguration.ManagedGoogleAccount managedGoogleAccount =
      bakeRequest.account_name
      ? googleConfigurationProperties?.accounts?.find { it.name == bakeRequest.account_name }
      : googleConfigurationProperties?.accounts?.getAt(0)

    if (!managedGoogleAccount) {
      throw new IllegalArgumentException("Could not resolve Google account: (account_name=$bakeRequest.account_name).")
    }

    return managedGoogleAccount
  }

  @Override
  String getArtifactReference(BakeRequest bakeRequest, Bake bakeDetails) {
    RoscoGoogleConfiguration.ManagedGoogleAccount managedGoogleAccount = resolveAccount(bakeRequest)
    def project = managedGoogleAccount.getProject()
    def imageName = bakeDetails.image_name

    // TODO(ezimanyi): Remove hard-coding of image URI
    // Either get packer to directly return the generated URI (preferred) or send a request to
    // clouddriver to convert the project/image combination into a URI using the Google compute API
    return "https://compute.googleapis.com/compute/v1/projects/$project/global/images/$imageName"
  }
}
