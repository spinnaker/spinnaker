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

package com.netflix.spinnaker.rosco.providers.docker

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.docker.config.RoscoDockerConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackageNameConverter
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class DockerBakeHandlerSpec extends Specification implements TestDefaults {

  private static final String REGION = "global"
  private static final String TARGET_REPOSITORY = "my-docker-repository:5000"
  private static final String TEMPLATE_FILE = "docker_template.json"
  private static final String SOURCE_UBUNTU_IMAGE_NAME = "ubuntu:precise"
  private static final String SOURCE_TRUSTY_IMAGE_NAME = "ubuntu:trusty"
  private static final String SOURCE_CENTOS_HVM_IMAGE_NAME = "centos:6"

  @Shared
  String configDir = "/some/path"

  @Shared
  RoscoDockerConfiguration.DockerBakeryDefaults dockerBakeryDefaults

  void setupSpec() {
    def dockerBakeryDefaultsJson = [
      targetRepository: TARGET_REPOSITORY,
      templateFile: TEMPLATE_FILE,
      baseImages: [
        [
          baseImage: [
            id: "ubuntu",
            packageType: "DEB",
          ],
          virtualizationSettings: [sourceImage: SOURCE_UBUNTU_IMAGE_NAME]
        ],
        [
          baseImage: [
            id: "trusty",
            packageType: "DEB",
          ],
          virtualizationSettings: [sourceImage: SOURCE_TRUSTY_IMAGE_NAME]
        ],
        [
          baseImage: [
            id: "centos",
            packageType: "RPM",
          ],
          virtualizationSettings: [sourceImage: SOURCE_CENTOS_HVM_IMAGE_NAME]
        ]
      ]
    ]

    dockerBakeryDefaults = new ObjectMapper().convertValue(dockerBakeryDefaultsJson, RoscoDockerConfiguration.DockerBakeryDefaults)
  }

  void 'can scrape packer logs for image name'() {
    setup:
      @Subject
      DockerBakeHandler dockerBakeHandler = new DockerBakeHandler(dockerBakeryDefaults: dockerBakeryDefaults)

    when:


      def logsContent =
        " ==> docker: Exporting the container\n" +
        " ==> docker: Killing the container: 2680047ff61983593134c30dfa0e25e25829afaff7f973a147a8d88a36847d20\n" +
        " ==> docker: Running post-processor: docker-import\n" +
        "     docker (docker-import): Importing image: Container\n" +
        "     docker (docker-import): Repository: mjd-docker-instance-2:5000/kato-all-1428964025241-trusty:latest\n" +
        "     docker (docker-import): Imported ID: 9d73c7917e47a2a815bc0bb82c36fddaaf3183a81c3774250e70514040a0e74c\n" +
        "     ==> docker: Running post-processor: docker-push\n" +
        "     docker (docker-push): Pushing: mjd-docker-instance-2:5000/kato-all-1428964025241-trusty\n" +
        "     docker (docker-push): The push refers to a repository [mjd-docker-instance-2:5000/kato-all-1428964025241-trusty] (len: 1)\n" +
        "     docker (docker-push): Sending image list\n" +
        "     docker (docker-push): Pushing repository mjd-docker-instance-2:5000/kato-all-1428964025241-trusty (1 tags)\n" +
        "     docker (docker-push): Pushing tag for rev [9d73c7917e47] on {http://mjd-docker-instance-2:5000/v1/repositories/kato-all-1428964025241-trusty/tags/latest}\n" +
        "     Build 'docker' finished.\n" +
        "\n" +
        "     ==> Builds finished. The artifacts of successful builds are:"

      Bake bake = dockerBakeHandler.scrapeCompletedBakeResults("", "123", logsContent)

    then:
      with (bake) {
        id == "123"
        !ami
        image_name == "mjd-docker-instance-2:5000/kato-all-1428964025241-trusty:latest"
      }
  }

  void 'scraping returns null for missing image name'() {
    setup:
      @Subject
      DockerBakeHandler dockerBakeHandler = new DockerBakeHandler(dockerBakeryDefaults: dockerBakeryDefaults)

    when:
      def logsContent =
        " ==> docker: Exporting the container\n" +
        " ==> docker: Killing the container: 2680047ff61983593134c30dfa0e25e25829afaff7f973a147a8d88a36847d20\n" +
        " ==> docker: Running post-processor: docker-import\n" +
        "     docker (docker-import): Importing image: Container\n" +
        "     docker (docker-import): Imported ID: 9d73c7917e47a2a815bc0bb82c36fddaaf3183a81c3774250e70514040a0e74c\n" +
        "     ==> docker: Running post-processor: docker-push\n" +
        "     docker (docker-push): Pushing: mjd-docker-instance-2:5000/kato-all-1428964025241-trusty\n" +
        "     docker (docker-push): The push refers to a repository [mjd-docker-instance-2:5000/kato-all-1428964025241-trusty] (len: 1)\n" +
        "     docker (docker-push): Sending image list\n" +
        "     docker (docker-push): Pushing repository mjd-docker-instance-2:5000/kato-all-1428964025241-trusty (1 tags)\n" +
        "     docker (docker-push): Pushing tag for rev [9d73c7917e47] on {http://mjd-docker-instance-2:5000/v1/repositories/kato-all-1428964025241-trusty/tags/latest}\n" +
        "     Build 'docker' finished.\n" +
        "\n" +
        "     ==> Builds finished. The artifacts of successful builds are:"

    Bake bake = dockerBakeHandler.scrapeCompletedBakeResults("", "123", logsContent)

    then:
      with (bake) {
        id == "123"
        !ami
        !image_name
      }
  }

  void 'produces packer command with all required parameters for ubuntu'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.docker)
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def osPackages = parseDebOsPackageNames(bakeRequest.package_name)
      def parameterMap = [
        docker_source_image: SOURCE_UBUNTU_IMAGE_NAME,
        docker_target_image: targetImageName,
        docker_target_repository: TARGET_REPOSITORY,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      DockerBakeHandler dockerBakeHandler = new DockerBakeHandler(configDir: configDir,
                                                                  dockerBakeryDefaults: dockerBakeryDefaults,
                                                                  imageNameFactory: imageNameFactoryMock,
                                                                  packerCommandFactory: packerCommandFactoryMock,
                                                                  debianRepository: DEBIAN_REPOSITORY)

    when:
      dockerBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$dockerBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for trusty'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "trusty",
                                        cloud_provider_type: BakeRequest.CloudProviderType.docker)
      def targetImageName = "kato-x8664-timestamp-trusty"
      def osPackages = parseDebOsPackageNames(bakeRequest.package_name)
      def parameterMap = [
        docker_source_image: SOURCE_TRUSTY_IMAGE_NAME,
        docker_target_image: targetImageName,
        docker_target_repository: TARGET_REPOSITORY,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      DockerBakeHandler dockerBakeHandler = new DockerBakeHandler(configDir: configDir,
                                                                  dockerBakeryDefaults: dockerBakeryDefaults,
                                                                  imageNameFactory: imageNameFactoryMock,
                                                                  packerCommandFactory: packerCommandFactoryMock,
                                                                  debianRepository: DEBIAN_REPOSITORY)

    when:
      dockerBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$dockerBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for centos'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        cloud_provider_type: BakeRequest.CloudProviderType.docker)
      def targetImageName = "kato-x8664-timestamp-centos"
      def osPackages = parseRpmOsPackageNames(bakeRequest.package_name)
      def parameterMap = [
        docker_source_image: SOURCE_CENTOS_HVM_IMAGE_NAME,
        docker_target_image: targetImageName,
        docker_target_repository: TARGET_REPOSITORY,
        repository: YUM_REPOSITORY,
        package_type: RPM_PACKAGE_TYPE.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      DockerBakeHandler dockerBakeHandler = new DockerBakeHandler(configDir: configDir,
                                                                  dockerBakeryDefaults: dockerBakeryDefaults,
                                                                  imageNameFactory: imageNameFactoryMock,
                                                                  packerCommandFactory: packerCommandFactoryMock,
                                                                  yumRepository: YUM_REPOSITORY)

    when:
      dockerBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(RPM_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$dockerBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters including appversion and build_host for trusty'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def fullyQualifiedPackageName = "nflx-djangobase-enhanced_0.1-h12.170cdbd_all"
      def appVersionStr = "nflx-djangobase-enhanced-0.1-170cdbd.h12"
      def buildHost = "http://some-build-server:8080"
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: fullyQualifiedPackageName,
                                        base_os: "trusty",
                                        build_host: buildHost,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def osPackage = PackageNameConverter.parseDebPackageName(bakeRequest.package_name)
      def targetImageName = "kato-x8664-timestamp-trusty"
      def parameterMap = [
        docker_source_image: SOURCE_TRUSTY_IMAGE_NAME,
        docker_target_image: targetImageName,
        docker_target_repository: TARGET_REPOSITORY,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.packageType,
        packages: fullyQualifiedPackageName,
        configDir: configDir,
        appversion: appVersionStr,
        build_host: buildHost
      ]

      @Subject
      DockerBakeHandler dockerBakeHandler = new DockerBakeHandler(configDir: configDir,
                                                                  dockerBakeryDefaults: dockerBakeryDefaults,
                                                                  imageNameFactory: imageNameFactoryMock,
                                                                  packerCommandFactory: packerCommandFactoryMock,
                                                                  debianRepository: DEBIAN_REPOSITORY)

    when:
      dockerBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, [osPackage]) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, [osPackage]) >> appVersionStr
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, [osPackage]) >> fullyQualifiedPackageName
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$dockerBakeryDefaults.templateFile")
  }

  void 'throws exception when virtualization settings are not found for specified operating system'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "wily",
                                        cloud_provider_type: BakeRequest.CloudProviderType.docker)

      @Subject
      DockerBakeHandler dockerBakeHandler = new DockerBakeHandler(dockerBakeryDefaults: dockerBakeryDefaults,
                                                                  imageNameFactory: imageNameFactoryMock,
                                                                  packerCommandFactory: packerCommandFactoryMock,
                                                                  debianRepository: DEBIAN_REPOSITORY)

    when:
      dockerBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      IllegalArgumentException e = thrown()
      e.message == "No virtualization settings found for 'wily'."
  }

}
