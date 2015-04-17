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

  @Shared
  RoscoAWSConfiguration.AWSBakeryDefaults awsBakeryDefaults

  void setupSpec() {
    def awsBakeryDefaultsJson = [
      awsAccessKey: "FOO",
      awsSecretKey: "BAR",
      templateFile: "aws_template.json",
      defaultVirtualizationType: "hvm",
      operatingSystemVirtualizationSettings: [
        [
          os: "ubuntu",
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
          os: "trusty",
          virtualizationSettings: [
            [
              region: REGION,
              virtualizationType: "hvm",
              instanceType: "t2.micro",
              sourceAmi: SOURCE_TRUSTY_HVM_IMAGE_NAME,
              sshUserName: "ubuntu"
            ]
          ]
        ]
      ]
    ]

    awsBakeryDefaults = new ObjectMapper().convertValue(awsBakeryDefaultsJson, RoscoAWSConfiguration.AWSBakeryDefaults)
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
                                        base_os: BakeRequest.OperatingSystem.ubuntu,
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
        packages: PACKAGE_NAME
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock)

    when:
      awsBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest) >> [targetImageName, null, PACKAGE_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, awsBakeryDefaults.templateFile)
  }

  void 'produces packer command with all required parameters for ubuntu, using explicit vm type'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.ubuntu,
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
        packages: PACKAGE_NAME
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock)

    when:
      awsBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest) >> [targetImageName, null, PACKAGE_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, awsBakeryDefaults.templateFile)
  }

  void 'produces packer command with all required parameters for trusty, using explicit vm type'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.trusty,
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
        packages: PACKAGE_NAME
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock)

    when:
      awsBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest) >> [targetImageName, null, PACKAGE_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, awsBakeryDefaults.templateFile)
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
        packages: fullyQualifiedPackageName,
        appversion: appVersionStr,
        build_host: buildHost
      ]

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock)

    when:
      awsBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest) >>
        [targetImageName, appVersionStr, fullyQualifiedPackageName]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, awsBakeryDefaults.templateFile)
  }

  void 'throws exception when virtualization settings are not found for specified operating system'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: BakeRequest.OperatingSystem.centos,
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock)

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
                                        base_os: BakeRequest.OperatingSystem.trusty,
                                        vm_type: BakeRequest.VmType.pv,
                                        cloud_provider_type: BakeRequest.CloudProviderType.aws)

      @Subject
      AWSBakeHandler awsBakeHandler = new AWSBakeHandler(awsBakeryDefaults: awsBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock)

    when:
      awsBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      IllegalArgumentException e = thrown()
      e.message == "No virtualization settings found for region 'us-east-1', operating system 'trusty', and vm type 'pv'."
  }

}
