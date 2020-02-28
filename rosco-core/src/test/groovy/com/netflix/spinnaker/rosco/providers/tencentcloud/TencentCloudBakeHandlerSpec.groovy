/*
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.
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

package com.netflix.spinnaker.rosco.providers.tencentcloud

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.tencentcloud.config.RoscoTencentCloudConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class TencentCloudBakeHandlerSpec extends Specification implements TestDefaults {
    private static final String REGION = "ap-guangzhou"
    private static final String ZONE = "ap-guangzhou-3"
    private static final String INSTANCE_TYPE = "S3.SMALL1"
    private static final String SOURCE_IMAGE_ID = "img-pyqx34y1"
    private static final String SSH_USER_NAME_UBUNTU = "ubuntu"

    @Shared
    String configDir = "/some/path"

    @Shared
    RoscoTencentCloudConfiguration.TencentCloudBakeryDefaults tencentCloudBakeryDefaults

    void setupSpec() {
        def tencentCloudBakeryDefaultsJson = [
                templateFile: "tencentcloud_template.json",
                baseImages: [
                        [
                                baseImage: [
                                        id: "ubuntu-1604",
                                        packageType: "DEB",
                                ],
                                virtualizationSettings: [
                                        [
                                                region: REGION,
                                                zone: ZONE,
                                                instanceType: INSTANCE_TYPE,
                                                sourceImageId: SOURCE_IMAGE_ID,
                                                sshUserName: SSH_USER_NAME_UBUNTU
                                        ],

                                ]
                        ],
                ]
        ]

        tencentCloudBakeryDefaults = new ObjectMapper().convertValue(tencentCloudBakeryDefaultsJson, RoscoTencentCloudConfiguration.TencentCloudBakeryDefaults)
    }

    void 'can scrape packer logs for image name'() {
        setup:
        @Subject
        TencentCloudBakeHandler tencentCloudBakeHandler = new TencentCloudBakeHandler(tencentCloudBakeryDefaults: tencentCloudBakeryDefaults)

        when:
        def logsContent =
                "==> tencentcloud-cvm: Trying to create a new image: all-timestamp-ubuntu1604...\n" +
                        "tencentcloud-cvm: Waiting for image ready\n" +
                        "tencentcloud-cvm: Image created: img-gu2hy024\n" +
                        "==> tencentcloud-cvm: Cleaning up instance...\n" +
                        "==> tencentcloud-cvm: Cleaning up securitygroup...\n" +
                        "==> tencentcloud-cvm: Cleaning up subnet...\n" +
                        "==> tencentcloud-cvm: Cleaning up vpc...\n" +
                        "==> tencentcloud-cvm: Cleaning up keypair...\n" +
                        "Build 'tencentcloud-cvm' finished.\n" +
                        "\n" +
                        "==> Builds finished. The artifacts of successful builds are:\n" +
                        "--> tencentcloud-cvm: Tencentcloud images(ap-guangzhou: img-gu2hy024) were created."

        Bake bake = tencentCloudBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

        then:
        with (bake) {
            id == "123"
            ami == "img-gu2hy024"
            image_name == "all-timestamp-ubuntu1604"
        }
    }

    void 'scraping returns null for missing image id'() {
        setup:
        @Subject
        TencentCloudBakeHandler tencentCloudBakeHandler = new TencentCloudBakeHandler(tencentCloudBakeryDefaults: tencentCloudBakeryDefaults)

        when:
        def logsContent =
                "==> tencentcloud-cvm: Trying to create a new image: all-timestamp-ubuntu1604...\n" +
                        "tencentcloud-cvm: Waiting for image ready\n" +
                        "tencentcloud-cvm: Image created: img-gu2hy024\n" +
                        "==> tencentcloud-cvm: Cleaning up instance...\n" +
                        "==> tencentcloud-cvm: Cleaning up securitygroup...\n" +
                        "==> tencentcloud-cvm: Cleaning up subnet...\n" +
                        "==> tencentcloud-cvm: Cleaning up vpc...\n" +
                        "==> tencentcloud-cvm: Cleaning up keypair...\n" +
                        "Build 'tencentcloud-cvm' finished.\n"

        Bake bake = tencentCloudBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

        then:
        with (bake) {
            id == "123"
            !ami
            image_name == "all-timestamp-ubuntu1604"
        }
    }

    void 'scraping returns null for missing image name'() {
        setup:
        @Subject
        TencentCloudBakeHandler tencentCloudBakeHandler = new TencentCloudBakeHandler(tencentCloudBakeryDefaults: tencentCloudBakeryDefaults)

        when:
        def logsContent =
                "==> tencentcloud-cvm: Trying to check image name: packer-test...\n" +
                        "tencentcloud-cvm: Image name: useable"

        Bake bake = tencentCloudBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

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
                base_os: "ubuntu-1604",
                cloud_provider_type: BakeRequest.CloudProviderType.tencentcloud)
        def targetImageName = "kato-timestamp-ubuntu"
        def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
        def parameterMap = [
                tencentcloud_source_image_id: SOURCE_IMAGE_ID,
                tencentcloud_region: REGION,
                tencentcloud_zone: ZONE,
                tencentcloud_ssh_username: SSH_USER_NAME_UBUNTU,
                tencentcloud_instance_type: INSTANCE_TYPE,
                tencentcloud_target_image: targetImageName,
                repository: DEBIAN_REPOSITORY,
                package_type: DEB_PACKAGE_TYPE.util.packageType,
                packages: PACKAGES_NAME,
                configDir: configDir
        ]

        @Subject
        TencentCloudBakeHandler tencentCloudBakeHandler = new TencentCloudBakeHandler(configDir: configDir,
                tencentCloudBakeryDefaults: tencentCloudBakeryDefaults,
                imageNameFactory: imageNameFactoryMock,
                packerCommandFactory: packerCommandFactoryMock,
                debianRepository: DEBIAN_REPOSITORY)

        when:
        tencentCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

        then:
        1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
        1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
        1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
        1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$tencentCloudBakeryDefaults.templateFile")
    }

    void 'produces packer command with all required parameters for ubuntu, and overriding template filename'() {
        setup:
        def imageNameFactoryMock = Mock(ImageNameFactory)
        def packerCommandFactoryMock = Mock(PackerCommandFactory)
        def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                package_name: PACKAGES_NAME,
                base_os: "ubuntu-1604",
                cloud_provider_type: BakeRequest.CloudProviderType.tencentcloud,
                template_file_name: "somePackerTemplate.json")
        def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
        def targetImageName = "kato-timestamp-ubuntu"
        def parameterMap = [
                tencentcloud_source_image_id: SOURCE_IMAGE_ID,
                tencentcloud_region: REGION,
                tencentcloud_zone: ZONE,
                tencentcloud_ssh_username: SSH_USER_NAME_UBUNTU,
                tencentcloud_instance_type: INSTANCE_TYPE,
                tencentcloud_target_image: targetImageName,
                repository: DEBIAN_REPOSITORY,
                package_type: DEB_PACKAGE_TYPE.util.packageType,
                packages: PACKAGES_NAME,
                configDir: configDir
        ]

        @Subject
        TencentCloudBakeHandler tencentCloudBakeHandler = new TencentCloudBakeHandler(configDir: configDir,
                tencentCloudBakeryDefaults: tencentCloudBakeryDefaults,
                imageNameFactory: imageNameFactoryMock,
                packerCommandFactory: packerCommandFactoryMock,
                debianRepository: DEBIAN_REPOSITORY)

        when:
        tencentCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

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
                base_os: "ubuntu-1604",
                cloud_provider_type: BakeRequest.CloudProviderType.tencentcloud,
                extended_attributes: [someAttr1: "someValue1", someAttr2: "someValue2"])
        def targetImageName = "kato-timestamp-ubuntu"
        def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
        def parameterMap = [
                tencentcloud_source_image_id: SOURCE_IMAGE_ID,
                tencentcloud_region: REGION,
                tencentcloud_zone: ZONE,
                tencentcloud_ssh_username: SSH_USER_NAME_UBUNTU,
                tencentcloud_instance_type: INSTANCE_TYPE,
                tencentcloud_target_image: targetImageName,
                repository: DEBIAN_REPOSITORY,
                package_type: DEB_PACKAGE_TYPE.util.packageType,
                packages: PACKAGES_NAME,
                configDir: configDir,
                someAttr1: "someValue1",
                someAttr2: "someValue2"
        ]

        @Subject
        TencentCloudBakeHandler tencentCloudBakeHandler = new TencentCloudBakeHandler(configDir: configDir,
                tencentCloudBakeryDefaults: tencentCloudBakeryDefaults,
                imageNameFactory: imageNameFactoryMock,
                packerCommandFactory: packerCommandFactoryMock,
                debianRepository: DEBIAN_REPOSITORY)

        when:
        tencentCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

        then:
        1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
        1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
        1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
        1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$tencentCloudBakeryDefaults.templateFile")
    }

    void 'throws exception when virtualization settings are not found for specified operating system'() {
        setup:
        def imageNameFactoryMock = Mock(ImageNameFactory)
        def packerCommandFactoryMock = Mock(PackerCommandFactory)
        def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                package_name: PACKAGES_NAME,
                base_os: "centos",
                cloud_provider_type: BakeRequest.CloudProviderType.tencentcloud)

        @Subject
        TencentCloudBakeHandler tencentCloudBakeHandler = new TencentCloudBakeHandler(tencentCloudBakeryDefaults: tencentCloudBakeryDefaults,
                imageNameFactory: imageNameFactoryMock,
                packerCommandFactory: packerCommandFactoryMock,
                debianRepository: DEBIAN_REPOSITORY)

        when:
        tencentCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

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
                base_os: "ubuntu-1604",
                cloud_provider_type: BakeRequest.CloudProviderType.tencentcloud)

        @Subject
        TencentCloudBakeHandler tencentCloudBakeHandler = new TencentCloudBakeHandler(tencentCloudBakeryDefaults: tencentCloudBakeryDefaults,
                imageNameFactory: imageNameFactoryMock,
                packerCommandFactory: packerCommandFactoryMock,
                debianRepository: DEBIAN_REPOSITORY)

        when:
        tencentCloudBakeHandler.produceBakeRecipe("ap-beijing", bakeRequest)

        then:
        IllegalArgumentException e = thrown()
        e.message == "No virtualization settings found for region 'ap-beijing', operating system 'ubuntu-1604'"
    }

    void 'produce a default Tencents Cloud bakeKey with image name'() {
        setup:
        def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                package_name: PACKAGES_NAME,
                base_os: "ubuntu1604",
                cloud_provider_type: BakeRequest.CloudProviderType.tencentcloud,
                ami_name: "kato-app")
        @Subject
        TencentCloudBakeHandler tencentCloudBakeHandler = new TencentCloudBakeHandler(tencentCloudBakeryDefaults: tencentCloudBakeryDefaults)

        when:
        String bakeKey = tencentCloudBakeHandler.produceBakeKey(REGION, bakeRequest)

        then:
        bakeKey == "bake:tencentcloud:ubuntu1604:kato-app:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:ap-guangzhou"
    }

    void 'do not consider ami suffix when composing bake key'() {
        setup:
        def bakeRequest1 = new BakeRequest(user: "someuser@gmail.com",
                package_name: PACKAGES_NAME,
                base_os: "ubuntu1604",
                cloud_provider_type: BakeRequest.CloudProviderType.tencentcloud,
                ami_suffix: "1.0")
        def bakeRequest2 = new BakeRequest(user: "someuser@gmail.com",
                package_name: PACKAGES_NAME,
                base_os: "ubuntu1604",
                cloud_provider_type: BakeRequest.CloudProviderType.tencentcloud,
                ami_suffix: "2.0")
        @Subject
        TencentCloudBakeHandler tencentCloudBakeHandler = new TencentCloudBakeHandler(tencentCloudBakeryDefaults: tencentCloudBakeryDefaults)

        when:
        String bakeKey1 = tencentCloudBakeHandler.produceBakeKey(REGION, bakeRequest1)
        String bakeKey2 = tencentCloudBakeHandler.produceBakeKey(REGION, bakeRequest2)

        then:
        bakeKey1 == "bake:tencentcloud:ubuntu1604:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:ap-guangzhou"
        bakeKey2 == bakeKey1
    }

    void 'produces packer command with all required parameters including shared_with multiple accounts as extended_attribute'() {
        setup:
        def imageNameFactoryMock = Mock(ImageNameFactory)
        def packerCommandFactoryMock = Mock(PackerCommandFactory)
        def fullyQualifiedPackageName = "nflx-djangobase-enhanced_0.1-h12.170cdbd_all"
        def appVersionStr = "nflx-djangobase-enhanced-0.1-170cdbd.h12"
        def buildHost = "http://some-build-server:8080"
        def share_account = "000001, 000002"
        def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                package_name: fullyQualifiedPackageName,
                base_os: "ubuntu-1604",
                build_host: buildHost,
                cloud_provider_type: BakeRequest.CloudProviderType.tencentcloud,
                extended_attributes: [share_with: share_account])
        def osPackages = parseDebOsPackageNames(fullyQualifiedPackageName)
        def targetImageName = "kato-timestamp-trusty"
        def parameterMap = [
                tencentcloud_source_image_id: SOURCE_IMAGE_ID,
                tencentcloud_region: REGION,
                tencentcloud_zone: ZONE,
                tencentcloud_ssh_username: SSH_USER_NAME_UBUNTU,
                tencentcloud_instance_type: INSTANCE_TYPE,
                tencentcloud_target_image: targetImageName,
                package_type: DEB_PACKAGE_TYPE.util.packageType,
                repository: DEBIAN_REPOSITORY,
                packages: fullyQualifiedPackageName,
                share_with_1: "000001",
                share_with_2: "000002",
                configDir: configDir,
                appversion: appVersionStr,
                build_host: buildHost,
        ]

        @Subject
        TencentCloudBakeHandler tencentCloudBakeHandler = new TencentCloudBakeHandler(configDir: configDir,
                tencentCloudBakeryDefaults: tencentCloudBakeryDefaults,
                imageNameFactory: imageNameFactoryMock,
                packerCommandFactory: packerCommandFactoryMock,
                debianRepository: DEBIAN_REPOSITORY,
                yumRepository: YUM_REPOSITORY)

        when:
        tencentCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

        then:
        1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
        1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> appVersionStr
        1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> fullyQualifiedPackageName
        1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$tencentCloudBakeryDefaults.templateFile")
    }

    void 'produces packer command with all required parameters including copy_to multiple regions as extended_attribute'() {
        setup:
        def imageNameFactoryMock = Mock(ImageNameFactory)
        def packerCommandFactoryMock = Mock(PackerCommandFactory)
        def fullyQualifiedPackageName = "nflx-djangobase-enhanced_0.1-h12.170cdbd_all"
        def appVersionStr = "nflx-djangobase-enhanced-0.1-170cdbd.h12"
        def buildHost = "http://some-build-server:8080"
        def copy_regions = "ap-guangzhou, ap-shanghai"
        def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                package_name: fullyQualifiedPackageName,
                base_os: "ubuntu-1604",
                build_host: buildHost,
                cloud_provider_type: BakeRequest.CloudProviderType.tencentcloud,
                extended_attributes: [copy_to: copy_regions])
        def osPackages = parseDebOsPackageNames(fullyQualifiedPackageName)
        def targetImageName = "kato-timestamp-trusty"
        def parameterMap = [
                tencentcloud_source_image_id: SOURCE_IMAGE_ID,
                tencentcloud_region: REGION,
                tencentcloud_zone: ZONE,
                tencentcloud_ssh_username: SSH_USER_NAME_UBUNTU,
                tencentcloud_instance_type: INSTANCE_TYPE,
                tencentcloud_target_image: targetImageName,
                package_type: DEB_PACKAGE_TYPE.util.packageType,
                repository: DEBIAN_REPOSITORY,
                packages: fullyQualifiedPackageName,
                copy_to_1: "ap-guangzhou",
                copy_to_2: "ap-shanghai",
                configDir: configDir,
                appversion: appVersionStr,
                build_host: buildHost,
        ]

        @Subject
        TencentCloudBakeHandler tencentCloudBakeHandler = new TencentCloudBakeHandler(configDir: configDir,
                tencentCloudBakeryDefaults: tencentCloudBakeryDefaults,
                imageNameFactory: imageNameFactoryMock,
                packerCommandFactory: packerCommandFactoryMock,
                debianRepository: DEBIAN_REPOSITORY,
                yumRepository: YUM_REPOSITORY)

        when:
        tencentCloudBakeHandler.produceBakeRecipe(REGION, bakeRequest)

        then:
        1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
        1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> appVersionStr
        1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> fullyQualifiedPackageName
        1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$tencentCloudBakeryDefaults.templateFile")
    }
}
