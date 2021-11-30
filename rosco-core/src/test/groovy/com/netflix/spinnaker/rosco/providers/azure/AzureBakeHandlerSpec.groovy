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
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AzureBakeHandlerSpec extends Specification implements TestDefaults{

  private static final String REGION = "westus"
  private static final String CLIENT_ID = "123ABC-456DEF"
  private static final String IMAGE_PUBLISHER = "Canonical"
  private static final String IMAGE_PUBLISHER_WINDOWS = "MicrosoftWindowsServer"
  private static final String CLIENT_SECRET = "blahblah"
  private static final String RESOURCE_GROUP =  "resGroup"
  private static final String STORAGE_ACCOUNT = "packerStorage"
  private static final String SUBSCRIPTION_ID =  "123ABC"
  private static final String TENANT_ID = "DEF456"
  private static final String OBJECT_ID = "2121ce3f-ad4f-4c93-8e1d-62884a22eba6"
  private static final String IMAGE_OFFER = "UbuntuServer"
  private static final String IMAGE_OFFER_WINDOWS = "WindowsServer"
  private static final String IMAGE_SKU = "14.04.3-LTS"
  private static final String IMAGE_SKU_WINDOWS = "2012-R2-Datacenter"
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
            templateFile: "azure-centos.json",
          ]
        ],
        [
          baseImage: [
            id: "windows-2012-r2",
            shortDescription: "2012 R2",
            detailedDescription: "Windows Server 2012 R2 Datacenter",
            publisher: IMAGE_PUBLISHER_WINDOWS,
            offer: IMAGE_OFFER_WINDOWS,
            sku: IMAGE_SKU_WINDOWS,
            version: "4.0.20170111",
            packageType: "NUPKG",
            templateFile: "azure-windows-2012-r2.json",
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
          objectId: OBJECT_ID,
          subscriptionId: SUBSCRIPTION_ID,
          packerResourceGroup: RESOURCE_GROUP,
          packerStorageAccount: STORAGE_ACCOUNT
        ],
        [
          name: "azure-2",
          clientId: "456DEF-123ABC",
          appKey: "foobar",
          tenantId: "ABC123",
          objectId: "62884a22-8e1d-4c93-ad4f-2121ce3feba6",
          subscriptionId: "456DEF",
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
        "ManagedImageName: pkroswfvmtp50x8\n" +
        "ManagedImageId: pkroswfvmtp50x8id"


      Bake bake = azureBakeHandler.scrapeCompletedBakeResults(null, "123", logsContent)

    then:
      with (bake) {
        id == "123"
        ami == "pkroswfvmtp50x8id"
        image_name == "pkroswfvmtp50x8"
      }
  }

  void 'can scrape packer logs for image name for windows'() {
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
        "ManagedImageName: pkroswfvmtp50x8\n" +
        "ManagedImageId: pkroswfvmtp50x8id"


    Bake bake = azureBakeHandler.scrapeCompletedBakeResults(null, "123", logsContent)

    then:
    with (bake) {
      id == "123"
      ami == "pkroswfvmtp50x8id"
      image_name == "pkroswfvmtp50x8"
    }
  }

  void 'can scrape packer logs for image name in SIG'() {
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
                    "ManagedImageName: pkroswfvmtp50x8\n" +
                    "ManagedImageSharedImageGalleryId: pkroswfvmtp50x8id"


    Bake bake = azureBakeHandler.scrapeCompletedBakeResults(null, "123", logsContent)

    then:
    with (bake) {
      id == "123"
      ami == "pkroswfvmtp50x8id"
      image_name == "pkroswfvmtp50x8"
    }
  }

  void 'can scrape packer logs for image name for windows in SIG'() {
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
                    "ManagedImageName: pkroswfvmtp50x8\n" +
                    "ManagedImageSharedImageGalleryId: pkroswfvmtp50x8id"


    Bake bake = azureBakeHandler.scrapeCompletedBakeResults(null, "123", logsContent)

    then:
    with (bake) {
      id == "123"
      ami == "pkroswfvmtp50x8id"
      image_name == "pkroswfvmtp50x8"
    }
  }

  void 'template file name data is serialized as expected'() {
    setup:
      @Subject
      AzureBakeHandler azureBakeHandler = new AzureBakeHandler(azureBakeryDefaults: azureBakeryDefaults)

    when:
      String templateFileName = azureBakeHandler.getTemplateFileName(azureBakeHandler.bakeOptions.baseImages[1])

    then:
      templateFileName == "azure-centos.json"
  }

  void 'template file name data is serialized as expected for windows'() {
    setup:
    @Subject
    AzureBakeHandler azureBakeHandler = new AzureBakeHandler(azureBakeryDefaults: azureBakeryDefaults)

    when:
    String templateFileName = azureBakeHandler.getTemplateFileName(azureBakeHandler.bakeOptions.baseImages[2])

    then:
    templateFileName == "azure-windows-2012-r2.json"
  }

  void 'image config data is serialized as expected'() {
    setup:
      @Subject
      AzureBakeHandler azureBakeHandler = new AzureBakeHandler(azureBakeryDefaults: azureBakeryDefaults)

    when:
      BakeOptions bakeOptions = azureBakeHandler.getBakeOptions()

    then:
      with(bakeOptions) {
        baseImages.size() == 3
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
        azure_subscription_id: SUBSCRIPTION_ID,
        azure_tenant_id: TENANT_ID,
        azure_object_id: OBJECT_ID,
        azure_location: REGION,
        azure_image_publisher: IMAGE_PUBLISHER,
        azure_image_offer: IMAGE_OFFER,
        azure_image_sku: IMAGE_SKU,
        azure_managed_image_name: IMAGE_NAME,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
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
      azureBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/azure-linux.json")
  }

  void 'produces packer command with all required parameters for windows'() {
    setup:
    def imageNameFactoryMock = Mock(ImageNameFactory)
    def packerCommandFactoryMock = Mock(PackerCommandFactory)
    def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
      package_name: NUPKG_PACKAGES_NAME,
      base_os: "windows-2012-r2",
      cloud_provider_type: BakeRequest.CloudProviderType.azure,
      build_number: BUILD_NUMBER,
      base_name: BUILD_NAME)
    def targetImageName = "googlechrome-all-20170121212839-windows-2012-r2"
    def osPackages = parseNupkgOsPackageNames(NUPKG_PACKAGES_NAME)
    def parameterMap = [
      azure_client_id: CLIENT_ID,
      azure_client_secret: CLIENT_SECRET,
      azure_resource_group: RESOURCE_GROUP,
      azure_subscription_id: SUBSCRIPTION_ID,
      azure_tenant_id: TENANT_ID,
      azure_object_id: OBJECT_ID,
      azure_location: REGION,
      azure_image_publisher: IMAGE_PUBLISHER_WINDOWS,
      azure_image_offer: IMAGE_OFFER_WINDOWS,
      azure_image_sku: IMAGE_SKU_WINDOWS,
      azure_managed_image_name: IMAGE_NAME,
      repository: CHOCOLATEY_REPOSITORY,
      package_type: NUPKG_PACKAGE_TYPE.util.packageType,
      packages: NUPKG_PACKAGES_NAME,
      configDir: configDir
    ]

    @Subject
    AzureBakeHandler azureBakeHandler = new AzureBakeHandler(configDir: configDir,
      azureBakeryDefaults: azureBakeryDefaults,
      imageNameFactory: imageNameFactoryMock,
      packerCommandFactory: packerCommandFactoryMock,
      chocolateyRepository: CHOCOLATEY_REPOSITORY,
      azureConfigurationProperties: azureConfigurationProperties)

    when:
    azureBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
    1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
    1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, NUPKG_PACKAGE_TYPE) >> null
    1 * imageNameFactoryMock.buildPackagesParameter(NUPKG_PACKAGE_TYPE, osPackages) >> NUPKG_PACKAGES_NAME
    1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/azure-windows-2012-r2.json")
  }

  void 'Create proper azure_image_name'() {
    setup:
    def azureBakeHandler = new AzureBakeHandler(azureBakeryDefaults: azureBakeryDefaults, azureConfigurationProperties: azureConfigurationProperties)

    when:
    def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
      package_name: NUPKG_PACKAGES_NAME,
      base_os: "windows-2012-r2",
      cloud_provider_type: BakeRequest.CloudProviderType.azure,
      build_number: buildNumber,
      base_name: baseName)
    def azureVirtualizationSettings = azureBakeHandler.findVirtualizationSettings(REGION, bakeRequest)
    def parameterMap = azureBakeHandler.buildParameterMap(REGION, azureVirtualizationSettings, imageName, bakeRequest, "SomeVersion")

    then:
    parameterMap.azure_managed_image_name == expectedAzureImageName

    where:
    buildNumber   | baseName    | imageName                                           || expectedAzureImageName
    null          | null        | "namethatexceeds.23char.acters"                     || "namethatexceeds.23char.acters"
    BUILD_NUMBER  | BUILD_NAME  | IMAGE_NAME                                          || IMAGE_NAME
    BUILD_NUMBER  | BUILD_NAME  | null                                                || IMAGE_NAME
    BUILD_NUMBER  | null        | IMAGE_NAME                                          || IMAGE_NAME
    null          | BUILD_NAME  | IMAGE_NAME                                          || IMAGE_NAME
    null          | null        | IMAGE_NAME                                          || IMAGE_NAME
    null          | null        | "test-with!>#characters.morethanmorethanmorethanmorethanmorethanmorethan.75characters"      || "test-withcharacters.morethanmorethanmorethanmorethanmorethanmorethan.75char"
    null          | null        | "test-with!>#characters..-."                        || "test-withcharacters"
    BUILD_NUMBER  | BUILD_NAME  | "this-is-a--test-withnamethatexceeds.75characters"  || IMAGE_NAME
  }

  void 'account selection is reflected in bake key'() {
    setup:
    def imageNameFactoryMock = Mock(ImageNameFactory)
    def packerCommandFactoryMock = Mock(PackerCommandFactory)
    def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                      package_name: NUPKG_PACKAGES_NAME,
                                      base_os: "windows-2012-r2",
                                      cloud_provider_type: BakeRequest.CloudProviderType.azure,
                                      account_name: "azure-2")

    @Subject
    AzureBakeHandler azureBakeHandler = new AzureBakeHandler(configDir: configDir,
      azureBakeryDefaults: azureBakeryDefaults,
      imageNameFactory: imageNameFactoryMock,
      packerCommandFactory: packerCommandFactoryMock,
      chocolateyRepository: CHOCOLATEY_REPOSITORY,
      azureConfigurationProperties: azureConfigurationProperties)

    when:
    def parameterMap = azureBakeHandler.buildParameterMap(REGION, null, null, bakeRequest, null)
    String bakeKey = azureBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
    bakeKey == "bake:azure:windows-2012-r2:googlechrome|javaruntime:azure-2"
    parameterMap.azure_client_id == azureConfigurationProperties?.accounts?.find { it.name == "azure-2" }?.clientId
  }
}
