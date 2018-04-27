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
import com.netflix.spinnaker.rosco.providers.util.PackageNameConverter
import com.netflix.spinnaker.rosco.providers.util.PackerArtifactService
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import com.netflix.spinnaker.rosco.providers.google.config.RoscoGoogleConfiguration
import com.netflix.spinnaker.rosco.providers.google.config.RoscoGoogleConfiguration.GCEBakeryDefaults
import com.netflix.spinnaker.rosco.providers.util.PackerManifest
import com.netflix.spinnaker.rosco.providers.util.PackerManifestService
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import spock.lang.Shared
import spock.lang.Subject
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Paths

class GCEBakeHandlerSpec extends Specification implements TestDefaults{

  private static final String REGION = "us-central1"
  private static final String SOURCE_PRECISE_IMAGE_NAME = "some-precise-image"
  private static final String SOURCE_TRUSTY_IMAGE_NAME = "some-trusty-image"
  private static final String SOURCE_XENIAL_IMAGE_NAME = "some-xenial-image"
  private static final String SOURCE_YAKKETY_IMAGE_NAME = "some-yakkety-image"
  private static final String SOURCE_CENTOS_HVM_IMAGE_NAME = "some-centos-image"
  private static final String MANIFEST_FILE_NAME = "/path/to/stub-manifest.json"
  private static final String ARTIFACT_FILE_NAME = "/path/to/stub-artifact.json"

  @Shared
  String configDir = "/some/path"

  @Shared
  GCEBakeryDefaults gceBakeryDefaults

  @Shared
  RoscoGoogleConfiguration.GoogleConfigurationProperties googleConfigurationProperties

  @Shared
  ObjectMapper objectMapper = new ObjectMapper()

  @Shared
  def gceBakeryDefaultsJson = [
    zone: "us-central1-a",
    network: "default",
    templateFile: "gce_template.json",
    baseImages: [
      [
        baseImage: [
          id: "precise",
          packageType: "DEB",
        ],
        virtualizationSettings: [
          sourceImage: SOURCE_PRECISE_IMAGE_NAME
        ]
      ],
      [
        baseImage: [
          id: "trusty",
          packageType: "DEB",
        ],
        virtualizationSettings: [
          sourceImage: SOURCE_TRUSTY_IMAGE_NAME
        ]
      ],
      [
        baseImage: [
          id: "xenial",
          packageType: "DEB",
        ],
        virtualizationSettings: [
          sourceImageFamily: SOURCE_XENIAL_IMAGE_NAME + "-family"
        ]
      ],
      [
        baseImage: [
          id: "yakkety",
          packageType: "DEB",
        ],
        virtualizationSettings: [
          sourceImage: SOURCE_YAKKETY_IMAGE_NAME,
          sourceImageFamily: SOURCE_YAKKETY_IMAGE_NAME + "-family"
        ]
      ],
      [
        baseImage: [
          id: "centos",
          packageType: "RPM",
        ],
        virtualizationSettings: [
          sourceImage: SOURCE_CENTOS_HVM_IMAGE_NAME
        ]
      ],
      [
        baseImage: [
          id: "coyote",
          packageType: "DEB",
        ],
        virtualizationSettings: [
          sourceImage: null,
          sourceImageFamily: null
        ]
      ]
    ]
  ]

  void setupSpec() {
    gceBakeryDefaults = new ObjectMapper().convertValue(gceBakeryDefaultsJson, RoscoGoogleConfiguration.GCEBakeryDefaults)

    def googleConfigurationPropertiesJson = [
      accounts: [
        [
          name: "my-google-account",
          project: "some-gcp-project"
        ],
        [
          name: "my-other-google-account",
          project: "some-other-gcp-project",
          jsonPath: "some-json-path.json"
        ]
      ]
    ]

    googleConfigurationProperties = new ObjectMapper().convertValue(googleConfigurationPropertiesJson, RoscoGoogleConfiguration.GoogleConfigurationProperties)
  }

  void 'can scrape packer logs for image name'() {
    setup:
      def packerManifestServiceMock = Mock(PackerManifestService)
      packerManifestServiceMock.manifestExists(*_) >> false
      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(googleConfigurationProperties: googleConfigurationProperties,
                                                         packerManifestService: packerManifestServiceMock)

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
      def packerManifestServiceMock = Mock(PackerManifestService)
      packerManifestServiceMock.manifestExists(*_) >> false
      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(gceBakeryDefaults: gceBakeryDefaults,
                                                         packerManifestService: packerManifestServiceMock)
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

  void 'can get image name from manifest file'() {
    setup:
      def packerManifestServiceMock = Mock(PackerManifestService)
      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(googleConfigurationProperties: googleConfigurationProperties,
                                                         packerManifestService: packerManifestServiceMock)

    when:
      // Pass in valid logs to ensure that we're prioritizing the image name from the manifest
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
      1 * packerManifestServiceMock.manifestExists("123") >> true
      1 * packerManifestServiceMock.getBuild("123") >> {
        def build = new PackerManifest.PackerBuild()
        build.setArtifactId("my-test-image-name")
        return build
      }
      with (bake) {
        id == "123"
        !ami
        image_name == "my-test-image-name"
      }
  }

  void 'produces packer command with all required parameters for precise'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def packerArtifactServiceMock = Mock(PackerArtifactService)
      def packerManifestServiceMock = Mock(PackerManifestService)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "precise",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def targetImageName = "kato-x8664-timestamp-precise"
      def osPackages = parseDebOsPackageNames(bakeRequest.package_name)
      def parameterMap = [
        gce_project_id: googleConfigurationProperties.accounts.get(0).project,
        gce_zone: gceBakeryDefaults.zone,
        gce_network: gceBakeryDefaults.network,
        gce_source_image: SOURCE_PRECISE_IMAGE_NAME,
        gce_target_image: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        artifactFile: ARTIFACT_FILE_NAME,
        manifestFile: MANIFEST_FILE_NAME
      ]

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(configDir: configDir,
                                                         gceBakeryDefaults: gceBakeryDefaults,
                                                         googleConfigurationProperties: googleConfigurationProperties,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         packerArtifactService: packerArtifactServiceMock,
                                                         packerManifestService: packerManifestServiceMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerArtifactServiceMock.writeArtifactsToFile(bakeRequest.request_id, _) >> Paths.get(ARTIFACT_FILE_NAME)
      1 * packerManifestServiceMock.getManifestFileName(bakeRequest.request_id) >> MANIFEST_FILE_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$gceBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for centos'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def packerArtifactServiceMock = Mock(PackerArtifactService)
      def packerManifestServiceMock = Mock(PackerManifestService)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def targetImageName = "kato-x8664-timestamp-centos"
      def osPackages = parseRpmOsPackageNames(bakeRequest.package_name)
      def parameterMap = [
        gce_project_id: googleConfigurationProperties.accounts.get(0).project,
        gce_zone: gceBakeryDefaults.zone,
        gce_network: gceBakeryDefaults.network,
        gce_source_image: SOURCE_CENTOS_HVM_IMAGE_NAME,
        gce_target_image: targetImageName,
        repository: YUM_REPOSITORY,
        package_type: RPM_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        artifactFile: ARTIFACT_FILE_NAME,
        manifestFile: MANIFEST_FILE_NAME
      ]

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(configDir: configDir,
                                                         gceBakeryDefaults: gceBakeryDefaults,
                                                         googleConfigurationProperties: googleConfigurationProperties,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         packerArtifactService: packerArtifactServiceMock,
                                                         packerManifestService: packerManifestServiceMock,
                                                         yumRepository: YUM_REPOSITORY)

    when:
      gceBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, RPM_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(RPM_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerArtifactServiceMock.writeArtifactsToFile(bakeRequest.request_id, _) >> Paths.get(ARTIFACT_FILE_NAME)
      1 * packerManifestServiceMock.getManifestFileName(bakeRequest.request_id) >> MANIFEST_FILE_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$gceBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for precise, and overriding base image'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def packerArtifactServiceMock = Mock(PackerArtifactService)
      def packerManifestServiceMock = Mock(PackerManifestService)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "precise",
                                        base_ami: "some-gce-image-name",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def targetImageName = "kato-x8664-timestamp-precise"
      def osPackages = parseDebOsPackageNames(bakeRequest.package_name)
      def parameterMap = [
        gce_project_id: googleConfigurationProperties.accounts.get(0).project,
        gce_zone: gceBakeryDefaults.zone,
        gce_network: gceBakeryDefaults.network,
        gce_source_image: "some-gce-image-name",
        gce_target_image: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        artifactFile: ARTIFACT_FILE_NAME,
        manifestFile: MANIFEST_FILE_NAME
      ]

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(configDir: configDir,
                                                         gceBakeryDefaults: gceBakeryDefaults,
                                                         googleConfigurationProperties: googleConfigurationProperties,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         packerArtifactService: packerArtifactServiceMock,
                                                         packerManifestService: packerManifestServiceMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerArtifactServiceMock.writeArtifactsToFile(bakeRequest.request_id, _) >> Paths.get(ARTIFACT_FILE_NAME)
      1 * packerManifestServiceMock.getManifestFileName(bakeRequest.request_id) >> MANIFEST_FILE_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$gceBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for precise, and overriding template filename'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def packerArtifactServiceMock = Mock(PackerArtifactService)
      def packerManifestServiceMock = Mock(PackerManifestService)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "precise",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce,
                                        template_file_name: "somePackerTemplate.json")
      def osPackages = parseDebOsPackageNames(bakeRequest.package_name)
      def targetImageName = "kato-x8664-timestamp-precise"
      def parameterMap = [
        gce_project_id: googleConfigurationProperties.accounts.get(0).project,
        gce_zone: gceBakeryDefaults.zone,
        gce_network: gceBakeryDefaults.network,
        gce_source_image: SOURCE_PRECISE_IMAGE_NAME,
        gce_target_image: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        artifactFile: ARTIFACT_FILE_NAME,
        manifestFile: MANIFEST_FILE_NAME
      ]

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(configDir: configDir,
                                                         gceBakeryDefaults: gceBakeryDefaults,
                                                         googleConfigurationProperties: googleConfigurationProperties,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         packerArtifactService: packerArtifactServiceMock,
                                                         packerManifestService: packerManifestServiceMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerArtifactServiceMock.writeArtifactsToFile(bakeRequest.request_id, _) >> Paths.get(ARTIFACT_FILE_NAME)
      1 * packerManifestServiceMock.getManifestFileName(bakeRequest.request_id) >> MANIFEST_FILE_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/somePackerTemplate.json")
  }

  void 'produces packer command with all required parameters for precise, and adding extended attributes'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def packerArtifactServiceMock = Mock(PackerArtifactService)
      def packerManifestServiceMock = Mock(PackerManifestService)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "precise",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce,
                                        extended_attributes: [someAttr1: "someValue1", someAttr2: "someValue2"])
      def targetImageName = "kato-x8664-timestamp-precise"
      def osPackages = parseDebOsPackageNames(bakeRequest.package_name)
      def parameterMap = [
        gce_project_id: googleConfigurationProperties.accounts.get(0).project,
        gce_zone: gceBakeryDefaults.zone,
        gce_network: gceBakeryDefaults.network,
        gce_source_image: SOURCE_PRECISE_IMAGE_NAME,
        gce_target_image: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        artifactFile: ARTIFACT_FILE_NAME,
        manifestFile: MANIFEST_FILE_NAME,
        someAttr1: "someValue1",
        someAttr2: "someValue2"
      ]

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(configDir: configDir,
                                                         gceBakeryDefaults: gceBakeryDefaults,
                                                         googleConfigurationProperties: googleConfigurationProperties,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         packerArtifactService: packerArtifactServiceMock,
                                                         packerManifestService: packerManifestServiceMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerArtifactServiceMock.writeArtifactsToFile(bakeRequest.request_id, _) >> Paths.get(ARTIFACT_FILE_NAME)
      1 * packerManifestServiceMock.getManifestFileName(bakeRequest.request_id) >> MANIFEST_FILE_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$gceBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for precise, and overrides native attributes via extended attributes'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def packerArtifactServiceMock = Mock(PackerArtifactService)
      def packerManifestServiceMock = Mock(PackerManifestService)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "precise",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce,
                                        extended_attributes: [
                                          gce_zone: "europe-west1-b",
                                          gce_network: "other-network",
                                          gce_subnetwork: "custom-subnetwork"
                                        ])
      def targetImageName = "kato-x8664-timestamp-precise"
      def osPackages = parseDebOsPackageNames(bakeRequest.package_name)
      def parameterMap = [
        gce_project_id: googleConfigurationProperties.accounts.get(0).project,
        gce_zone: "europe-west1-b",
        gce_network: "other-network",
        gce_subnetwork: "custom-subnetwork",
        gce_source_image: SOURCE_PRECISE_IMAGE_NAME,
        gce_target_image: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        artifactFile: ARTIFACT_FILE_NAME,
        manifestFile: MANIFEST_FILE_NAME
      ]

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(configDir: configDir,
                                                         gceBakeryDefaults: gceBakeryDefaults,
                                                         googleConfigurationProperties: googleConfigurationProperties,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         packerArtifactService: packerArtifactServiceMock,
                                                         packerManifestService: packerManifestServiceMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerArtifactServiceMock.writeArtifactsToFile(bakeRequest.request_id, _) >> Paths.get(ARTIFACT_FILE_NAME)
      1 * packerManifestServiceMock.getManifestFileName(bakeRequest.request_id) >> MANIFEST_FILE_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$gceBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for precise, and respects optional subnetwork config default'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def packerArtifactServiceMock = Mock(PackerArtifactService)
      def packerManifestServiceMock = Mock(PackerManifestService)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "precise",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def targetImageName = "kato-x8664-timestamp-precise"
      def osPackages = parseDebOsPackageNames(bakeRequest.package_name)
      def parameterMap = [
        gce_project_id: googleConfigurationProperties.accounts.get(0).project,
        gce_zone: gceBakeryDefaults.zone,
        gce_network: gceBakeryDefaults.network,
        gce_subnetwork: "custom-subnetwork",
        gce_source_image: SOURCE_PRECISE_IMAGE_NAME,
        gce_target_image: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        artifactFile: ARTIFACT_FILE_NAME,
        manifestFile: MANIFEST_FILE_NAME
      ]
      def gceBakeryDefaultsAugmented = gceBakeryDefaults.clone()
      gceBakeryDefaultsAugmented.subnetwork = "custom-subnetwork"

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(configDir: configDir,
                                                         gceBakeryDefaults: gceBakeryDefaultsAugmented,
                                                         googleConfigurationProperties: googleConfigurationProperties,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         packerArtifactService: packerArtifactServiceMock,
                                                         packerManifestService: packerManifestServiceMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerArtifactServiceMock.writeArtifactsToFile(bakeRequest.request_id, _) >> Paths.get(ARTIFACT_FILE_NAME)
      1 * packerManifestServiceMock.getManifestFileName(bakeRequest.request_id) >> MANIFEST_FILE_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$gceBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for trusty'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def packerArtifactServiceMock = Mock(PackerArtifactService)
      def packerManifestServiceMock = Mock(PackerManifestService)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "trusty",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def osPackages = parseDebOsPackageNames(bakeRequest.package_name)
      def targetImageName = "kato-x8664-timestamp-trusty"
      def parameterMap = [
        gce_project_id: googleConfigurationProperties.accounts.get(0).project,
        gce_zone: gceBakeryDefaults.zone,
        gce_network: gceBakeryDefaults.network,
        gce_source_image: SOURCE_TRUSTY_IMAGE_NAME,
        gce_target_image: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        artifactFile: ARTIFACT_FILE_NAME,
        manifestFile: MANIFEST_FILE_NAME
      ]

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(configDir: configDir,
                                                         gceBakeryDefaults: gceBakeryDefaults,
                                                         googleConfigurationProperties: googleConfigurationProperties,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         packerArtifactService: packerArtifactServiceMock,
                                                         packerManifestService: packerManifestServiceMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerArtifactServiceMock.writeArtifactsToFile(bakeRequest.request_id, _) >> Paths.get(ARTIFACT_FILE_NAME)
      1 * packerManifestServiceMock.getManifestFileName(bakeRequest.request_id) >> MANIFEST_FILE_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$gceBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for trusty, including network project id'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def packerArtifactServiceMock = Mock(PackerArtifactService)
      def packerManifestServiceMock = Mock(PackerManifestService)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "trusty",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def osPackages = parseDebOsPackageNames(bakeRequest.package_name)
      def targetImageName = "kato-x8664-timestamp-trusty"
      def gceBakeryDefaults = new ObjectMapper().convertValue(gceBakeryDefaultsJson, RoscoGoogleConfiguration.GCEBakeryDefaults)
      gceBakeryDefaults.networkProjectId = "some-xpn-host-project"
      def parameterMap = [
        gce_project_id: googleConfigurationProperties.accounts.get(0).project,
        gce_zone: gceBakeryDefaults.zone,
        gce_network: gceBakeryDefaults.network,
        gce_network_project_id: gceBakeryDefaults.networkProjectId,
        gce_source_image: SOURCE_TRUSTY_IMAGE_NAME,
        gce_target_image: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        artifactFile: ARTIFACT_FILE_NAME,
        manifestFile: MANIFEST_FILE_NAME
      ]

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(configDir: configDir,
                                                         gceBakeryDefaults: gceBakeryDefaults,
                                                         googleConfigurationProperties: googleConfigurationProperties,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         packerArtifactService: packerArtifactServiceMock,
                                                         packerManifestService: packerManifestServiceMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerArtifactServiceMock.writeArtifactsToFile(bakeRequest.request_id, _) >> Paths.get(ARTIFACT_FILE_NAME)
      1 * packerManifestServiceMock.getManifestFileName(bakeRequest.request_id) >> MANIFEST_FILE_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$gceBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters including appversion, build_host and build_info_url for trusty'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def packerArtifactServiceMock = Mock(PackerArtifactService)
      def packerManifestServiceMock = Mock(PackerManifestService)
      def fullyQualifiedPackageName = "nflx-djangobase-enhanced_0.1-h12.170cdbd_all"
      def appVersionStr = "nflx-djangobase-enhanced-0.1-170cdbd.h12"
      def buildHost = "http://some-build-server:8080"
      def buildInfoUrl = "http://some-build-server:8080/repogroup/repo/builds/320282"
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: fullyQualifiedPackageName,
                                        base_os: "trusty",
                                        build_host: buildHost,
                                        build_info_url: buildInfoUrl,
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def osPackage = PackageNameConverter.buildOsPackageName(DEB_PACKAGE_TYPE, bakeRequest.package_name)
      def targetImageName = "kato-x8664-timestamp-trusty"
      def parameterMap = [
        gce_project_id: googleConfigurationProperties.accounts.get(0).project,
        gce_zone: gceBakeryDefaults.zone,
        gce_network: gceBakeryDefaults.network,
        gce_source_image: SOURCE_TRUSTY_IMAGE_NAME,
        gce_target_image: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: fullyQualifiedPackageName,
        configDir: configDir,
        artifactFile: ARTIFACT_FILE_NAME,
        manifestFile: MANIFEST_FILE_NAME,
        appversion: appVersionStr,
        build_host: buildHost,
        build_info_url: buildInfoUrl
      ]

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(configDir: configDir,
                                                         gceBakeryDefaults: gceBakeryDefaults,
                                                         googleConfigurationProperties: googleConfigurationProperties,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         packerArtifactService: packerArtifactServiceMock,
                                                         packerManifestService: packerManifestServiceMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, [osPackage]) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, [osPackage], DEB_PACKAGE_TYPE) >> appVersionStr
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, [osPackage]) >> fullyQualifiedPackageName
      1 * packerArtifactServiceMock.writeArtifactsToFile(bakeRequest.request_id, _) >> Paths.get(ARTIFACT_FILE_NAME)
      1 * packerManifestServiceMock.getManifestFileName(bakeRequest.request_id) >> MANIFEST_FILE_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$gceBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for trusty and reflects specified account_name'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def packerArtifactServiceMock = Mock(PackerArtifactService)
      def packerManifestServiceMock = Mock(PackerManifestService)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "trusty",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce,
                                        account_name: 'my-other-google-account')
      def osPackages = parseDebOsPackageNames(bakeRequest.package_name)
      def targetImageName = "kato-x8664-timestamp-trusty"
      def parameterMap = [
        gce_project_id: googleConfigurationProperties.accounts.get(1).project,
        gce_account_file: googleConfigurationProperties.accounts.get(1).jsonPath,
        gce_zone: gceBakeryDefaults.zone,
        gce_network: gceBakeryDefaults.network,
        gce_source_image: SOURCE_TRUSTY_IMAGE_NAME,
        gce_target_image: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        artifactFile: ARTIFACT_FILE_NAME,
        manifestFile: MANIFEST_FILE_NAME
      ]

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(configDir: configDir,
                                                         gceBakeryDefaults: gceBakeryDefaults,
                                                         googleConfigurationProperties: googleConfigurationProperties,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         packerArtifactService: packerArtifactServiceMock,
                                                         packerManifestService: packerManifestServiceMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerArtifactServiceMock.writeArtifactsToFile(bakeRequest.request_id, _) >> Paths.get(ARTIFACT_FILE_NAME)
      1 * packerManifestServiceMock.getManifestFileName(bakeRequest.request_id) >> MANIFEST_FILE_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$gceBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for xenial, when source image family is configured'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def packerArtifactServiceMock = Mock(PackerArtifactService)
      def packerManifestServiceMock = Mock(PackerManifestService)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "xenial",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def osPackages = parseDebOsPackageNames(bakeRequest.package_name)
      def targetImageName = "kato-x8664-timestamp-xenial"
      def parameterMap = [
        gce_project_id: googleConfigurationProperties.accounts.get(0).project,
        gce_zone: gceBakeryDefaults.zone,
        gce_network: gceBakeryDefaults.network,
        gce_source_image_family: SOURCE_XENIAL_IMAGE_NAME + "-family",
        gce_target_image: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        artifactFile: ARTIFACT_FILE_NAME,
        manifestFile: MANIFEST_FILE_NAME
      ]

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(configDir: configDir,
                                                         gceBakeryDefaults: gceBakeryDefaults,
                                                         googleConfigurationProperties: googleConfigurationProperties,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         packerArtifactService: packerArtifactServiceMock,
                                                         packerManifestService: packerManifestServiceMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerArtifactServiceMock.writeArtifactsToFile(bakeRequest.request_id, _) >> Paths.get(ARTIFACT_FILE_NAME)
      1 * packerManifestServiceMock.getManifestFileName(bakeRequest.request_id) >> MANIFEST_FILE_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$gceBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for xenial, and overriding base image even though source image family is configured'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def packerArtifactServiceMock = Mock(PackerArtifactService)
      def packerManifestServiceMock = Mock(PackerManifestService)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "xenial",
                                        base_ami: "some-gce-image-name",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def targetImageName = "kato-x8664-timestamp-xenial"
      def osPackages = parseDebOsPackageNames(bakeRequest.package_name)
      def parameterMap = [
        gce_project_id: googleConfigurationProperties.accounts.get(0).project,
        gce_zone: gceBakeryDefaults.zone,
        gce_network: gceBakeryDefaults.network,
        gce_source_image: "some-gce-image-name",
        gce_target_image: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        artifactFile: ARTIFACT_FILE_NAME,
        manifestFile: MANIFEST_FILE_NAME
      ]

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(configDir: configDir,
                                                         gceBakeryDefaults: gceBakeryDefaults,
                                                         googleConfigurationProperties: googleConfigurationProperties,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         packerArtifactService: packerArtifactServiceMock,
                                                         packerManifestService: packerManifestServiceMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerArtifactServiceMock.writeArtifactsToFile(bakeRequest.request_id, _) >> Paths.get(ARTIFACT_FILE_NAME)
      1 * packerManifestServiceMock.getManifestFileName(bakeRequest.request_id) >> MANIFEST_FILE_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$gceBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for yakkety, preferring source image when both source image and source image family are configured'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def packerArtifactServiceMock = Mock(PackerArtifactService)
      def packerManifestServiceMock = Mock(PackerManifestService)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "yakkety",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def osPackages = parseDebOsPackageNames(bakeRequest.package_name)
      def targetImageName = "kato-x8664-timestamp-yakkety"
      def parameterMap = [
        gce_project_id: googleConfigurationProperties.accounts.get(0).project,
        gce_zone: gceBakeryDefaults.zone,
        gce_network: gceBakeryDefaults.network,
        gce_source_image: SOURCE_YAKKETY_IMAGE_NAME,
        gce_target_image: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        artifactFile: ARTIFACT_FILE_NAME,
        manifestFile: MANIFEST_FILE_NAME
      ]

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(configDir: configDir,
                                                         gceBakeryDefaults: gceBakeryDefaults,
                                                         googleConfigurationProperties: googleConfigurationProperties,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         packerArtifactService: packerArtifactServiceMock,
                                                         packerManifestService: packerManifestServiceMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerArtifactServiceMock.writeArtifactsToFile(bakeRequest.request_id, _) >> Paths.get(ARTIFACT_FILE_NAME)
      1 * packerManifestServiceMock.getManifestFileName(bakeRequest.request_id) >> MANIFEST_FILE_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$gceBakeryDefaults.templateFile")
  }

  void 'throws exception when virtualization settings are not found for specified operating system'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "wily",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(gceBakeryDefaults: gceBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      IllegalArgumentException e = thrown()
      e.message == "No virtualization settings found for 'wily'."
  }

  void 'throws exception when neither source image nor source image family are found for specified operating system'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "coyote",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(gceBakeryDefaults: gceBakeryDefaults,
                                                         googleConfigurationProperties: googleConfigurationProperties,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      IllegalArgumentException e = thrown()
      e.message == "No source image or source image family found for 'coyote'."
  }

  void 'throws exception when zero google accounts are configured'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "trusty",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def osPackages = parseDebOsPackageNames(bakeRequest.package_name)
      def targetImageName = "kato-x8664-timestamp-trusty"

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(gceBakeryDefaults: gceBakeryDefaults,
                                                         googleConfigurationProperties: new RoscoGoogleConfiguration.GoogleConfigurationProperties(),
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      gceBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName

      IllegalArgumentException e = thrown()
      e.message == "Could not resolve Google account: (account_name=null)."
  }

  void 'produce a default GCE bakeKey without base image'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(googleConfigurationProperties: googleConfigurationProperties)

    when:
      String bakeKey = gceBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:gce:centos:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:my-google-account"
  }

  @Unroll
  void 'produce a default GCE bakeKey without base image, even when no packages are specified'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: packageName,
                                        base_os: "centos",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(googleConfigurationProperties: googleConfigurationProperties)

    when:
      String bakeKey = gceBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:gce:centos::my-google-account"

    where:
      packageName << [null, ""]
  }

  void 'produce a default GCE bakeKey with base image'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce,
                                        base_ami: "my-base-image")

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(googleConfigurationProperties: googleConfigurationProperties)

    when:
      String bakeKey = gceBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:gce:centos:my-base-image:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:my-google-account"
  }

  void 'account selection is reflected in bake key'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce,
                                        account_name: 'my-other-google-account')

      @Subject
      GCEBakeHandler gceBakeHandler = new GCEBakeHandler(googleConfigurationProperties: googleConfigurationProperties)

    when:
      String bakeKey = gceBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:gce:centos:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:my-other-google-account"
  }
}
