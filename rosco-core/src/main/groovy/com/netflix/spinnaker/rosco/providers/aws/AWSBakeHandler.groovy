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

package com.netflix.spinnaker.rosco.providers.aws

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import com.netflix.spinnaker.rosco.providers.aws.config.RoscoAWSConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
public class AWSBakeHandler implements CloudProviderBakeHandler {

  private static final String BUILDER_TYPE = "amazon-ebs"
  private static final String IMAGE_NAME_TOKEN = "amazon-ebs: Creating the AMI:"

  @Autowired
  RoscoAWSConfiguration.AWSBakeryDefaults awsBakeryDefaults

  @Autowired
  ImageNameFactory imageNameFactory

  @Autowired
  PackerCommandFactory packerCommandFactory

  @Override
  String produceBakeKey(String region, BakeRequest bakeRequest) {
    if (!bakeRequest.vm_type) {
      bakeRequest = bakeRequest.copyWith(vm_type: awsBakeryDefaults.defaultVirtualizationType)
    }

    // TODO(duftler): Work through definition of uniqueness.
    bakeRequest.with {
      return "bake:$cloud_provider_type:$region:$vm_type:$base_os:$package_name"
    }
  }

  @Override
  List<String> producePackerCommand(String region, BakeRequest bakeRequest) {
    def (imageName, appVersionStr, appVersion, packagesParameter) = imageNameFactory.produceImageName(bakeRequest)

    if (!bakeRequest.vm_type) {
      bakeRequest = bakeRequest.copyWith(vm_type: awsBakeryDefaults.defaultVirtualizationType)
    }

    def awsOperatingSystemVirtualizationSettings = awsBakeryDefaults?.operatingSystemVirtualizationSettings.find {
      it.os == bakeRequest.base_os
    }

    if (!awsOperatingSystemVirtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for '$bakeRequest.base_os'.")
    }

    def awsVirtualizationSettings = awsOperatingSystemVirtualizationSettings?.virtualizationSettings.find {
      it.region == region
      it.virtualizationType == bakeRequest.vm_type
    }

    if (!awsVirtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for region '$region', operating system '$bakeRequest.base_os', and vm type '$bakeRequest.vm_type'.")
    }

    def parameterMap = [
      aws_access_key:    awsBakeryDefaults.awsAccessKey,
      aws_secret_key:    awsBakeryDefaults.awsSecretKey,
      aws_region:        region,
      aws_ssh_username:  awsVirtualizationSettings.sshUserName,
      aws_instance_type: awsVirtualizationSettings.instanceType,
      aws_source_ami:    awsVirtualizationSettings.sourceAmi,
      aws_target_ami:    imageName
    ]

    // TODO(duftler): Build out proper support for installation of packages.
    parameterMap.packages = packagesParameter

    // TODO(duftler): Also set 'build_host' once it is included in BakeRequest.
    if (appVersion) {
      parameterMap.appversion = appVersionStr
    }

    return packerCommandFactory.buildPackerCommand("", parameterMap, awsBakeryDefaults.templateFile)
  }

  @Override
  boolean isProducerOf(String logsContentFirstLine) {
    logsContentFirstLine =~ BUILDER_TYPE
  }

  @Override
  Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    String amiId
    String imageName

    // TODO(duftler): Presently scraping the logs for the image name/id. Would be better to not be reliant on the log
    // format not changing. Resolve this by storing bake details in redis and querying oort for amiId from amiName.
    logsContent.eachLine { String line ->
      if (line =~ IMAGE_NAME_TOKEN) {
        imageName = line.split(" ").last()
      } else if (line =~ "$region:") {
        amiId = line.split(" ").last()
      }
    }

    return new Bake(id: bakeId, ami: amiId, image_name: imageName)
  }
}