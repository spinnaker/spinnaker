/*
 * Copyright 2016 Microsoft, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rosco.providers.azure

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.azure.config.RoscoAzureConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.time.Clock

@Component
public class AzureBakeHandler extends CloudProviderBakeHandler{

  private static final String IMAGE_NAME_TOKEN = "OSDiskUri:"

  ImageNameFactory imageNameFactory = new ImageNameFactory()

  @Autowired
  RoscoAzureConfiguration.AzureBakeryDefaults azureBakeryDefaults

  @Autowired
  RoscoAzureConfiguration.AzureConfigurationProperties azureConfigurationProperties

  @Override
  def getBakeryDefaults() {
    return azureBakeryDefaults
  }

  @Override
  BakeOptions getBakeOptions() {
    new BakeOptions(
      cloudProvider: BakeRequest.CloudProviderType.azure,
      baseImages: azureBakeryDefaults?.baseImages?.collect { it.baseImage }
    )
  }

  @Override
  Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    String imageName
    String ami

    // TODO(duftler): Presently scraping the logs for the image name. Would be better to not be reliant on the log
    // format not changing. Resolve this by storing bake details in redis.
    logsContent.eachLine { String line ->
      if (line =~ IMAGE_NAME_TOKEN) {
        imageName = line.split("/").last()
        ami = "https:" + line.split(":").last()
      }
    }

    return new Bake(id: bakeId, image_name: imageName, ami: ami)
  }

  @Override
  def findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    return null
  }

  @Override
  Map buildParameterMap(String region, def virtualizationSettings, String imageName, BakeRequest bakeRequest, String appVersionStr) {

    def selectedImage = azureBakeryDefaults?.baseImages?.find { it.baseImage.id == bakeRequest.base_os }

    // TODO(larrygug): Presently rosco is only supporting a single account. Need to update to support a named account
    def selectedAccount = azureConfigurationProperties?.accounts?.get(0)

    def parameterMap = [
      azure_client_id: selectedAccount?.clientId,
      azure_client_secret: selectedAccount?.appKey,
      azure_resource_group: selectedAccount?.packerResourceGroup,
      azure_storage_account: selectedAccount?.packerStorageAccount,
      azure_subscription_id: selectedAccount?.subscriptionId,
      azure_tenant_id: selectedAccount?.tenantId,
      azure_object_id: selectedAccount?.objectId,
      azure_location: region,
      azure_image_publisher: selectedImage?.baseImage?.publisher,
      azure_image_offer: selectedImage?.baseImage?.offer,
      azure_image_sku: selectedImage?.baseImage?.sku
    ]

    if (bakeRequest.build_number && bakeRequest.base_name) {
      parameterMap.azure_image_name = "$bakeRequest.build_number-$bakeRequest.base_name"
    } else if (imageName) {
      parameterMap.azure_image_name = imageName
    } else {
      parameterMap.azure_image_name = Clock.systemUTC.millis().toString()
    }

    // Ensure 'azure_image_name' conforms to CaptureNamePrefix regex in packer.
    // https://github.com/mitchellh/packer/blob/master/builder/azure/arm/config.go#L45
    def azureImageName = parameterMap.azure_image_name
    azureImageName = azureImageName.replaceAll(/[^A-Za-z0-9_\-\.]/, "")
    azureImageName = azureImageName.length() <= 23 ? azureImageName : azureImageName.substring(0, 23)
    azureImageName = azureImageName.replaceAll(/[\-\.]+$/, "")

    parameterMap.azure_image_name = azureImageName

    if (appVersionStr) {
      parameterMap.appversion = appVersionStr
    }

    return parameterMap
  }

  @Override
  String getTemplateFileName(BakeOptions.BaseImage baseImage) {
    return baseImage.templateFile ?: azureBakeryDefaults.templateFile
  }
}
