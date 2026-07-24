/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.rosco.providers.proxmox

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.proxmox.config.RoscoProxmoxConfiguration
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import jakarta.annotation.PostConstruct

@Component
class ProxmoxBakeHandler extends CloudProviderBakeHandler {

  @Autowired
  CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry

  @PostConstruct
  void init() {
    cloudProviderBakeHandlerRegistry.register(BakeRequest.CloudProviderType.proxmox, this)
  }

  // The packer proxmox plugin reports the new template's VMID in its final artifact line
  // ("A template was created: <vmid>"); also accept "Template ID: <vmid>" from custom templates.
  private static final String TEMPLATE_ID_TOKEN = /(?:Template ID|A template was created):/

  ImageNameFactory imageNameFactory = new ProxmoxImageNameFactory()

  @Autowired
  RoscoProxmoxConfiguration.ProxmoxBakeryDefaults proxmoxBakeryDefaults

  @Override
  def getBakeryDefaults() {
    return proxmoxBakeryDefaults
  }

  @Override
  BakeOptions getBakeOptions() {
    new BakeOptions(
      cloudProvider: BakeRequest.CloudProviderType.proxmox,
      baseImages: proxmoxBakeryDefaults?.baseImages?.collect { it.baseImage }
    )
  }

  @Override
  def findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    def settings = proxmoxBakeryDefaults?.baseImages?.find {
      it.baseImage.id == bakeRequest.base_os
    }?.virtualizationSettings

    if (!settings) {
      throw new IllegalArgumentException("No virtualization settings found for '${bakeRequest.base_os}'.")
    }

    // Allow the caller to override the source template via base_ami.
    if (bakeRequest.base_ami) {
      settings = settings.clone()
      settings.cloneVmId = bakeRequest.base_ami as int
    }

    return settings
  }

  @Override
  Map buildParameterMap(String region, def virtualizationSettings, String imageName, BakeRequest bakeRequest, String appVersionStr) {
    def effectiveNode = virtualizationSettings.node ?: proxmoxBakeryDefaults.node

    // Use API token when configured, otherwise fall back to password auth.
    def (username, password) = proxmoxBakeryDefaults.apiToken
      ? [proxmoxBakeryDefaults.apiToken, '']
      : [proxmoxBakeryDefaults.username, proxmoxBakeryDefaults.password]

    def parameterMap = [
      proxmox_url                    : proxmoxBakeryDefaults.proxmoxUrl,
      proxmox_username               : username,
      proxmox_password               : password,
      proxmox_node                   : effectiveNode,
      proxmox_clone_vm_id            : String.valueOf(virtualizationSettings.cloneVmId),
      proxmox_vm_name                : imageName,
      proxmox_storage                : proxmoxBakeryDefaults.storage,
      proxmox_cloud_init_storage     : proxmoxBakeryDefaults.cloudInitStorage,
      proxmox_ssh_username           : virtualizationSettings.sshUsername ?: 'ubuntu',
      proxmox_cores                  : String.valueOf(virtualizationSettings.cores),
      proxmox_memory                 : String.valueOf(virtualizationSettings.memory),
      proxmox_insecure_skip_tls_verify: String.valueOf(proxmoxBakeryDefaults.insecureSkipTlsVerify),
    ]

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
    System.out.println("Loading template from " + baseImage.templateFile + " or " + proxmoxBakeryDefaults.templateFile + " Or defaulting back to proxmox-clone.json on the classpath")
    return baseImage.templateFile ?: proxmoxBakeryDefaults.templateFile ?: 'proxmox-clone.json'
  }

  @Override
  Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    String templateId
    String imageName

    logsContent.eachLine { String line ->
      if (line =~ TEMPLATE_ID_TOKEN) {
        templateId = line.split(':').last().trim()
      }
    }

    imageName = templateId ?: bakeId

    return new Bake(id: bakeId, image_name: imageName)
  }

  @Override
  List<String> getMaskedPackerParameters() {
    // Mask the password/token so it never appears in logs.
    return ['proxmox_password']
  }
}
