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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.azure.config.RoscoAzureConfiguration
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AzureBakeHandlerSpec extends Specification{

  @Shared
  String configDir = "/some/path"

  @Shared
  RoscoAzureConfiguration.AzureBakeryDefaults azureBakeryDefaults

  void setupSpec() {
    def azureBakeryDefaultsJson = [
      templateFile: "azure-linux.json",
      baseImages: [
        [
          baseImage: [
            id: "ubuntu",
            detailedDescription: "Ubuntu Server 14.04.4-LTS",
            packageType: "DEB",
          ]
        ],
        [
          baseImage: [
            id: "centos",
            detailedDescription: "OpenLogic CentOS 7.1.20150731",
            packageType: "RPM",
          ]
        ]
      ]
    ]

    azureBakeryDefaults = new ObjectMapper().convertValue(azureBakeryDefaultsJson, RoscoAzureConfiguration.AzureBakeryDefaults)
  }

  void 'can scrape packer logs for image name'() {
    setup:
    @Subject
    AzureBakeHandler azureBakeHandler = new AzureBakeHandler()

    when:
    def logsContent =
      "==> azure-arm: Deleting the temporary OS disk ...\n" +
      "==> azure-arm:  -> OS Disk             : 'https://lgpackervms.blob.core.windows.net/images/pkroswfvmtp50x8.vhd'\n" +
      "==> azure-arm:\n" +
      "==> azure-arm: Cleanup requested, deleting resource group ...\n" +
      "==> azure-arm: Error deleting resource group.  Please delete it manually.\n" +
      "==> azure-arm:\n" +
      "==> azure-arm: Name: packer-Resource-Group-wfvmtp50x8\n" +
      "==> azure-arm: Error: azure#updatePollingState: Azure Polling Error - Unable to obtain polling URI for DELETE : StatusCode=0\n" +
      "==> azure-arm: Resource group has been deleted.\n" +
      "Build 'azure-arm' finished.\n" +
      "\n" +
      "==> Builds finished. The artifacts of successful builds are:\n" +
      "--> azure-arm: Azure.ResourceManagement.VMImage:\n" +
      "\n" +
      "StorageAccountLocation: westus\n" +
      "OSDiskUri: https://lgpackervms.blob.core.windows.net/system/Microsoft.Compute/Images/images/packer-osDisk.0425d8dd-45a0-4f2e-aabb-b5f9a03b08c9.vhd\n" +
      "OSDiskUriReadOnlySas: https://lgpackervms.blob.core.windows.net/system/Microsoft.Compute/Images/images/packer-osDisk.0425d8dd-45a0-4f2e-aabb-b5f9a03b08c9.vhd?se=2016-06-24T18%3A16%3A46Z&sig=RHZXFGD3gZq6BGo%2BOb09FXHW6BAYULVJ8thlBEblkmo%3D&sp=r&sr=b&sv=2015-02-21"


    Bake bake = azureBakeHandler.scrapeCompletedBakeResults(null, "123", logsContent)

    then:
    with (bake) {
      id == "123"
      !ami
      image_name == "packer-osDisk.0425d8dd-45a0-4f2e-aabb-b5f9a03b08c9.vhd"
    }
  }

  void 'template file name data is serialized as expected'() {
    setup:
    @Subject
    AzureBakeHandler azureBakeHandler = new AzureBakeHandler(azureBakeryDefaults: azureBakeryDefaults)

    when:
    String templateFileName = azureBakeHandler.getTemplateFileName()

    then:
    templateFileName == "azure-linux.json"
  }

  void 'image config data is serialized as expected'() {
    setup:
    @Subject
    AzureBakeHandler azureBakeHandler = new AzureBakeHandler(azureBakeryDefaults: azureBakeryDefaults)

    when:
    BakeOptions bakeOptions = azureBakeHandler.getBakeOptions()

    then:
    with(bakeOptions) {
      baseImages.size() == 2
      cloudProvider == BakeRequest.CloudProviderType.azure.toString()
    }
  }
}
