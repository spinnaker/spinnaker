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
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackageNameConverter
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AzureBakeHandlerSpec extends Specification implements TestDefaults{

  private static final String REGION = "westus"
  private static final String CLIENT_ID = "123ABC-456DEF"
  private static final String IMAGE_PUBLISHER = "Canonical"
  private static final String CLIENT_SECRET = "blahblah"
  private static final String RESOURCE_GROUP =  "resGroup"
  private static final String STORAGE_ACCOUNT = "packerStorage"
  private static final String SUBSCRIPTION_ID =  "123ABC"
  private static final String TENANT_ID = "DEF456"
  private static final String IMAGE_OFFER = "UbuntuServer"
  private static final String IMAGE_SKU = "14.04.3-LTS"
  private static final String BUILD_NUMBER = "42"
  private static final String BUILD_NAME = "production"
  private static final String IMAGE_NAME = "$BUILD_NUMBER-$BUILD_NAME"

  @Shared
  String configDir = "/some/path"

  @Shared
  RoscoAzureConfiguration.AzureBakeryDefaults azureBakeryDefaults

  @Shared
  RoscoAzureConfiguration.AzureConfigurationProperties azureConfigurationProperties

  void setupSpec() {
    def azureBakeryDefaultsJson = [
      templateFile: "azure-linux.json",
      baseImages: [
        [
          baseImage: [
            id: "ubuntu",
            shortDescription: "v14.04",
            detailedDescription: "Ubuntu Server 14.04.4-LTS",
            publisher: IMAGE_PUBLISHER,
            offer: IMAGE_OFFER,
            sku: IMAGE_SKU,
            version: "14.04.201602171",
            packageType: "DEB",
          ]
        ],
        [
          baseImage: [
            id: "centos",
            shortDescription: "7",
            detailedDescription: "OpenLogic CentOS 7.1.20150731",
            publisher: "OpenLogic",
            offer: "CentOS",
            sku: "7.1",
            version: "7.1.20150731",
            packageType: "RPM",
          ]
        ]
      ]
    ]

    def azureConfigurationPropertiesJson = [
      accounts: [
        [
          name: "azure-1",
          clientId: CLIENT_ID,
          appKey: CLIENT_SECRET,
          tenantId: TENANT_ID,
          subscriptionId: SUBSCRIPTION_ID,
          packerResourceGroup: RESOURCE_GROUP,
          packerStorageAccount: STORAGE_ACCOUNT
        ]
      ]
    ]

    azureBakeryDefaults = new ObjectMapper().convertValue(azureBakeryDefaultsJson, RoscoAzureConfiguration.AzureBakeryDefaults)
    azureConfigurationProperties = new ObjectMapper().convertValue(azureConfigurationPropertiesJson, RoscoAzureConfiguration.AzureConfigurationProperties)
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
        ami == "https://lgpackervms.blob.core.windows.net/system/Microsoft.Compute/Images/images/packer-osDisk.0425d8dd-45a0-4f2e-aabb-b5f9a03b08c9.vhd"
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

  void 'produces packer command with all required parameters for ubuntu'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
        package_name: PACKAGES_NAME,
        base_os: "ubuntu",
        cloud_provider_type: BakeRequest.CloudProviderType.azure,
        template_file_name: "azure-linux.json",
        build_number: BUILD_NUMBER,
        base_name: BUILD_NAME)
      def targetImageName = "myapp"
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def parameterMap = [
        azure_client_id: CLIENT_ID,
        azure_client_secret: CLIENT_SECRET,
        azure_resource_group: RESOURCE_GROUP,
        azure_storage_account: STORAGE_ACCOUNT,
        azure_subscription_id: SUBSCRIPTION_ID,
        azure_tenant_id: TENANT_ID,
        azure_location: REGION,
        azure_image_publisher: IMAGE_PUBLISHER,
        azure_image_offer: IMAGE_OFFER,
        azure_image_sku: IMAGE_SKU,
        azure_image_name: IMAGE_NAME,
        repository: DEBIAN_REPOSITORY,
        package_type: BakeRequest.PackageType.DEB.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      AzureBakeHandler azureBakeHandler = new AzureBakeHandler(configDir: configDir,
        azureBakeryDefaults: azureBakeryDefaults,
        imageNameFactory: imageNameFactoryMock,
        packerCommandFactory: packerCommandFactoryMock,
        debianRepository: DEBIAN_REPOSITORY,
        azureConfigurationProperties: azureConfigurationProperties)

    when:
      azureBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(BakeRequest.PackageType.DEB, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/azure-linux.json")
  }
}
