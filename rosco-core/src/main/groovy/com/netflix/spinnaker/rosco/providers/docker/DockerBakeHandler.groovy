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

package com.netflix.spinnaker.rosco.providers.docker

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.docker.config.RoscoDockerConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
public class DockerBakeHandler extends CloudProviderBakeHandler {

  private static final String BUILDER_TYPE = "docker"
  private static final String IMAGE_NAME_TOKEN = "Repository:"

  ImageNameFactory imageNameFactory = new ImageNameFactory()

  @Autowired
  RoscoDockerConfiguration.DockerBakeryDefaults dockerBakeryDefaults

  @Override
  def getBakeryDefaults() {
    return dockerBakeryDefaults
  }

  BakeOptions getBakeOptions() {
    new BakeOptions(
      cloudProvider: BakeRequest.CloudProviderType.docker,
      baseImages: dockerBakeryDefaults?.baseImages?.collect { it.baseImage }
    )
  }

  @Override
  def findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    def virtualizationSettings = dockerBakeryDefaults?.baseImages.find {
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
  Map buildParameterMap(String region, def dockerVirtualizationSettings, String imageName, BakeRequest bakeRequest) {
    return [
      docker_source_image     : dockerVirtualizationSettings.sourceImage,
      docker_target_image     : imageName,
      docker_target_repository: dockerBakeryDefaults.targetRepository
    ]
  }

  @Override
  String getTemplateFileName() {
    return dockerBakeryDefaults.templateFile
  }

  @Override
  boolean isProducerOf(String logsContentFirstLine) {
    logsContentFirstLine =~ BUILDER_TYPE
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
}