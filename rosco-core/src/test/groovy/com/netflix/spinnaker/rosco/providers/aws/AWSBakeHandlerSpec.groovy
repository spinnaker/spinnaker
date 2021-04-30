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

package com.netflix.spinnaker.rosco.providers.aws

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.config.RoscoConfiguration
import com.netflix.spinnaker.rosco.providers.aws.config.RoscoAWSConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackageNameConverter
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import com.netflix.spinnaker.rosco.services.ClouddriverService
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class AWSBakeHandlerSpec extends Specification implements TestDefaults {

  private static final String REGION = "us-east-1"
  private static final String SOURCE_UBUNTU_HVM_IMAGE_NAME = "ami-a123456b"
  private static final String SOURCE_UBUNTU_PV_IMAGE_NAME = "ami-a654321b"
  private static final String SOURCE_BIONIC_HVM_IMAGE_NAME = "bionic-base-123"
  private static final String SOURCE_BIONIC_HVM_IMAGE_ID = "ami-b054321b"
  private static final String SOURCE_TRUSTY_HVM_IMAGE_NAME = "ami-c456789d"
  private static final String SOURCE_AMZN_HVM_IMAGE_NAME = "ami-8fcee4e5"
  private static final String SOURCE_WINDOWS_2012_R2_HVM_IMAGE_NAME = "ami-21414f36"

  @Shared
  String configDir = "/some/path"

  @Shared
  RoscoAWSConfiguration.AWSBakeryDefaults awsBakeryDefaults

  @Shared
  List<RoscoAWSConfiguration.AWSNamedImage> searchByNameResults

  @Shared
  RoscoConfiguration roscoConfiguration

  void setupSpec() {
    def awsBakeryDefaultsJson = [
      templateFile: "aws_template.json",
      defaultVirtualizationType: "hvm",
      baseImages: [
        [
          baseImage: [
            id: "ubuntu",
            packageType: "DEB",
          ],
          virtualizationSettings: [
            [
              region: REGION,
              virtualizationType: "hvm",
              instanceType: "t2.micro",
              sourceAmi: SOURCE_UBUNTU_HVM_IMAGE_NAME,
              sshUserName: "ubuntu",
              spotPrice: "auto",
              spotPriceAutoProduct: "Linux/UNIX (Amazon VPC)"
            ],
            [
              region: REGION,
              virtualizationType: "pv",
              instanceType: "m3.medium",
              sourceAmi: SOURCE_UBUNTU_PV_IMAGE_NAME,
              sshUserName: "ubuntu",
              spotPrice: "auto",
              spotPriceAutoProduct: "Linux/UNIX (Amazon VPC)"
            ]
          ]
        ],
        [
          baseImage: [
            id: "bionic",
            packageType: "DEB"
          ],
          virtualizationSettings: [
            [
              region: REGION,
              virtualizationType: "hvm",
              sourceAmi: SOURCE_BIONIC_HVM_IMAGE_NAME
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
              virtualizationType: "hvm",
              instanceType: "t2.micro",
              sourceAmi: SOURCE_TRUSTY_HVM_IMAGE_NAME,
              sshUserName: "ubuntu",
              spotPrice: "auto",
              spotPriceAutoProduct: "Linux/UNIX (Amazon VPC)"
            ]
          ]
        ],
        [
          baseImage: [
            id: "trusty_custom_repo",
            packageType: "DEB",
            customRepository: DEBIAN_CUSTOM_REPOSITORY
          ],
          virtualizationSettings: [
            [
              region: REGION,
              virtualizationType: "hvm",
              instanceType: "t2.micro",
              sourceAmi: SOURCE_TRUSTY_HVM_IMAGE_NAME,
              sshUserName: "ubuntu",
              spotPrice: "auto",
              spotPriceAutoProduct: "Linux/UNIX (Amazon VPC)"
            ]
          ]
        ],
        [
          baseImage: [
           id: "amzn",
           packageType: "RPM",
          ],
          virtualizationSettings: [
            [
              region: REGION,
              virtualizationType: "hvm",
              instanceType: "t2.micro",
              sourceAmi: SOURCE_AMZN_HVM_IMAGE_NAME,
              sshUserName: "ec2-user",
              spotPrice: "auto",
              spotPriceAutoProduct: "Linux/UNIX (Amazon VPC)"
            ]
          ]
        ],
        [
          baseImage: [
            id: "windows-2012-r2",
            packageType: "NUPKG",
            templateFile: "aws-windows-2012-r2.json"
          ],
          virtualizationSettings: [
            [
              region: REGION,
              virtualizationType: "hvm",
              instanceType: "t2.micro",
              sourceAmi: SOURCE_WINDOWS_2012_R2_HVM_IMAGE_NAME,
              winRmUserName: "Administrator",
              spotPrice: "auto",
              spotPriceAutoProduct: "Windows (Amazon VPC)"
            ]
          ]
        ],
        [
            baseImage: [
                id: "xenial",
                packageType: "DEB",
            ],
            virtualizationSettings: [
                [
                    region: REGION,
                    virtualizationType: "hvm",
                    instanceType: "t2.micro",
                    sourceAmi: "ubuntu/images/hvm-ssd/ubuntu-xenial-16.04-amd64-server-",
                    mostRecent: true,
                    sshUserName: "ubuntu",
                    spotPrice: "auto",
                    spotPriceAutoProduct: "Linux/UNIX (Amazon VPC)"
                ]
            ]
        ],
        [
            baseImage: [
                id: "xenial-not-recent",
                packageType: "DEB",
            ],
            virtualizationSettings: [
                [
                    region: REGION,
                    virtualizationType: "hvm",
                    instanceType: "t2.micro",
                    sourceAmi: "ubuntu/images/hvm-ssd/ubuntu-xenial-16.04-amd64-server-",
                    mostRecent: false,
                    sshUserName: "ubuntu",
                    spotPrice: "auto",
                    spotPriceAutoProduct: "Linux/UNIX (Amazon VPC)"
                ]
            ]
        ],
      ]
    ]

    awsBakeryDefaults = new ObjectMapper().convertValue(awsBakeryDefaultsJson, RoscoAWSConfiguration.AWSBakeryDefaults)

    searchByNameResults = [
        new RoscoAWSConfiguration.AWSNamedImage(
            imageName: "ubuntu/images/hvm-ssd/ubuntu-xenial-16.04-amd64-server-20200126",
            attributes: new RoscoAWSConfiguration.AWSImageAttributes(
                creationDate: new Date(2020, 1, 26),
                virtualizationType: BakeRequest.VmType.hvm
            ),
            amis: [
                (REGION): ["ami-20200126"]
            ]
        ),
        new RoscoAWSConfiguration.AWSNamedImage(
            imageName: "ubuntu/images/hvm-ssd/ubuntu-xenial-16.04-amd64-server-20201210",
            attributes: new RoscoAWSConfiguration.AWSImageAttributes(
                creationDate: new Date(2020, 12, 10),
                virtualizationType: BakeRequest.VmType.hvm
            ),
            amis: [
                (REGION): ["ami-20201210"]
            ]
        ),
        new RoscoAWSConfiguration.AWSNamedImage(
            imageName: "ubuntu/images/hvm-ssd/ubuntu-xenial-16.04-amd64-server-20201009",
            attributes: new RoscoAWSConfiguration.AWSImageAttributes(
                creationDate: new Date(2020, 10, 9),
                virtualizationType: BakeRequest.VmType.hvm
            ),
            amis: [
                (REGION): ["ami-20201009"]
            ]
        ),
    ]
  }

  void 'can scrape packer logs for image name'() {
    setup:
      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      def logsContent =
        "    amazon-ebs: Processing triggers for libc-bin ...\n" +
        "    amazon-ebs: ldconfig deferred processing now taking place\n" +
        "==> amazon-ebs: Stopping the source instance...\n" +
        "==> amazon-ebs: Waiting for the instance to stop...\n" +
        "==> amazon-ebs: Creating the AMI: kato-x8664-1422459898853-ubuntu\n" +
        "    amazon-ebs: AMI: ami-2c014644\n" +
        "==> amazon-ebs: Waiting for AMI to become ready...\n" +
        "==> amazon-ebs: Terminating the source AWS instance...\n" +
        "==> amazon-ebs: Deleting temporary security group...\n" +
        "==> amazon-ebs: Deleting temporary keypair...\n" +
        "Build 'amazon-ebs' finished.\n" +
        "\n" +
        "==> Builds finished. The artifacts of successful builds are:\n" +
        "--> amazon-ebs: AMIs were created:\n" +
        "\n" +
        "us-east-1: ami-2c014644"

      Bake bake = awsBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

    then:
      with (bake) {
        id == "123"
        ami == "ami-2c014644"
        image_name == "kato-x8664-1422459898853-ubuntu"
      }
  }

  void 'can scrape packer 1.3+ logs for unencrypted image name'() {
    setup:
    @Subject
    AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
    def logsContent =
      "==> amazon-ebs: Creating unencrypted AMI test-ami-123456789_123456789 from instance i-123456789\n" +
      "    amazon-ebs: AMI: ami-2c014644\n" +
      "==> amazon-ebs: Waiting for AMI to become ready...\n" +
      "==> amazon-ebs: Terminating the source AWS instance...\n" +
      "==> amazon-ebs: Cleaning up any extra volumes...\n" +
      "==> amazon-ebs: No volumes to clean up, skipping\n" +
      "==> amazon-ebs: Deleting temporary security group...\n" +
      "==> amazon-ebs: Deleting temporary keypair...\n" +
      "Build 'amazon-ebs' finished.\n" +
      "\n" +
      "==> Builds finished. The artifacts of successful builds are:\n" +
      "--> amazon-ebs: AMIs were created:\n" +
      "\n" +
      "us-east-1: ami-2c014644"

    Bake bake = awsBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

    then:
    with (bake) {
      id == "123"
      ami == "ami-2c014644"
      image_name == "test-ami-123456789_123456789"
    }
  }

  void 'can scrape packer (amazon-chroot) logs for image name'() {
    setup:
      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      def logsContent =
        "    amazon-chroot: Processing triggers for libc-bin ...\n" +
        "    amazon-chroot: ldconfig deferred processing now taking place\n" +
        "==> amazon-chroot: Stopping the source instance...\n" +
        "==> amazon-chroot: Waiting for the instance to stop...\n" +
        "==> amazon-chroot: Creating the AMI: kato-x8664-1422459898853-ubuntu\n" +
        "    amazon-chroot: AMI: ami-2c014644\n" +
        "==> amazon-chroot: Waiting for AMI to become ready...\n" +
        "==> amazon-chroot: Terminating the source AWS instance...\n" +
        "==> amazon-chroot: Deleting temporary security group...\n" +
        "==> amazon-chroot: Deleting temporary keypair...\n" +
        "Build 'amazon-chroot' finished.\n" +
        "\n" +
        "==> Builds finished. The artifacts of successful builds are:\n" +
        "--> amazon-chroot: AMIs were created:\n" +
        "\n" +
        "us-east-1: ami-2c014644"

      Bake bake = awsBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

    then:
      with (bake) {
        id == "123"
        ami == "ami-2c014644"
        image_name == "kato-x8664-1422459898853-ubuntu"
      }
  }

  void 'can scrape copied amis'() {
    setup:
      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      def logsContent =
        "==> amazon-chroot: Unmounting the root device...\n" +
        "==> amazon-chroot: Detaching EBS volume...\n" +
        "==> amazon-chroot: Creating snapshot...\n" +
        "    amazon-chroot: Snapshot ID: snap-06452c9aceea2437f\n" +
        "==> amazon-chroot: Registering the AMI...\n" +
        "==> amazon-chroot: AMI: ami-0450240c900844342\n" +
        "==> amazon-chroot: Waiting for AMI to become ready...\n" +
        "==> amazon-chroot: Copying AMI (ami-0450240c900844342) to other regions...\n" +
        "    amazon-chroot: Copying to: eu-north-1\n" +
        "    amazon-chroot: Avoiding copying AMI to duplicate region eu-west-1\n" +
        "    amazon-chroot: Waiting for all copies to complete...\n" +
        "==> amazon-chroot: Modifying attributes on snapshot (snap-06452c9aceea2437f)...\n" +
        "==> amazon-chroot: Modifying attributes on snapshot (snap-00a31182e306507b8)...\n" +
        "==> amazon-chroot: Adding tags to AMI (ami-076cf277a86b6e5b4)...\n" +
        "==> amazon-chroot: Tagging snapshot: snap-00a31182e306507b8\n" +
        "==> amazon-chroot: Creating snapshot tags\n" +
        "==> amazon-chroot: Adding tags to AMI (ami-0450240c900844342)...\n" +
        "==> amazon-chroot: Tagging snapshot: snap-06452c9aceea2437f\n" +
        "==> amazon-chroot: Creating snapshot tags\n" +
        "==> amazon-chroot: Deleting the created EBS volume...\n" +
        "Build 'amazon-chroot' finished.\n" +
        "\n" +
        "==> Builds finished. The artifacts of successful builds are:\n" +
        "--> amazon-chroot: AMIs were created:\n" +
        "eu-north-1: ami-076cf277a86b6e5b4\n" +
        "eu-west-1: ami-0450240c900844342"

      Bake bake = awsBakeHandler.scrapeCompletedBakeResults("eu-west-1", "123", logsContent)

    then:
      with (bake) {
        id == "123"
        ami == "ami-0450240c900844342"

        artifacts.size() == 2
        artifacts.find{it.location == "eu-north-1"}.reference == "ami-076cf277a86b6e5b4"
        artifacts.find{it.location == "eu-west-1"}.reference == "ami-0450240c900844342"
      }
  }

  void 'can scrape packer (Windows) logs for image name'() {
    setup:
    @Subject
    AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
    def logsContent =
      "    amazon-ebs: Chocolatey installed 2/2 packages. 0 packages failed.\n" +
        "    amazon-ebs: See the log for details (C:\\ProgramData\\chocolatey\\logs\\chocolatey.log).\n" +
        "==> amazon-ebs: Stopping the source instance...\n" +
        "==> amazon-ebs: Waiting for the instance to stop...\n" +
        "==> amazon-ebs: Creating the AMI: googlechrome-all-20170121212839-windows-2012-r2\n" +
        "    amazon-ebs: AMI: ami-ca32c7dc\n" +
        "==> amazon-ebs: Waiting for AMI to become ready...\n" +
        "==> amazon-ebs: Adding tags to AMI (ami-ca32c7dc)...\n" +
        "    amazon-ebs: Adding tag: \"appversion\": \"\"\n" +
        "    amazon-ebs: Adding tag: \"build_host\": \"\"\n" +
        "    amazon-ebs: Adding tag: \"build_info_url\": \"\"\n" +
        "==> amazon-ebs: Tagging snapshot: snap-08b92184c6506720c\n" +
        "==> amazon-ebs: Terminating the source AWS instance...\n" +
        "==> amazon-ebs: Cleaning up any extra volumes...\n" +
        "==> amazon-ebs: No volumes to clean up, skipping\n" +
        "==> amazon-ebs: Deleting temporary security group...\n" +
        "==> amazon-ebs: Deleting temporary keypair...\n" +
        "Build 'amazon-ebs' finished.\n" +
        "\n" +
        "==> Builds finished. The artifacts of successful builds are:\n" +
        "--> amazon-ebs: AMIs were created:\n" +
        "\n" +
        "us-east-1: ami-ca32c7dc"

    Bake bake = awsBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

    then:
    with (bake) {
      id == "123"
      ami == "ami-ca32c7dc"
      image_name == "googlechrome-all-20170121212839-windows-2012-r2"
    }
  }

  void 'scraping returns null for missing image id'() {
    setup:
      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      def logsContent =
        "    amazon-ebs: Processing triggers for libc-bin ...\n" +
        "    amazon-ebs: ldconfig deferred processing now taking place\n" +
        "==> amazon-ebs: Stopping the source instance...\n" +
        "==> amazon-ebs: Waiting for the instance to stop...\n" +
        "==> amazon-ebs: Creating the AMI: kato-x8664-1422459898853-ubuntu\n" +
        "    amazon-ebs: AMI: ami-2c014644\n" +
        "==> amazon-ebs: Waiting for AMI to become ready...\n" +
        "==> amazon-ebs: Terminating the source AWS instance...\n" +
        "==> amazon-ebs: Deleting temporary security group...\n" +
        "==> amazon-ebs: Deleting temporary keypair...\n" +
        "Build 'amazon-ebs' finished.\n" +
        "\n" +
        "==> Builds finished. The artifacts of successful builds are:\n" +
        "--> amazon-ebs: AMIs were created:\n"

      Bake bake = awsBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

    then:
      with (bake) {
        id == "123"
        !ami
        image_name == "kato-x8664-1422459898853-ubuntu"
      }
  }

  void 'scraping returns null for missing image name'() {
    setup:
      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      def logsContent =
          "    amazon-ebs: Processing triggers for libc-bin ...\n" +
          "    amazon-ebs: ldconfig deferred processing now taking place\n" +
          "==> amazon-ebs: Stopping the source instance...\n" +
          "==> amazon-ebs: Waiting for the instance to stop...\n"

      Bake bake = awsBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

    then:
      with (bake) {
        id == "123"
        !ami
        !image_name
      }
  }

  void 'looks up base ami id from name'() {
    def packerCommandFactoryMock = Mock(PackerCommandFactory)
    def imageNameFactoryMock = Mock(ImageNameFactory)
    def clouddriverService = Mock(ClouddriverService)
    def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
      package_name: PACKAGES_NAME,
      base_os: "bionic",
      base_label: "release",
      vm_type: BakeRequest.VmType.hvm,
      cloud_provider_type: BakeRequest.CloudProviderType.aws)

    @Subject
    AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
      awsBakeryDefaults: awsBakeryDefaults,
      imageNameFactory: imageNameFactoryMock,
      packerCommandFactory: packerCommandFactoryMock,
      clouddriverService: clouddriverService,
      retrySupport: new NoSleepRetry(),
      defaultAccount: "test",
      debianRepository: DEBIAN_REPOSITORY)

    when:
    def vmSettings = awsBakeHandler.findVirtualizationSettings(REGION, bakeRequest)

    then:
    1 * clouddriverService.findAmazonImageByName(_, _, _) >> [
      new RoscoAWSConfiguration.AWSNamedImage(
        imageName: SOURCE_BIONIC_HVM_IMAGE_NAME,
        attributes: new RoscoAWSConfiguration.AWSImageAttributes(virtualizationType: BakeRequest.VmType.hvm),
        amis: [
          (REGION): [ SOURCE_BIONIC_HVM_IMAGE_ID ]
        ]
      )
    ]

    vmSettings.sourceAmi == SOURCE_BIONIC_HVM_IMAGE_ID
  }

  void 'produces packer command with all required parameters for ubuntu, using default vm type'() {
    setup:
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def parameterMap = [
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "t2.micro",
        aws_spot_price: "auto",
        aws_spot_price_auto_product: "Linux/UNIX (Amazon VPC)",
        aws_source_ami: SOURCE_UBUNTU_HVM_IMAGE_NAME,
        aws_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$awsBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for ubuntu, using ami lookup by name'() {
    setup:
      def clouddriverService = Stub(ClouddriverService) {
        findAmazonImageByName(_, _, _) >> {
          return searchByNameResults
        }
      }
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: base_os,
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def parameterMap = [
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "t2.micro",
        aws_spot_price: "auto",
        aws_spot_price_auto_product: "Linux/UNIX (Amazon VPC)",
        aws_source_ami: expected,
        aws_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         clouddriverService: clouddriverService,
                                                         retrySupport: new RetrySupport(),
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$awsBakeryDefaults.templateFile")

    where:
    base_os             | expected
    "xenial"            | "ami-20201210"
    "xenial-not-recent" | "ami-20200126"
  }

  void 'produces packer command with all required parameters for amzn, using default vm type'() {
    setup:
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "amzn",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)
      def targetImageName = "kato-x8664-timestamp-amzn"
      def osPackages = parseRpmOsPackageNames(PACKAGES_NAME)
      def parameterMap = [
        aws_region: REGION,
        aws_ssh_username: "ec2-user",
        aws_instance_type: "t2.micro",
        aws_source_ami: SOURCE_AMZN_HVM_IMAGE_NAME,
        aws_target_ami: targetImageName,
        aws_spot_price: "auto",
        aws_spot_price_auto_product: "Linux/UNIX (Amazon VPC)",
        repository: YUM_REPOSITORY,
        package_type: RPM_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         yumRepository: YUM_REPOSITORY)

    when:
      awsBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, RPM_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(RPM_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$awsBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for amzn, with sudo'() {
    setup:
      def awsBakeryDefaultsJson = [
        templateFile: "aws-chroot.json",
        defaultVirtualizationType: "hvm",
        baseImages: [
          [
            baseImage: [
              id: "amzn",
              packageType: "RPM",
            ],
            virtualizationSettings: [
              [
                region: REGION,
                virtualizationType: "hvm",
                instanceType: "t2.micro",
                sourceAmi: SOURCE_AMZN_HVM_IMAGE_NAME,
                sshUserName: "ec2-user"
              ]
            ]
          ]
        ]
      ]
      RoscoAWSConfiguration.AWSBakeryDefaults localAwsBakeryDefaults = new ObjectMapper().convertValue(awsBakeryDefaultsJson, RoscoAWSConfiguration.AWSBakeryDefaults)

      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
        package_name: PACKAGES_NAME,
        base_os: "amzn",
        vm_type: BakeRequest.VmType.hvm,
        cloud_provider_type: BakeRequest.CloudProviderType.aws)
      def targetImageName = "kato-x8664-timestamp-amzn"
      def osPackages = parseRpmOsPackageNames(PACKAGES_NAME)

      def parameterMap = [
        aws_region: REGION,
        aws_ssh_username: "ec2-user",
        aws_instance_type: "t2.micro",
        aws_source_ami: SOURCE_AMZN_HVM_IMAGE_NAME,
        aws_target_ami: targetImageName,
        repository: YUM_REPOSITORY,
        package_type: RPM_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
        awsBakeryDefaults: localAwsBakeryDefaults,
        imageNameFactory: imageNameFactoryMock,
        packerCommandFactory: packerCommandFactoryMock,
        templatesNeedingRoot: [ "aws-chroot.json" ],
        yumRepository: YUM_REPOSITORY)

    when:
      awsBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, RPM_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(RPM_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("sudo", parameterMap, null, "$configDir/aws-chroot.json")
  }

  void 'produces packer command with all required parameters for ubuntu, using default vm type, and overriding base ami'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        base_ami: "ami-12345678",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def parameterMap = [
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "t2.micro",
        aws_source_ami: "ami-12345678",
        aws_target_ami: targetImageName,
        aws_spot_price: "auto",
        aws_spot_price_auto_product: "Linux/UNIX (Amazon VPC)",
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$awsBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for ubuntu, using explicit vm type'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        vm_type: BakeRequest.VmType.pv,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def parameterMap = [
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "m3.medium",
        aws_source_ami: SOURCE_UBUNTU_PV_IMAGE_NAME,
        aws_target_ami: targetImageName,
        aws_spot_price: "auto",
        aws_spot_price_auto_product: "Linux/UNIX (Amazon VPC)",
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$awsBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for ubuntu, using explicit vm type, and overriding template filename'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        vm_type: BakeRequest.VmType.pv,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                        template_file_name: "somePackerTemplate.json")
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def parameterMap = [
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "m3.medium",
        aws_source_ami: SOURCE_UBUNTU_PV_IMAGE_NAME,
        aws_target_ami: targetImageName,
        aws_spot_price: "auto",
        aws_spot_price_auto_product: "Linux/UNIX (Amazon VPC)",
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/somePackerTemplate.json")
  }

  void 'produces packer command with all required parameters for ubuntu, using explicit vm type, and adding extended attributes'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        vm_type: BakeRequest.VmType.pv,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                        extended_attributes: [someAttr1: "someValue1", someAttr2: "someValue2"])
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def parameterMap = [
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "m3.medium",
        aws_source_ami: SOURCE_UBUNTU_PV_IMAGE_NAME,
        aws_target_ami: targetImageName,
        aws_spot_price: "auto",
        aws_spot_price_auto_product: "Linux/UNIX (Amazon VPC)",
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        someAttr1: "someValue1",
        someAttr2: "someValue2"
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$awsBakeryDefaults.templateFile")
  }

  void 'sends spot_price_auto_product iff spot_price is set to auto'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest()

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      def virtualizationSettings = [
              region: "us-east-1",
              spotPrice: "0",
              spotPriceAutoProduct: "Linux/UNIX (Amazon VPC)",
      ]
      def parameterMap = awsBakeHandler.buildParameterMap(REGION, virtualizationSettings, "", bakeRequest, "")

    then:
      parameterMap.aws_spot_price == "0"
      !parameterMap.containsValue("aws_spot_price_auto_product")

    when:
      virtualizationSettings = [
              region: "us-east-1",
              spotPrice: "auto",
              spotPriceAutoProduct: "Linux/UNIX (Amazon VPC)",
      ]
      parameterMap = awsBakeHandler.buildParameterMap(REGION, virtualizationSettings, "", bakeRequest, "")

    then:
      parameterMap.aws_spot_price == "auto"
      parameterMap.aws_spot_price_auto_product == "Linux/UNIX (Amazon VPC)"
  }
  void 'overrides repository for images with custom repository property'() {
    setup:
    def imageNameFactoryMock = Mock(ImageNameFactory)
    def packerCommandFactoryMock = Mock(PackerCommandFactory)
    def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
            package_name: PACKAGES_NAME,
            base_os: "trusty_custom_repo",
            vm_type: BakeRequest.VmType.hvm,
            cloud_provider_type: BakeRequest.CloudProviderType.aws)
    def targetImageName = "kato-x8664-timestamp-trusty"
    def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
    def parameterMap = [
            aws_region: REGION,
            aws_ssh_username: "ubuntu",
            aws_instance_type: "t2.micro",
            aws_source_ami: SOURCE_TRUSTY_HVM_IMAGE_NAME,
            aws_target_ami: targetImageName,
            aws_spot_price: "auto",
            aws_spot_price_auto_product: "Linux/UNIX (Amazon VPC)",
            repository: DEBIAN_CUSTOM_REPOSITORY,
            package_type: DEB_PACKAGE_TYPE.util.packageType,
            packages: PACKAGES_NAME,
            configDir: configDir
    ]

    @Subject
    AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
            awsBakeryDefaults: awsBakeryDefaults,
            imageNameFactory: imageNameFactoryMock,
            packerCommandFactory: packerCommandFactoryMock,
            debianRepository: DEBIAN_REPOSITORY)

    when:
    awsBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
    1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
    1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
    1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
    1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$awsBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for trusty, using explicit vm type'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "trusty",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)
      def targetImageName = "kato-x8664-timestamp-trusty"
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def parameterMap = [
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "t2.micro",
        aws_source_ami: SOURCE_TRUSTY_HVM_IMAGE_NAME,
        aws_target_ami: targetImageName,
        aws_spot_price: "auto",
        aws_spot_price_auto_product: "Linux/UNIX (Amazon VPC)",
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$awsBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters including appversion, build_host and build_info_url for trusty'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def fullyQualifiedPackageName = "nflx-djangobase-enhanced_0.1-h12.170cdbd_all"
      def appVersionStr = "nflx-djangobase-enhanced-0.1-170cdbd.h12"
      def buildHost = "http://some-build-server:8080"
      def buildInfoUrl = "http://some-build-server:8080/repogroup/repo/builds/320282"
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: fullyQualifiedPackageName,
                                        base_os: "trusty",
                                        vm_type: BakeRequest.VmType.hvm,
                                        build_host: buildHost,
                                        build_info_url: buildInfoUrl,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)
      def targetImageName = "kato-x8664-timestamp-trusty"
      def osPackages = [PackageNameConverter.buildOsPackageName(DEB_PACKAGE_TYPE, fullyQualifiedPackageName)]
      def parameterMap = [
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "t2.micro",
        aws_source_ami: SOURCE_TRUSTY_HVM_IMAGE_NAME,
        aws_target_ami: targetImageName,
        aws_spot_price: "auto",
        aws_spot_price_auto_product: "Linux/UNIX (Amazon VPC)",
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: fullyQualifiedPackageName,
        configDir: configDir,
        appversion: appVersionStr,
        build_host: buildHost,
        build_info_url: buildInfoUrl
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> appVersionStr
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> fullyQualifiedPackageName
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$awsBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters including upgrade'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                        upgrade: true)
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def parameterMap = [
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "t2.micro",
        aws_source_ami: SOURCE_UBUNTU_HVM_IMAGE_NAME,
        aws_target_ami: targetImageName,
        aws_spot_price: "auto",
        aws_spot_price_auto_product: "Linux/UNIX (Amazon VPC)",
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        packages: PACKAGES_NAME,
        upgrade: true,
        configDir: configDir
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$awsBakeryDefaults.templateFile")
  }

  void 'produces packer (Windows) command with all required parameters'() {
    setup:
    def imageNameFactoryMock = Mock(ImageNameFactory)
    def packerCommandFactoryMock = Mock(PackerCommandFactory)
    def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
      package_name: NUPKG_PACKAGES_NAME,
      base_os: "windows-2012-r2",
      vm_type: BakeRequest.VmType.hvm,
      cloud_provider_type: BakeRequest.CloudProviderType.aws)
    def targetImageName = "googlechrome-all-20170121212839-windows-2012-r2"
    def osPackages = parseNupkgOsPackageNames(NUPKG_PACKAGES_NAME)
    def parameterMap = [
      aws_region: REGION,
      aws_winrm_username: "Administrator",
      aws_instance_type: "t2.micro",
      aws_source_ami: SOURCE_WINDOWS_2012_R2_HVM_IMAGE_NAME,
      aws_target_ami: targetImageName,
      aws_spot_price: "auto",
      aws_spot_price_auto_product: "Windows (Amazon VPC)",
      repository: CHOCOLATEY_REPOSITORY,
      package_type: NUPKG_PACKAGE_TYPE.util.packageType,
      packages: NUPKG_PACKAGES_NAME,
      configDir: configDir
    ]
    def operatingSystemVirtualizationSettings = awsBakeryDefaults.baseImages.find { it?.baseImage?.id == bakeRequest.base_os}

    @Subject
    AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
      awsBakeryDefaults: awsBakeryDefaults,
      imageNameFactory: imageNameFactoryMock,
      packerCommandFactory: packerCommandFactoryMock,
      chocolateyRepository: CHOCOLATEY_REPOSITORY)

    when:
    awsBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
    1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
    1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, NUPKG_PACKAGE_TYPE) >> null
    1 * imageNameFactoryMock.buildPackagesParameter(NUPKG_PACKAGE_TYPE, osPackages) >> NUPKG_PACKAGES_NAME
    1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$operatingSystemVirtualizationSettings.baseImage.templateFile")
  }

  void 'throws exception when virtualization settings are not found for specified operating system'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      IllegalArgumentException e = thrown()
      e.message == "No virtualization settings found for 'centos'."
  }

  void 'throws exception when virtualization settings are not found for specified region, operating system, and vm type'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "trusty",
                                        vm_type: BakeRequest.VmType.pv,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      IllegalArgumentException e = thrown()
      e.message == "No virtualization settings found for region 'us-east-1', operating system 'trusty', and vm type 'pv'."
  }

  void 'produce a default AWS bakeKey without base ami'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      String bakeKey = awsBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:aws:centos:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:us-east-1:hvm:enhancedNWDisabled"
  }

  @Unroll
  void 'produce a default AWS bakeKey without base ami, even when no packages are specified'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: packageName,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      String bakeKey = awsBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:aws:centos::us-east-1:hvm:enhancedNWDisabled"

    where:
      packageName << [null, ""]
  }

  void 'produce a default AWS bakeKey with base ami'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                        base_ami: "ami-123456")

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      String bakeKey = awsBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:aws:centos:ami-123456:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:us-east-1:hvm:enhancedNWDisabled"
  }

  void 'produce a default AWS bakeKey with enhanced network enabled'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                        enhanced_networking: true)

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      String bakeKey = awsBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:aws:centos:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:us-east-1:hvm:enhancedNWEnabled"
  }

  void 'produce a default AWS bakeKey with ami name'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                        ami_name: "kato-app")
      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      String bakeKey = awsBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:aws:centos:kato-app:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:us-east-1:hvm:enhancedNWDisabled"
  }

  void 'do not consider ami suffix when composing bake key'() {
    setup:
      def bakeRequest1 = new BakeRequest(user: "someuser@gmail.com",
                                         package_name: PACKAGES_NAME,
                                         base_os: "centos",
                                         vm_type: BakeRequest.VmType.hvm,
                                         cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                         ami_suffix: "1.0")
      def bakeRequest2 = new BakeRequest(user: "someuser@gmail.com",
                                         package_name: PACKAGES_NAME,
                                         base_os: "centos",
                                         vm_type: BakeRequest.VmType.hvm,
                                         cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                         ami_suffix: "2.0")
      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      String bakeKey1 = awsBakeHandler.produceBakeKey(REGION, bakeRequest1)
      String bakeKey2 = awsBakeHandler.produceBakeKey(REGION, bakeRequest2)

    then:
      bakeKey1 == "bake:aws:centos:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:us-east-1:hvm:enhancedNWDisabled"
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
                                        vm_type: BakeRequest.VmType.hvm,
                                        build_host: buildHost,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                        extended_attributes: [share_with: share_account],
                                        build_info_url: buildInfoUrl)
      def osPackages = parseDebOsPackageNames(fullyQualifiedPackageName)
      def targetImageName = "kato-x8664-timestamp-trusty"
      def parameterMap = [
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "t2.micro",
        aws_source_ami: SOURCE_TRUSTY_HVM_IMAGE_NAME,
        aws_target_ami: targetImageName,
        aws_spot_price: "auto",
        aws_spot_price_auto_product: "Linux/UNIX (Amazon VPC)",
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
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY,
                                                         yumRepository: YUM_REPOSITORY)

    when:
      awsBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> appVersionStr
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> fullyQualifiedPackageName
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$awsBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters including copy_to multiple regions as extended_attribute'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def fullyQualifiedPackageName = "nflx-djangobase-enhanced_0.1-h12.170cdbd_all"
      def appVersionStr = "nflx-djangobase-enhanced-0.1-170cdbd.h12"
      def buildHost = "http://some-build-server:8080"
      def buildInfoUrl = "http://some-build-server:8080/repogroup/repo/builds/320282"
      def copy_regions = "us-west-1, us-west-2"
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: fullyQualifiedPackageName,
                                        base_os: "trusty",
                                        vm_type: BakeRequest.VmType.hvm,
                                        build_host: buildHost,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                        extended_attributes: [copy_to: copy_regions],
                                        build_info_url: buildInfoUrl)
      def osPackages = parseDebOsPackageNames(fullyQualifiedPackageName)
      def targetImageName = "kato-x8664-timestamp-trusty"
      def parameterMap = [
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "t2.micro",
        aws_source_ami: SOURCE_TRUSTY_HVM_IMAGE_NAME,
        aws_target_ami: targetImageName,
        aws_spot_price: "auto",
        aws_spot_price_auto_product: "Linux/UNIX (Amazon VPC)",
        package_type: DEB_PACKAGE_TYPE.util.packageType,
        repository: DEBIAN_REPOSITORY,
        packages: fullyQualifiedPackageName,
        copy_to_1: "us-west-1",
        copy_to_2: "us-west-2",
        configDir: configDir,
        appversion: appVersionStr,
        build_host: buildHost,
        build_info_url: buildInfoUrl
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY,
                                                         yumRepository: YUM_REPOSITORY)

    when:
      awsBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> appVersionStr
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> fullyQualifiedPackageName
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$awsBakeryDefaults.templateFile")
  }

  static class NoSleepRetry extends RetrySupport {
    void sleep(long time) {}
  }
}
