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

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.api.BakeRequest.VmType
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.aws.config.RoscoAWSConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.services.ClouddriverService
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import java.time.Duration
import java.util.regex.Pattern

@Component
@Slf4j
public class AWSBakeHandler extends CloudProviderBakeHandler {

  private static final String IMAGE_NAME_TOKEN = "amazon-(chroot|ebs): Creating the AMI:"
  private static final String UNENCRYPTED_IMAGE_NAME_TOKEN = "(==> |)amazon-(chroot|ebs): Creating unencrypted AMI"
  // AMI_EXTRACTOR finds amis from the format produced by packer ie:
  // eu-north-1: ami-076cf277a86b6e5b4
  // us-east-1: ami-2c014644
  private static final Pattern AMI_ID = Pattern.compile("^ami-[a-z0-9]+\$")
  private static final String AMI_EXTRACTOR = "[a-z]{2}-[a-z]{2,10}-[0-9]:\\sami-[a-z0-9]{8,}"
  private static final String AMI_TYPE = "aws/image"
  private static final String PACKER_BUILD_FINISHED = "==> Builds finished. The artifacts of successful builds are:"

  ImageNameFactory imageNameFactory = new ImageNameFactory()

  @Autowired
  RoscoAWSConfiguration.AWSBakeryDefaults awsBakeryDefaults

  @Autowired
  ClouddriverService clouddriverService

  @Autowired
  RetrySupport retrySupport

  @Value('${bakeAccount}')
  String defaultAccount

  @Override
  def getBakeryDefaults() {
    return awsBakeryDefaults
  }

  @Override
  BakeOptions getBakeOptions() {
    new BakeOptions(
      cloudProvider: BakeRequest.CloudProviderType.aws,
      baseImages: awsBakeryDefaults?.baseImages?.collect { it.baseImage }
    )
  }

  @Override
  String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
    if (!bakeRequest.vm_type) {
      bakeRequest = bakeRequest.copyWith(vm_type: awsBakeryDefaults.defaultVirtualizationType)
    }

    bakeRequest.with {
      def enhancedNetworkingSegment = enhanced_networking ? 'enhancedNWEnabled' : 'enhancedNWDisabled'

      return "$region:$vm_type:$enhancedNetworkingSegment"
    }
  }

  @Override
  def findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    BakeRequest.VmType vm_type = bakeRequest.vm_type ?: awsBakeryDefaults.defaultVirtualizationType
    String account = bakeRequest.account_name ?: defaultAccount

    def awsOperatingSystemVirtualizationSettings = awsBakeryDefaults?.baseImages.find {
      it.baseImage.id == bakeRequest.base_os
    }

    if (!awsOperatingSystemVirtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for '$bakeRequest.base_os'.")
    }

    def awsVirtualizationSettings = awsOperatingSystemVirtualizationSettings?.virtualizationSettings.find {
      it.region == region && it.virtualizationType == vm_type
    }

    if (!awsVirtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for region '$region', operating system '$bakeRequest.base_os', and vm type '$vm_type'.")
    }

    awsVirtualizationSettings = awsVirtualizationSettings.clone()

    if (bakeRequest.base_ami) {
      awsVirtualizationSettings.sourceAmi = bakeRequest.base_ami
    }

    if (!awsVirtualizationSettings.sourceAmi) {
      String base = lookupBaseByDynamicProperty(region, bakeRequest)
      if (base != null && !base.matches(AMI_ID)) {
        awsVirtualizationSettings.sourceAmi = lookupAmiByName(base, region, account, vm_type, awsVirtualizationSettings.mostRecent)
      } else {
        awsVirtualizationSettings.sourceAmi = base
      }
    } else if (!awsVirtualizationSettings.sourceAmi.matches(AMI_ID)) {
      awsVirtualizationSettings.sourceAmi = lookupAmiByName(
        awsVirtualizationSettings.sourceAmi, region, account, vm_type, awsVirtualizationSettings.mostRecent)
    }

    if (awsVirtualizationSettings.sourceAmi == null) {
      throw new IllegalArgumentException("No base image found for os '$bakeRequest.base_os', vm type '$vm_type', " +
        "region '$region', label: '$bakeRequest.base_label', account: '$account'")
    }

    return awsVirtualizationSettings
  }

  @Override
  Map buildParameterMap(String region, def awsVirtualizationSettings, String imageName, BakeRequest bakeRequest, String appVersionStr) {
    def parameterMap = [
      aws_region       : region,
      aws_instance_type: awsVirtualizationSettings.instanceType,
      aws_source_ami   : awsVirtualizationSettings.sourceAmi,
      aws_target_ami   : imageName
    ]

    if (awsVirtualizationSettings.sshUserName) {
      parameterMap.aws_ssh_username = awsVirtualizationSettings.sshUserName
    }

    if (awsVirtualizationSettings.winRmUserName) {
      parameterMap.aws_winrm_username = awsVirtualizationSettings.winRmUserName
    }

    if (awsVirtualizationSettings.spotPrice) {
      parameterMap.aws_spot_price = awsVirtualizationSettings.spotPrice
    }

    if (awsVirtualizationSettings.spotPrice == "auto" && awsVirtualizationSettings.spotPriceAutoProduct) {
      parameterMap.aws_spot_price_auto_product = awsVirtualizationSettings.spotPriceAutoProduct
    }

    if (awsBakeryDefaults.awsAccessKey && awsBakeryDefaults.awsSecretKey) {
      parameterMap.aws_access_key = awsBakeryDefaults.awsAccessKey
      parameterMap.aws_secret_key = awsBakeryDefaults.awsSecretKey
    }

    if (awsBakeryDefaults.awsSubnetId) {
      parameterMap.aws_subnet_id = awsBakeryDefaults.awsSubnetId
    }

    if (awsBakeryDefaults.awsVpcId) {
      parameterMap.aws_vpc_id = awsBakeryDefaults.awsVpcId
    }

    if (awsBakeryDefaults.awsAssociatePublicIpAddress != null) {
      parameterMap.aws_associate_public_ip_address = awsBakeryDefaults.awsAssociatePublicIpAddress
    }

    if (bakeRequest.enhanced_networking) {
      parameterMap.aws_ena_support = true
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
  String getTemplateFileName(BakeOptions.BaseImage baseImage) {
    return baseImage.templateFile ?: awsBakeryDefaults.templateFile
  }

  @Override
  Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    String amiId
    String imageName
    List<Artifact> artifacts = new ArrayList<>()
    // TODO(duftler): Presently scraping the logs for the image name/id. Would be better to not be reliant on the log
    // format not changing. Resolve this by storing bake details in redis and querying oort for amiId from amiName.
    boolean foundAmisCreated = false
    logsContent.eachLine { String line ->
      if (line =~ IMAGE_NAME_TOKEN) {
        imageName = line.split(" ").last()
      } else if (line =~ UNENCRYPTED_IMAGE_NAME_TOKEN) {
        line = line.replaceAll(UNENCRYPTED_IMAGE_NAME_TOKEN, "").trim()
        imageName = line.split(" ").first()
      } else if (line =~ "$region:") {
        amiId = line.split(" ").last()
      } else if (line =~ PACKER_BUILD_FINISHED) {
        foundAmisCreated = true
      }

      if (foundAmisCreated && line =~ AMI_EXTRACTOR) {
        Artifact a = Artifact.builder()
          .type(AMI_TYPE)
          .location(line.split(": ").first())
          .reference(line.split(": ").last())
          .build()
        artifacts.add(a)
      }
    }

    return new Bake(id: bakeId, ami: amiId, image_name: imageName, artifacts: artifacts)
  }

  private String lookupAmiByName(String name, String region, String account, VmType vmType, boolean mostRecent) {
    def images = AuthenticatedRequest.allowAnonymous(
      {
        retrySupport.retry({
          clouddriverService.findAmazonImageByName(name, account, region)
        }, 3, Duration.ofSeconds(3), false)
      }
    )

    def image
    if (mostRecent) {
      def matchingImages = images?.findAll { it?.attributes?.virtualizationType == vmType }
      image = matchingImages?.max {it?.attributes?.creationDate }
    } else {
      image = images?.find { it.attributes.virtualizationType == vmType }
    }
    
    return image?.amis?.get(region)?.first()
  }
}
