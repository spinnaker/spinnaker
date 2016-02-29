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
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.aws.config.RoscoAWSConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AWSBakeHandlerSpec extends Specification {

  private static final String PACKAGE_NAME = "kato"
  private static final String REGION = "us-east-1"
  private static final String SOURCE_UBUNTU_HVM_IMAGE_NAME = "ami-a123456b"
  private static final String SOURCE_UBUNTU_PV_IMAGE_NAME = "ami-a654321b"
  private static final String SOURCE_TRUSTY_HVM_IMAGE_NAME = "ami-c456789d"
  private static final String SOURCE_AMZN_HVM_IMAGE_NAME = "ami-8fcee4e5"
  private static final String DEBIAN_REPOSITORY = "http://some-debian-repository"
  private static final String YUM_REPOSITORY = "http://some-yum-repository"

  @Shared
  String configDir = "/some/path"

  @Shared
  RoscoAWSConfiguration.AWSBakeryDefaults awsBakeryDefaults

  void setupSpec() {
    def awsBakeryDefaultsJson = [
      awsAccessKey: "FOO",
      awsSecretKey: "BAR",
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
              sshUserName: "ubuntu"
            ],
            [
              region: REGION,
              virtualizationType: "pv",
              instanceType: "m3.medium",
              sourceAmi: SOURCE_UBUNTU_PV_IMAGE_NAME,
              sshUserName: "ubuntu"
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
              sshUserName: "ubuntu"
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
              sshUserName: "ec2-user"
            ]
          ]
        ]
      ]
    ]

    awsBakeryDefaults = new ObjectMapper().convertValue(awsBakeryDefaultsJson, RoscoAWSConfiguration.AWSBakeryDefaults)
  }

  void 'can identify amazon-ebs builds'() {
    setup:
      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      def logsContent =
        "    amazon-ebs: Processing triggers for libc-bin ...\n"

      Boolean awsProducer = awsBakeHandler.isProducerOf(logsContent)

    then:
      awsProducer == true
  }

  void 'can identify amazon-chroot builds'() {
    setup:
      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      def logsContent =
        "    amazon-chroot: Processing triggers for libc-bin ...\n"

      Boolean awsProducer = awsBakeHandler.isProducerOf(logsContent)

    then:
      awsProducer == true
  }

  void 'rejects non amazon builds'() {
    setup:
      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      def logsContent =
        "    somesystem-thing: Processing triggers for libc-bin ...\n"

      Boolean awsProducer = awsBakeHandler.isProducerOf(logsContent)

    then:
      awsProducer == false
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

  void 'produces packer command with all required parameters for ubuntu, using default vm type'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def parameterMap = [
        aws_access_key: awsBakeryDefaults.awsAccessKey,
        aws_secret_key: awsBakeryDefaults.awsSecretKey,
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "t2.micro",
        aws_source_ami: SOURCE_UBUNTU_HVM_IMAGE_NAME,
        aws_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: BakeRequest.PackageType.DEB.packageType,
        packages: PACKAGE_NAME,
        configDir: configDir
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >> [targetImageName, null, PACKAGE_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$awsBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for amzn, using default vm type'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "amzn",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)
      def targetImageName = "kato-x8664-timestamp-amzn"
      def parameterMap = [
        aws_access_key: awsBakeryDefaults.awsAccessKey,
        aws_secret_key: awsBakeryDefaults.awsSecretKey,
        aws_region: REGION,
        aws_ssh_username: "ec2-user",
        aws_instance_type: "t2.micro",
        aws_source_ami: SOURCE_AMZN_HVM_IMAGE_NAME,
        aws_target_ami: targetImageName,
        repository: YUM_REPOSITORY,
        package_type: BakeRequest.PackageType.RPM.packageType,
        packages: PACKAGE_NAME,
        configDir: configDir
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         yumRepository: YUM_REPOSITORY)

    when:
      awsBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >> [targetImageName, null, PACKAGE_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$awsBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for ubuntu, using default vm type, and overriding base ami'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        base_ami: "ami-12345678",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def parameterMap = [
        aws_access_key: awsBakeryDefaults.awsAccessKey,
        aws_secret_key: awsBakeryDefaults.awsSecretKey,
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "t2.micro",
        aws_source_ami: "ami-12345678",
        aws_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: BakeRequest.PackageType.DEB.packageType,
        packages: PACKAGE_NAME,
        configDir: configDir
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >> [targetImageName, null, PACKAGE_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$awsBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for ubuntu, using explicit vm type'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        vm_type: BakeRequest.VmType.pv,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def parameterMap = [
        aws_access_key: awsBakeryDefaults.awsAccessKey,
        aws_secret_key: awsBakeryDefaults.awsSecretKey,
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "m3.medium",
        aws_source_ami: SOURCE_UBUNTU_PV_IMAGE_NAME,
        aws_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: BakeRequest.PackageType.DEB.packageType,
        packages: PACKAGE_NAME,
        configDir: configDir
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >> [targetImageName, null, PACKAGE_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$awsBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for ubuntu, using explicit vm type, and overriding template filename'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        vm_type: BakeRequest.VmType.pv,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                        template_file_name: "somePackerTemplate.json")
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def parameterMap = [
        aws_access_key: awsBakeryDefaults.awsAccessKey,
        aws_secret_key: awsBakeryDefaults.awsSecretKey,
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "m3.medium",
        aws_source_ami: SOURCE_UBUNTU_PV_IMAGE_NAME,
        aws_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: BakeRequest.PackageType.DEB.packageType,
        packages: PACKAGE_NAME,
        configDir: configDir
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >> [targetImageName, null, PACKAGE_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/somePackerTemplate.json")
  }

  void 'produces packer command with all required parameters for ubuntu, using explicit vm type, and adding extended attributes'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        vm_type: BakeRequest.VmType.pv,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                        extended_attributes: [someAttr1: "someValue1", someAttr2: "someValue2"])
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def parameterMap = [
        aws_access_key: awsBakeryDefaults.awsAccessKey,
        aws_secret_key: awsBakeryDefaults.awsSecretKey,
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "m3.medium",
        aws_source_ami: SOURCE_UBUNTU_PV_IMAGE_NAME,
        aws_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: BakeRequest.PackageType.DEB.packageType,
        packages: PACKAGE_NAME,
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
      awsBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >> [targetImageName, null, PACKAGE_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$awsBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for trusty, using explicit vm type'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "trusty",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)
      def targetImageName = "kato-x8664-timestamp-trusty"
      def parameterMap = [
        aws_access_key: awsBakeryDefaults.awsAccessKey,
        aws_secret_key: awsBakeryDefaults.awsSecretKey,
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "t2.micro",
        aws_source_ami: SOURCE_TRUSTY_HVM_IMAGE_NAME,
        aws_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: BakeRequest.PackageType.DEB.packageType,
        packages: PACKAGE_NAME,
        configDir: configDir
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >> [targetImageName, null, PACKAGE_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$awsBakeryDefaults.templateFile")
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
                                        vm_type: BakeRequest.VmType.hvm,
                                        build_host: buildHost,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)
      def targetImageName = "kato-x8664-timestamp-trusty"
      def parameterMap = [
        aws_access_key: awsBakeryDefaults.awsAccessKey,
        aws_secret_key: awsBakeryDefaults.awsSecretKey,
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "t2.micro",
        aws_source_ami: SOURCE_TRUSTY_HVM_IMAGE_NAME,
        aws_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: BakeRequest.PackageType.DEB.packageType,
        packages: fullyQualifiedPackageName,
        configDir: configDir,
        appversion: appVersionStr,
        build_host: buildHost
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(configDir: configDir,
                                                         awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >>
        [targetImageName, appVersionStr, fullyQualifiedPackageName]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$awsBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters including upgrade'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                        upgrade: true)
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def parameterMap = [
        aws_access_key: awsBakeryDefaults.awsAccessKey,
        aws_secret_key: awsBakeryDefaults.awsSecretKey,
        aws_region: REGION,
        aws_ssh_username: "ubuntu",
        aws_instance_type: "t2.micro",
        aws_source_ami: SOURCE_UBUNTU_HVM_IMAGE_NAME,
        aws_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: BakeRequest.PackageType.DEB.packageType,
        packages: PACKAGE_NAME,
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
      awsBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >> [targetImageName, null, PACKAGE_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$awsBakeryDefaults.templateFile")
  }

  void 'throws exception when virtualization settings are not found for specified operating system'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      IllegalArgumentException e = thrown()
      e.message == "No virtualization settings found for 'centos'."
  }

  void 'throws exception when virtualization settings are not found for specified region, operating system, and vm type'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "trusty",
                                        vm_type: BakeRequest.VmType.pv,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      awsBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      IllegalArgumentException e = thrown()
      e.message == "No virtualization settings found for region 'us-east-1', operating system 'trusty', and vm type 'pv'."
  }

  void 'produce a default AWS bakeKey without base ami'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      String bakeKey = awsBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:aws:centos:kato:us-east-1:hvm:enhancedNWDisabled"
  }

  void 'produce a default AWS bakeKey with base ami'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                        base_ami: "ami-123456")

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      String bakeKey = awsBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:aws:centos:ami-123456:kato:us-east-1:hvm:enhancedNWDisabled"
  }

  void 'produce a default AWS bakeKey with enhanced network enabled'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                        enhanced_networking: true)

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      String bakeKey = awsBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:aws:centos:kato:us-east-1:hvm:enhancedNWEnabled"
  }

  void 'produce a default AWS bakeKey with ami name'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                        ami_name: "kato-app")
      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      String bakeKey = awsBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:aws:centos:kato-app:us-east-1:hvm:enhancedNWDisabled"
  }

  void 'produce a default AWS bakeKey with ami suffix'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                        ami_suffix: "1.0")
      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      String bakeKey = awsBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:aws:centos:kato:1.0:us-east-1:hvm:enhancedNWDisabled"
  }

  void 'produce a default AWS bakeKey with ami name and suffix'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws,
                                        ami_name: "kato-app",
                                        ami_suffix: "1.0")
      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults)

    when:
      String bakeKey = awsBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:aws:centos:kato-app:1.0:us-east-1:hvm:enhancedNWDisabled"
  }
}
