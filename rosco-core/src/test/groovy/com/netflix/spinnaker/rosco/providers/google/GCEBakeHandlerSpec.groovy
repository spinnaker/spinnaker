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

package com.netflix.spinnaker.rosco.providers.google

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import com.netflix.spinnaker.rosco.providers.google.config.RoscoGoogleConfiguration
import com.netflix.spinnaker.rosco.providers.google.config.RoscoGoogleConfiguration.GCEBakeryDefaults
import spock.lang.Shared
import spock.lang.Subject
import spock.lang.Specification

class GCEBakeHandlerSpec extends Specification {

  private static final String PACKAGE_NAME = "kato"
  private static final String REGION = "us-central1"
  private static final String SOURCE_UBUNTU_IMAGE_NAME = "some-ubuntu-image"
  private static final String SOURCE_TRUSTY_IMAGE_NAME = "some-trusty-image"
  private static final String DEBIAN_REPOSITORY = "http://some-debian-repository"

  @Shared
  GCEBakeryDefaults gceBakeryDefaults

  void setupSpec() {
    def gceBakeryDefaultsJson = [
      project: "some-gcp-project",
      zone: "us-central1-a",
      templateFile: "gce_template.json",
      operatingSystemVirtualizationSettings: [
        [
          os: "ubuntu",
          virtualizationSettings: [sourceImage: SOURCE_UBUNTU_IMAGE_NAME]
        ],
        [
          os: "trusty",
          virtualizationSettings: [sourceImage: SOURCE_TRUSTY_IMAGE_NAME]
        ]
      ]
    ]

    gceBakeryDefaults = new ObjectMapper().convertValue(gceBakeryDefaultsJson, RoscoGoogleConfiguration.GCEBakeryDefaults)
  }

  void 'can scrape packer logs for image name'() {
    setup:
      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(gceBakeryDefaults: gceBakeryDefaults)

    when:
      def logsContent =
        "    googlecompute: Running hooks in /etc/ca-certificates/update.d....\n" +
        "    googlecompute: done.\n" +
        "    googlecompute: done.\n" +
        "==> googlecompute: Deleting instance...\n" +
        "    googlecompute: Instance has been deleted!\n" +
        "==> googlecompute: Creating image...\n" +
        "==> googlecompute: Deleting disk...\n" +
        "    googlecompute: Disk has been deleted!\n" +
        "Build 'googlecompute' finished.\n" +
        "\n" +
        "==> Builds finished. The artifacts of successful builds are:\n" +
        "--> googlecompute: A disk image was created: kato-x12345678-trusty"

      Bake bake = gceBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

    then:
      with (bake) {
        id == "123"
        !ami
        image_name == "kato-x12345678-trusty"
      }
  }

  void 'scraping returns null for missing image name'() {
    setup:
      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(gceBakeryDefaults: gceBakeryDefaults)

    when:
      def logsContent =
        "    googlecompute: Running hooks in /etc/ca-certificates/update.d....\n" +
        "    googlecompute: done.\n" +
        "    googlecompute: done.\n" +
        "==> googlecompute: Deleting instance...\n" +
        "    googlecompute: Instance has been deleted!\n" +
        "==> googlecompute: Creating image...\n" +
        "==> googlecompute: Deleting disk...\n" +
        "    googlecompute: Disk has been deleted!\n" +
        "Build 'googlecompute' finished.\n" +
        "\n" +
        "==> Builds finished. The artifacts of successful builds are:"

      Bake bake = gceBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

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
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.ubuntu,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def parameterMap = [
        gce_project_id: gceBakeryDefaults.project,
        gce_zone: gceBakeryDefaults.zone,
        gce_source_image: SOURCE_UBUNTU_IMAGE_NAME,
        gce_target_image: targetImageName,
        deb_repo: DEBIAN_REPOSITORY,
        packages: PACKAGE_NAME
      ]

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(gceBakeryDefaults: gceBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest) >> [targetImageName, null, PACKAGE_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, gceBakeryDefaults.templateFile)
  }

  void 'produces packer command with all required parameters for trusty'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.trusty,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def targetImageName = "kato-x8664-timestamp-trusty"
      def parameterMap = [
        gce_project_id: gceBakeryDefaults.project,
        gce_zone: gceBakeryDefaults.zone,
        gce_source_image: SOURCE_TRUSTY_IMAGE_NAME,
        gce_target_image: targetImageName,
        deb_repo: DEBIAN_REPOSITORY,
        packages: PACKAGE_NAME
      ]

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(gceBakeryDefaults: gceBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest) >> [targetImageName, null, PACKAGE_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, gceBakeryDefaults.templateFile)
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
                                        base_os: BakeRequest.OperatingSystem.trusty,
                                        build_host: buildHost,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def targetImageName = "kato-x8664-timestamp-trusty"
      def parameterMap = [
        gce_project_id: gceBakeryDefaults.project,
        gce_zone: gceBakeryDefaults.zone,
        gce_source_image: SOURCE_TRUSTY_IMAGE_NAME,
        gce_target_image: targetImageName,
        deb_repo: DEBIAN_REPOSITORY,
        packages: fullyQualifiedPackageName,
        appversion: appVersionStr,
        build_host: buildHost
      ]

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(gceBakeryDefaults: gceBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest) >>
        [targetImageName, appVersionStr, fullyQualifiedPackageName]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, gceBakeryDefaults.templateFile)
  }

  void 'throws exception when virtualization settings are not found for specified operating system'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.centos,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(gceBakeryDefaults: gceBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      IllegalArgumentException e = thrown()
      e.message == "No virtualization settings found for 'centos'."
  }

}
