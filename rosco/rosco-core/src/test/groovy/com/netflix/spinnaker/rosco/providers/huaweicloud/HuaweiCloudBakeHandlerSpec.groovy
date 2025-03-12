/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
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

package com.netflix.spinnaker.rosco.providers.huaweicloud

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.config.RoscoConfiguration
import com.netflix.spinnaker.rosco.providers.huaweicloud.HuaweiCloudBakeHandler
import com.netflix.spinnaker.rosco.providers.huaweicloud.config.RoscoHuaweiCloudConfiguration
import com.netflix.spinnaker.rosco.providers.huaweicloud.config.RoscoHuaweiCloudConfiguration.HuaweiCloudBakeryDefaults
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class HuaweiCloudBakeHandlerSpec extends Specification implements TestDefaults {

  private static final String REGION = "cn-north-1"
  private static final String SOURCE_UBUNTU_IMAGE_ID = "65da34a6-c829-43f7-a70e-f11fa26816dc"
  private static final String SOURCE_TRUSTY_IMAGE_ID = "76da34a6-c840-43f7-a79e-f11fa26817bb"

  @Shared
  String configDir = "/some/path"

  @Shared
  HuaweiCloudBakeryDefaults huaweicloudBakeryDefaults

  void setupSpec() {
    def huaweicloudBakeryDefaultsJson = [
      templateFile: "huaweicloud_template.json",
      baseImages: [
        [
          baseImage: [
            id: "ubuntu",
            packageType: "DEB",
          ],
          virtualizationSettings: [
            [
              region: REGION,
              eipType: "5_bgp",
              sshUserName: "root",
              instanceType: "s3.medium.2",
              sourceImageId: SOURCE_UBUNTU_IMAGE_ID,
            ],
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
              eipType: "5_bgp",
              sshUserName: "root",
              instanceType: "c3.large.4",
              sourceImageId: SOURCE_TRUSTY_IMAGE_ID,
            ]
          ]
        ]
      ]
    ]

    huaweicloudBakeryDefaults = new ObjectMapper().convertValue(huaweicloudBakeryDefaultsJson, HuaweiCloudBakeryDefaults)
  }

  void 'can scrape packer logs for image name'() {
    setup:
      @Subject
      HuaweiCloudBakeHandler huaweiCloudBakeHandler = new HuaweiCloudBakeHandler(huaweicloudBakeryDefaults: huaweicloudBakeryDefaults)

    when:
      def logsContent =
        "==> huaweicloud-ecs: Creating the image: packer-test-abc\n" +
        "    huaweicloud-ecs: Image: 77da34a6-c829-43f7-a79e-f11fa26816cc\n" +
        "==> huaweicloud-ecs: Waiting for image packer-test-abc (image id: 77da34a6-c829-43f7-a79e-f11fa26816cc) to become ready...\n" +
        "==> huaweicloud-ecs: Deleted temporary floating IP '9fa8dc49-8e08-4feb-9429-8bb37a5e3bd2' (117.78.52.160)\n" +
        "==> huaweicloud-ecs: Terminating the source server: e67273c0-15df-419b-84d2-f77fcaaa582f ...\n" +
        "==> huaweicloud-ecs: Deleting volume: c7af2d39-75c5-4d13-ae55-8c69c61268ce ...\n" +
        "==> huaweicloud-ecs: Deleting temporary keypair: packer_5dcbb7ee-13e2-9c75-6f32-cd6efae284f1 ...\n" +
        "Build 'huaweicloud-ecs' finished.\n" +
        "\n" +
        "==> Builds finished. The artifacts of successful builds are:\n" +
        "--> huaweicloud-ecs: An image was created: 77da34a6-c829-43f7-a79e-f11fa26816cc"

      Bake bake = huaweiCloudBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

    then:
      with (bake) {
        id == "123"
        ami == "77da34a6-c829-43f7-a79e-f11fa26816cc"
        image_name == "packer-test-abc"
      }
  }

  void 'scraping returns null for missing image id'() {
    setup:
      @Subject
      HuaweiCloudBakeHandler huaweiCloudBakeHandler = new HuaweiCloudBakeHandler(huaweicloudBakeryDefaults: huaweicloudBakeryDefaults)

    when:
      def logsContent =
        "==> huaweicloud-ecs: Creating the image: packer-test-abc\n" +
        "    huaweicloud-ecs: Image: 77da34a6-c829-43f7-a79e-f11fa26816cc\n" +
        "==> huaweicloud-ecs: Waiting for image packer-test-abc (image id: 77da34a6-c829-43f7-a79e-f11fa26816cc) to become ready...\n" +
        "==> huaweicloud-ecs: Deleted temporary floating IP '9fa8dc49-8e08-4feb-9429-8bb37a5e3bd2' (117.78.52.160)\n" +
        "==> huaweicloud-ecs: Terminating the source server: e67273c0-15df-419b-84d2-f77fcaaa582f ...\n" +
        "==> huaweicloud-ecs: Deleting volume: c7af2d39-75c5-4d13-ae55-8c69c61268ce ...\n" +
        "==> huaweicloud-ecs: Deleting temporary keypair: packer_5dcbb7ee-13e2-9c75-6f32-cd6efae284f1 ...\n" +
        "Build 'huaweicloud-ecs' finished.\n" +
        "\n" +
        "==> Builds finished. The artifacts of successful builds are:\n"

      Bake bake = huaweiCloudBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

    then:
      with (bake) {
        id == "123"
        !ami
        image_name == "packer-test-abc"
      }
  }

  void 'scraping returns null for missing image name'() {
    setup:
      @Subject
      HuaweiCloudBakeHandler huaweiCloudBakeHandler = new HuaweiCloudBakeHandler(huaweicloudBakeryDefaults: huaweicloudBakeryDefaults)

    when:
      def logsContent =
        "==> huaweicloud-ecs: Loading available zones\n" +
        "    huaweicloud-ecs: Available zones: cn-north-1a cn-north-1b cn-north-1c\n"

    Bake bake = huaweiCloudBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

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
                                        cloud_provider_type: BakeRequest.CloudProviderType.huaweicloud)
      def targetImageName = "kato-timestamp-ubuntu"
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def parameterMap = [
        huaweicloud_region: REGION,
        huaweicloud_eip_type: "5_bgp",
        huaweicloud_ssh_username: "root",
        huaweicloud_instance_type: "s3.medium.2",
        huaweicloud_source_image_id: SOURCE_UBUNTU_IMAGE_ID,
        huaweicloud_image_name: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      HuaweiCloudBakeHandler huaweiCloudBakeHandler = new HuaweiCloudBakeHandler(configDir: configDir,
                                                         huaweicloudBakeryDefaults: huaweicloudBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
    huaweiCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$huaweicloudBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for ubuntu, and overriding base ami'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        base_ami: SOURCE_UBUNTU_IMAGE_ID,
                                        cloud_provider_type: BakeRequest.CloudProviderType.huaweicloud)
      def targetImageName = "kato-timestamp-ubuntu"
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def parameterMap = [
        huaweicloud_region: REGION,
        huaweicloud_eip_type: "5_bgp",
        huaweicloud_ssh_username: "root",
        huaweicloud_instance_type: "s3.medium.2",
        huaweicloud_source_image_id: SOURCE_UBUNTU_IMAGE_ID,
        huaweicloud_image_name: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      HuaweiCloudBakeHandler huaweiCloudBakeHandler = new HuaweiCloudBakeHandler(configDir: configDir,
                                                         huaweicloudBakeryDefaults: huaweicloudBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
    huaweiCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$huaweicloudBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for ubuntu, and overriding template filename'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.huaweicloud,
                                        template_file_name: "somePackerTemplate.json")
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def targetImageName = "kato-timestamp-ubuntu"
      def parameterMap = [
        huaweicloud_region: REGION,
        huaweicloud_eip_type: "5_bgp",
        huaweicloud_ssh_username: "root",
        huaweicloud_instance_type: "s3.medium.2",
        huaweicloud_source_image_id: SOURCE_UBUNTU_IMAGE_ID,
        huaweicloud_image_name: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      HuaweiCloudBakeHandler huaweiCloudBakeHandler = new HuaweiCloudBakeHandler(configDir: configDir,
                                                         huaweicloudBakeryDefaults: huaweicloudBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      huaweiCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

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
                                        cloud_provider_type: BakeRequest.CloudProviderType.huaweicloud,
                                        extended_attributes: [someAttr1: "someValue1", someAttr2: "someValue2"])
      def targetImageName = "kato-timestamp-ubuntu"
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def parameterMap = [
        huaweicloud_region: REGION,
        huaweicloud_eip_type: "5_bgp",
        huaweicloud_ssh_username: "root",
        huaweicloud_instance_type: "s3.medium.2",
        huaweicloud_source_image_id: SOURCE_UBUNTU_IMAGE_ID,
        huaweicloud_image_name: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        someAttr1: "someValue1",
        someAttr2: "someValue2"
      ]

      @Subject
      HuaweiCloudBakeHandler huaweiCloudBakeHandler = new HuaweiCloudBakeHandler(configDir: configDir,
                                                         huaweicloudBakeryDefaults: huaweicloudBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      huaweiCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$huaweicloudBakeryDefaults.templateFile")
  }

  void 'throws exception when virtualization settings are not found for specified operating system'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        cloud_provider_type: BakeRequest.CloudProviderType.huaweicloud)

      @Subject
      HuaweiCloudBakeHandler huaweiCloudBakeHandler = new HuaweiCloudBakeHandler(huaweicloudBakeryDefaults: huaweicloudBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      huaweiCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

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
                                        cloud_provider_type: BakeRequest.CloudProviderType.huaweicloud)

      @Subject
      HuaweiCloudBakeHandler huaweiCloudBakeHandler = new HuaweiCloudBakeHandler(huaweicloudBakeryDefaults: huaweicloudBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      huaweiCloudBakeHandler.produceBakeRecipe("cn-north-4", bakeRequest)

    then:
      IllegalArgumentException e = thrown()
      e.message == "No virtualization settings found for region 'cn-north-4', operating system 'trusty'."
  }

  void 'produce a default Huawei Cloud bake key with image name'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        cloud_provider_type: BakeRequest.CloudProviderType.huaweicloud,
                                        ami_name: "kato-app")
      @Subject
      HuaweiCloudBakeHandler huaweiCloudBakeHandler = new HuaweiCloudBakeHandler(huaweicloudBakeryDefaults: huaweicloudBakeryDefaults)

    when:
      String bakeKey = huaweiCloudBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:huaweicloud:centos:kato-app:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:cn-north-1"
  }

  void 'do not consider ami suffix when composing bake key'() {
    setup:
      def bakeRequest1 = new BakeRequest(user: "someuser@gmail.com",
                                         package_name: PACKAGES_NAME,
                                         base_os: "centos",
                                         cloud_provider_type: BakeRequest.CloudProviderType.huaweicloud,
                                         ami_suffix: "1.0")
      def bakeRequest2 = new BakeRequest(user: "someuser@gmail.com",
                                         package_name: PACKAGES_NAME,
                                         base_os: "centos",
                                         cloud_provider_type: BakeRequest.CloudProviderType.huaweicloud,
                                         ami_suffix: "2.0")
      @Subject
      HuaweiCloudBakeHandler huaweiCloudBakeHandler = new HuaweiCloudBakeHandler(huaweicloudBakeryDefaults: huaweicloudBakeryDefaults)

    when:
      String bakeKey1 = huaweiCloudBakeHandler.produceBakeKey(REGION, bakeRequest1)
      String bakeKey2 = huaweiCloudBakeHandler.produceBakeKey(REGION, bakeRequest2)

    then:
      bakeKey1 == "bake:huaweicloud:centos:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:cn-north-1"
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
                                        cloud_provider_type: BakeRequest.CloudProviderType.huaweicloud,
                                        extended_attributes: [share_with: share_account],
                                        build_info_url: buildInfoUrl)
      def osPackages = parseDebOsPackageNames(fullyQualifiedPackageName)
      def targetImageName = "kato-timestamp-trusty"
      def parameterMap = [
        huaweicloud_region: REGION,
        huaweicloud_eip_type: "5_bgp",
        huaweicloud_ssh_username: "root",
        huaweicloud_instance_type: "c3.large.4",
        huaweicloud_source_image_id: SOURCE_TRUSTY_IMAGE_ID,
        huaweicloud_image_name: targetImageName,
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
      HuaweiCloudBakeHandler huaweiCloudBakeHandler = new HuaweiCloudBakeHandler(configDir: configDir,
                                                         huaweicloudBakeryDefaults: huaweicloudBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY,
                                                         yumRepository: YUM_REPOSITORY)

    when:
      huaweiCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> appVersionStr
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> fullyQualifiedPackageName
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$huaweicloudBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters including copy_to multiple regions as extended_attribute'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def fullyQualifiedPackageName = "nflx-djangobase-enhanced_0.1-h12.170cdbd_all"
      def appVersionStr = "nflx-djangobase-enhanced-0.1-170cdbd.h12"
      def buildHost = "http://some-build-server:8080"
      def buildInfoUrl = "http://some-build-server:8080/repogroup/repo/builds/320282"
      def copy_regions = "cn-north-2, cn-north-3"
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: fullyQualifiedPackageName,
                                        base_os: "trusty",
                                        build_host: buildHost,
                                        cloud_provider_type: BakeRequest.CloudProviderType.huaweicloud,
                                        extended_attributes: [copy_to: copy_regions],
                                        build_info_url: buildInfoUrl)
      def osPackages = parseDebOsPackageNames(fullyQualifiedPackageName)
      def targetImageName = "kato-timestamp-trusty"
      def parameterMap = [
        huaweicloud_region: REGION,
        huaweicloud_eip_type: "5_bgp",
        huaweicloud_ssh_username: "root",
        huaweicloud_instance_type: "c3.large.4",
        huaweicloud_source_image_id: SOURCE_TRUSTY_IMAGE_ID,
        huaweicloud_image_name: targetImageName,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        repository: DEBIAN_REPOSITORY,
        packages: fullyQualifiedPackageName,
        copy_to_1: "cn-north-2",
        copy_to_2: "cn-north-3",
        configDir: configDir,
        appversion: appVersionStr,
        build_host: buildHost,
        build_info_url: buildInfoUrl
      ]

      @Subject
      HuaweiCloudBakeHandler huaweiCloudBakeHandler = new HuaweiCloudBakeHandler(configDir: configDir,
                                                         huaweicloudBakeryDefaults: huaweicloudBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY,
                                                         yumRepository: YUM_REPOSITORY)

    when:
      huaweiCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> appVersionStr
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> fullyQualifiedPackageName
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$huaweicloudBakeryDefaults.templateFile")
  }
}
