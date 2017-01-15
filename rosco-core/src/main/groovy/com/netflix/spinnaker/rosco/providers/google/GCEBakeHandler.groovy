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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
public class GCEBakeHandler extends CloudProviderBakeHandler {

  private static final String IMAGE_NAME_TOKEN = "googlecompute: A disk image was created:"

  ImageNameFactory imageNameFactory = new ImageNameFactory()

  @Autowired
  RoscoGoogleConfiguration.GCEBakeryDefaults gceBakeryDefaults

  @Autowired
  RoscoGoogleConfiguration.GoogleConfigurationProperties googleConfigurationProperties

  @Override
  def getBakeryDefaults() {
    return gceBakeryDefaults
  }

  @Override
  BakeOptions getBakeOptions() {
    new BakeOptions(
      cloudProvider: BakeRequest.CloudProviderType.gce,
      baseImages: gceBakeryDefaults?.baseImages?.collect { it.baseImage }
    )
  }

  @Override
  String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
    return resolveAccount(bakeRequest).name
  }

  @Override
  def findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    def virtualizationSettings = gceBakeryDefaults?.baseImages.find {
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
      gce_zone        : gceBakeryDefaults.zone,
      gce_network     : gceBakeryDefaults.network,
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

    if (gceBakeryDefaults.useInternalIp != null) {
      parameterMap.gce_use_internal_ip = gceBakeryDefaults.useInternalIp
    }

    if (bakeRequest.build_info_url) {
      parameterMap.build_info_url = bakeRequest.build_info_url
    }

    if (appVersionStr) {
      parameterMap.appversion = appVersionStr
    }

    return parameterMap
  }

  @Override
  String getTemplateFileName() {
    return gceBakeryDefaults.templateFile
  }

  @Override
  Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    String imageName

    // TODO(duftler): Presently scraping the logs for the image name. Would be better to not be reliant on the log
    // format not changing. Resolve this by storing bake details in redis.
    logsContent.eachLine { String line ->
      if (line =~ IMAGE_NAME_TOKEN) {
        imageName = line.split(" ").last()
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

}