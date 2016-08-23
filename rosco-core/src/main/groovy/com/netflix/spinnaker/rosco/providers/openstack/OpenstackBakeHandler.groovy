/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.rosco.providers.openstack

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.openstack.config.RoscoOpenstackConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * TODO(drmaas): need to figure out the following:
 * 1) packer openstack variables - required vs optional (currently have a best guess for what is required for identity v3)
 * 2) openstack username/password management (currently will load via environment variable passed to java process)
 */
@Component
public class OpenstackBakeHandler extends CloudProviderBakeHandler {

  private static final String BUILDER_TYPE = 'openstack'
  private static final String IMAGE_NAME_TOKEN = 'openstack: An image was created:'

  ImageNameFactory imageNameFactory = new ImageNameFactory()

  @Autowired
  RoscoOpenstackConfiguration.OpenstackBakeryDefaults openstackBakeryDefaults

  @Override
  def getBakeryDefaults() {
    return openstackBakeryDefaults
  }

  @Override
  BakeOptions getBakeOptions() {
    new BakeOptions(
      cloudProvider: BakeRequest.CloudProviderType.openstack,
      baseImages: openstackBakeryDefaults?.baseImages?.collect { it.baseImage }
    )
  }

  @Override
  String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
    region
  }

  @Override
  def findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    def openstackOperatingSystemVirtualizationSettings = openstackBakeryDefaults?.baseImages.find {
      it.baseImage.id == bakeRequest.base_os
    }

    if (!openstackOperatingSystemVirtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for '$bakeRequest.base_os'.")
    }

    def openstackVirtualizationSettings = openstackOperatingSystemVirtualizationSettings?.virtualizationSettings.find {
      it.region == region
    }

    if (!openstackVirtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for region '$region' and operating system '$bakeRequest.base_os'.")
    }

    if (bakeRequest.base_ami) {
      openstackVirtualizationSettings = openstackVirtualizationSettings.clone()
      openstackVirtualizationSettings.sourceImageId = bakeRequest.base_ami
    }

    return openstackVirtualizationSettings
  }

  @Override
  Map buildParameterMap(String region, def openstackVirtualizationSettings, String imageName, BakeRequest bakeRequest) {
    def parameterMap = [
      openstack_identity_endpoint: openstackBakeryDefaults.identityEndpoint,
      openstack_region: region,
      openstack_ssh_username: openstackVirtualizationSettings.sshUserName,
      openstack_instance_type: openstackVirtualizationSettings.instanceType,
      openstack_source_image_id: openstackVirtualizationSettings.sourceImageId,
      openstack_image_name: imageName
    ]

    if (openstackBakeryDefaults.username && openstackBakeryDefaults.password) {
      parameterMap.openstack_username = openstackBakeryDefaults.username
      parameterMap.openstack_password = openstackBakeryDefaults.password
    }

    if (openstackBakeryDefaults.domainName) {
      parameterMap.openstack_domain_name = openstackBakeryDefaults.domainName
    }

    if (openstackBakeryDefaults.floatingIpPool) {
      parameterMap.openstack_floating_ip_pool = openstackBakeryDefaults.floatingIpPool
    }

    if (openstackBakeryDefaults.insecure != null) {
      parameterMap.openstack_insecure = openstackBakeryDefaults.insecure
    }

    if (openstackBakeryDefaults.securityGroups != null) {
      parameterMap.openstack_security_groups = openstackBakeryDefaults.securityGroups
    }

    if (openstackBakeryDefaults.tenantName != null) {
      parameterMap.openstack_tenant_name = openstackBakeryDefaults.tenantName
    }

    if (bakeRequest.build_info_url) {
      parameterMap.build_info_url = bakeRequest.build_info_url
    }

    return parameterMap
  }

  @Override
  String getTemplateFileName() {
    return openstackBakeryDefaults.templateFile
  }

  @Override
  boolean isProducerOf(String logsContentFirstLine) {
    logsContentFirstLine =~ BUILDER_TYPE
  }

  @Override
  Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    String imageName

    // TODO(duftler): Presently scraping the logs for the image name/id. Would be better to not be reliant on the log
    // format not changing. Resolve this by storing bake details in redis and querying oort for amiId from amiName.
    logsContent.eachLine { String line ->
      if (line =~ IMAGE_NAME_TOKEN) {
        imageName = line.split(" ").last()
      }
    }

    new Bake(id: bakeId, image_name: imageName)
  }

  @Override
  List<String> getMaskedPackerParameters() {
    ['openstack_password']
  }
}
