/*
 * Copyright 2019 Alibaba Group, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.rosco.providers.alicloud

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.config.RoscoConfiguration
import com.netflix.spinnaker.rosco.providers.alicloud.config.RoscoAliCloudConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AliCloudBakeHandlerSpec extends Specification implements TestDefaults {

  private static final String REGION = "cn-hangzhou"
  private static final String SOURCE_UBUNTU_IMAGE_NAME = "ubuntu_16_04_64.vhd"
    private static final String SOURCE_TRUSTY_IMAGE_NAME = "trusty_16_04_64.vhd"

  @Shared
  String configDir = "/some/path"

  @Shared
  RoscoAliCloudConfiguration.AliCloudBakeryDefaults alicloudBakeryDefaults

  void setupSpec() {
    def alicloudBakeryDefaultsJson = [
      templateFile: "alicloud_template.json",
      baseImages: [
        [
          baseImage: [
            id: "ubuntu",
            packageType: "DEB",
          ],
          virtualizationSettings: [
            [
              region: REGION,
              instanceType: "ecs.c5.large",
              sourceImage: SOURCE_UBUNTU_IMAGE_NAME,
              sshUserName: "root"
            ],
            [
              region: REGION,
              instanceType: "ecs.g5.large",
              sourceImage: SOURCE_UBUNTU_IMAGE_NAME,
              sshUserName: "root"
            ]
          ]
        ],
        [
          baseImage: [
            id: "trusty",
            packageType: "DEB",
          ],
          virtualizationSettings: [
            [
              region: REGION,
              instanceType: "ecs.c5.large",
              sourceImage: SOURCE_TRUSTY_IMAGE_NAME,
              sshUserName: "root"
            ]
          ]
        ]
      ]
    ]

    alicloudBakeryDefaults = new ObjectMapper().convertValue(alicloudBakeryDefaultsJson, RoscoAliCloudConfiguration.AliCloudBakeryDefaults)
  }

  void 'can scrape packer logs for image name'() {
    setup:
      @Subject
      AliCloudBakeHandler aliCloudBakeHandler = new AliCloudBakeHandler(alicloudBakeryDefaults: alicloudBakeryDefaults)

    when:
      def logsContent =
        "    alicloud-ecs: Reading package lists...\n" +
        "==> alicloud-ecs: Deleting image snapshots.\n" +
        "==> alicloud-ecs: Creating image: all-timestamp-ubuntu-1604\n" +
        "alicloud-ecs: Detach keypair packer_5d204762-9187cb8 from instance: i-12345abced\n" +
        "==> alicloud-ecs: Cleaning up 'EIP'\n" +
        "==> alicloud-ecs: Cleaning up 'instance'\n" +
        "==> alicloud-ecs: Cleaning up 'security group'\n" +
        "==> alicloud-ecs: Cleaning up 'vSwitch'\n" +
        "==> alicloud-ecs: Cleaning up 'VPC'\n" +
        "==> alicloud-ecs: Deleting temporary keypair...\n" +
        "Build 'alicloud-ecs' finished.\n" +
        "\n" +
        "==> Builds finished. The artifacts of successful builds are:\n" +
        "--> alicloud-ecs: Alicloud images were created:\n" +
        "\n" +
        "cn-hangzhou: m-12345abced"

      Bake bake = aliCloudBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)


    then:
      with (bake) {
        id == "123"
        ami == "m-12345abced"
        image_name == "all-timestamp-ubuntu-1604"
      }
  }

  void 'scraping returns null for missing image id'() {
    setup:
      @Subject
      AliCloudBakeHandler aliCloudBakeHandler = new AliCloudBakeHandler(alicloudBakeryDefaults: alicloudBakeryDefaults)

    when:
      def logsContent =
        "    alicloud-ecs: Reading package lists...\n" +
        "==> alicloud-ecs: Deleting image snapshots.\n" +
        "==> alicloud-ecs: Creating image: all-timestamp-ubuntu-1604\n" +
        "alicloud-ecs: Detach keypair packer_5d204762-9187cb8 from instance: i-12345abced\n" +
        "==> alicloud-ecs: Cleaning up 'EIP'\n" +
        "==> alicloud-ecs: Cleaning up 'instance'\n" +
        "==> alicloud-ecs: Cleaning up 'security group'\n" +
        "==> alicloud-ecs: Cleaning up 'vSwitch'\n" +
        "==> alicloud-ecs: Cleaning up 'VPC'\n" +
        "==> alicloud-ecs: Deleting temporary keypair...\n" +
        "Build 'alicloud-ecs' finished.\n" +
        "\n" +
        "==> Builds finished. The artifacts of successful builds are:\n" +
        "--> alicloud-ecs: Alicloud images were created:\n"

      Bake bake = aliCloudBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

    then:
      with (bake) {
        id == "123"
        !ami
        image_name == "all-timestamp-ubuntu-1604"
      }
  }

  void 'scraping returns null for missing image name'() {
    setup:
      @Subject
      AliCloudBakeHandler aliCloudBakeHandler = new AliCloudBakeHandler(alicloudBakeryDefaults: alicloudBakeryDefaults)

    when:
      def logsContent =
        "    alicloud-ecs: Reading package lists...\n" +
        "==> alicloud-ecs: Deleting image snapshots.\n"

    Bake bake = aliCloudBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

    then:
      with (bake) {
        id == "123"
        !ami
        !image_name
      }
  }

  void 'produces packer command with all required parameters for ubuntu'() {
    setup:
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.alicloud)
      def targetImageName = "kato-timestamp-ubuntu"
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def parameterMap = [
        alicloud_region: REGION,
        alicloud_ssh_username: "root",
        alicloud_instance_type: "ecs.c5.large",
        alicloud_source_image: SOURCE_UBUNTU_IMAGE_NAME,
        alicloud_target_image: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      AliCloudBakeHandler aliCloudBakeHandler = new AliCloudBakeHandler(configDir: configDir,
                                                         alicloudBakeryDefaults: alicloudBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
    aliCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$alicloudBakeryDefaults.templateFile")
  }


  void 'produces packer command with all required parameters for ubuntu, and overriding base ami'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        base_ami: "ubuntu_14_04_64.vhd",
                                        cloud_provider_type: BakeRequest.CloudProviderType.alicloud)
      def targetImageName = "kato-timestamp-ubuntu"
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def parameterMap = [
        alicloud_region: REGION,
        alicloud_ssh_username: "root",
        alicloud_instance_type: "ecs.c5.large",
        alicloud_source_image: "ubuntu_14_04_64.vhd",
        alicloud_target_image: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      AliCloudBakeHandler aliCloudBakeHandler = new AliCloudBakeHandler(configDir: configDir,
                                                         alicloudBakeryDefaults: alicloudBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
    aliCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$alicloudBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for ubuntu, and overriding template filename'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.alicloud,
                                        template_file_name: "somePackerTemplate.json")
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def targetImageName = "kato-timestamp-ubuntu"
      def parameterMap = [
        alicloud_region: REGION,
        alicloud_ssh_username: "root",
        alicloud_instance_type: "ecs.c5.large",
        alicloud_source_image: SOURCE_UBUNTU_IMAGE_NAME,
        alicloud_target_image: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      AliCloudBakeHandler aliCloudBakeHandler = new AliCloudBakeHandler(configDir: configDir,
                                                         alicloudBakeryDefaults: alicloudBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      aliCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/somePackerTemplate.json")
  }

  void 'produces packer command with all required parameters for ubuntu, and adding extended attributes'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.alicloud,
                                        extended_attributes: [someAttr1: "someValue1", someAttr2: "someValue2"])
      def targetImageName = "kato-timestamp-ubuntu"
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def parameterMap = [
        alicloud_region: REGION,
        alicloud_ssh_username: "root",
        alicloud_instance_type: "ecs.c5.large",
        alicloud_source_image: SOURCE_UBUNTU_IMAGE_NAME,
        alicloud_target_image: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        someAttr1: "someValue1",
        someAttr2: "someValue2"
      ]

      @Subject
      AliCloudBakeHandler aliCloudBakeHandler = new AliCloudBakeHandler(configDir: configDir,
                                                         alicloudBakeryDefaults: alicloudBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      aliCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$alicloudBakeryDefaults.templateFile")
  }

  void 'throws exception when virtualization settings are not found for specified operating system'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        cloud_provider_type: BakeRequest.CloudProviderType.alicloud)

      @Subject
      AliCloudBakeHandler aliCloudBakeHandler = new AliCloudBakeHandler(alicloudBakeryDefaults: alicloudBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      aliCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      IllegalArgumentException e = thrown()
      e.message == "No virtualization settings found for 'centos'."
  }

  void 'throws exception when virtualization settings are not found for specified region, operating system'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "trusty",
                                        cloud_provider_type: BakeRequest.CloudProviderType.alicloud)

      @Subject
      AliCloudBakeHandler aliCloudBakeHandler = new AliCloudBakeHandler(alicloudBakeryDefaults: alicloudBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      aliCloudBakeHandler.produceBakeRecipe("cn-beijing", bakeRequest)

    then:
      IllegalArgumentException e = thrown()
      e.message == "No virtualization settings found for region 'cn-beijing', operating system 'trusty'."
  }

  void 'produce a default Alibaba Cloud bakeKey with image name'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        cloud_provider_type: BakeRequest.CloudProviderType.alicloud,
                                        ami_name: "kato-app")
      @Subject
      AliCloudBakeHandler aliCloudBakeHandler = new AliCloudBakeHandler(alicloudBakeryDefaults: alicloudBakeryDefaults)

    when:
      String bakeKey = aliCloudBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:alicloud:centos:kato-app:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:cn-hangzhou"
  }

  void 'do not consider ami suffix when composing bake key'() {
    setup:
      def bakeRequest1 = new BakeRequest(user: "someuser@gmail.com",
                                         package_name: PACKAGES_NAME,
                                         base_os: "centos",
                                         cloud_provider_type: BakeRequest.CloudProviderType.alicloud,
                                         ami_suffix: "1.0")
      def bakeRequest2 = new BakeRequest(user: "someuser@gmail.com",
                                         package_name: PACKAGES_NAME,
                                         base_os: "centos",
                                         cloud_provider_type: BakeRequest.CloudProviderType.alicloud,
                                         ami_suffix: "2.0")
      @Subject
      AliCloudBakeHandler aliCloudBakeHandler = new AliCloudBakeHandler(alicloudBakeryDefaults: alicloudBakeryDefaults)

    when:
      String bakeKey1 = aliCloudBakeHandler.produceBakeKey(REGION, bakeRequest1)
      String bakeKey2 = aliCloudBakeHandler.produceBakeKey(REGION, bakeRequest2)

    then:
      bakeKey1 == "bake:alicloud:centos:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:cn-hangzhou"
      bakeKey2 == bakeKey1
  }

  void 'produces packer command with all required parameters including shared_with multiple accounts as extended_attribute'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def fullyQualifiedPackageName = "nflx-djangobase-enhanced_0.1-h12.170cdbd_all"
      def appVersionStr = "nflx-djangobase-enhanced-0.1-170cdbd.h12"
      def buildHost = "http://some-build-server:8080"
      def buildInfoUrl = "http://some-build-server:8080/repogroup/repo/builds/320282"
      def share_account = "000001, 000002"
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: fullyQualifiedPackageName,
                                        base_os: "trusty",
                                        build_host: buildHost,
                                        cloud_provider_type: BakeRequest.CloudProviderType.alicloud,
                                        extended_attributes: [share_with: share_account],
                                        build_info_url: buildInfoUrl)
      def osPackages = parseDebOsPackageNames(fullyQualifiedPackageName)
      def targetImageName = "kato-timestamp-trusty"
      def parameterMap = [
        alicloud_region: REGION,
        alicloud_ssh_username: "root",
        alicloud_instance_type: "ecs.c5.large",
        alicloud_source_image: SOURCE_TRUSTY_IMAGE_NAME,
        alicloud_target_image: targetImageName,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        repository: DEBIAN_REPOSITORY,
        packages: fullyQualifiedPackageName,
        share_with_1: "000001",
        share_with_2: "000002",
        configDir: configDir,
        appversion: appVersionStr,
        build_host: buildHost,
        build_info_url: buildInfoUrl
      ]

      @Subject
      AliCloudBakeHandler aliCloudBakeHandler = new AliCloudBakeHandler(configDir: configDir,
                                                         alicloudBakeryDefaults: alicloudBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY,
                                                         yumRepository: YUM_REPOSITORY)

    when:
      aliCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> appVersionStr
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> fullyQualifiedPackageName
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$alicloudBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters including copy_to multiple regions as extended_attribute'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def fullyQualifiedPackageName = "nflx-djangobase-enhanced_0.1-h12.170cdbd_all"
      def appVersionStr = "nflx-djangobase-enhanced-0.1-170cdbd.h12"
      def buildHost = "http://some-build-server:8080"
      def buildInfoUrl = "http://some-build-server:8080/repogroup/repo/builds/320282"
      def copy_regions = "cn-beijing, cn-shanghai"
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: fullyQualifiedPackageName,
                                        base_os: "trusty",
                                        build_host: buildHost,
                                        cloud_provider_type: BakeRequest.CloudProviderType.alicloud,
                                        extended_attributes: [copy_to: copy_regions],
                                        build_info_url: buildInfoUrl)
      def osPackages = parseDebOsPackageNames(fullyQualifiedPackageName)
      def targetImageName = "kato-timestamp-trusty"
      def parameterMap = [
        alicloud_region: REGION,
        alicloud_ssh_username: "root",
        alicloud_instance_type: "ecs.c5.large",
        alicloud_source_image: SOURCE_TRUSTY_IMAGE_NAME,
        alicloud_target_image: targetImageName,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        repository: DEBIAN_REPOSITORY,
        packages: fullyQualifiedPackageName,
        copy_to_1: "cn-beijing",
        copy_to_2: "cn-shanghai",
        configDir: configDir,
        appversion: appVersionStr,
        build_host: buildHost,
        build_info_url: buildInfoUrl
      ]

      @Subject
      AliCloudBakeHandler aliCloudBakeHandler = new AliCloudBakeHandler(configDir: configDir,
                                                         alicloudBakeryDefaults: alicloudBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY,
                                                         yumRepository: YUM_REPOSITORY)

    when:
      aliCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> appVersionStr
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> fullyQualifiedPackageName
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$alicloudBakeryDefaults.templateFile")
  }
}
