/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.rosco.providers.openstack

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.config.RoscoConfiguration
import com.netflix.spinnaker.rosco.providers.openstack.config.RoscoOpenstackConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class OpenstackBakeHandlerSpec extends Specification implements TestDefaults {

  private static final String REGION = "RegionOne"
  private static final String SOURCE_UBUNTU_IMAGE_NAME = "ubuntu-latest"
  private static final String SOURCE_TRUSTY_IMAGE_NAME = "ubuntu-trusty"
  private static final String SOURCE_OPENSTACK_IMAGE_NAME = "rhel7-latest"
  private static final String AUTH_URL = "http://my.openstack.com:5000/v3"

  @Shared
  String configDir = "/some/path"

  @Shared
  RoscoOpenstackConfiguration.OpenstackBakeryDefaults openstackBakeryDefaults

  @Shared
  RoscoConfiguration roscoConfiguration

  void setupSpec() {
    def openstackBakeryDefaultsJson = [
      authUrl: AUTH_URL,
      username: 'foo',
      password: 'bar',
      domainName: 'Default',
      insecure: 'false',
      networkId: '6d0b4799-8784-4f0a-bc16-9b7e783cd803',
      floatingIpPool: 'ippool',
      securityGroups: 'default',
      projectName: 'project1',
      templateFile: "openstack_template.json",
      baseImages: [
        [
          baseImage: [
            id: "ubuntu",
            packageType: "DEB",
          ],
          virtualizationSettings: [
            [
              region: REGION,
              instanceType: "smem-2vcpu",
              sourceImageId: SOURCE_UBUNTU_IMAGE_NAME,
              sshUserName: "ubuntu"
            ],
            [
              region: REGION,
              instanceType: "mmem-6vcpu",
              sourceImageId: SOURCE_UBUNTU_IMAGE_NAME,
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
              instanceType: "smem-2vcpu",
              sourceImageId: SOURCE_TRUSTY_IMAGE_NAME,
              sshUserName: "ubuntu"
            ]
          ]
        ],
        [
          baseImage: [
            id: "openstack",
            packageType: "RPM",
          ],
          virtualizationSettings: [
            [
              region: REGION,
              instanceType: "smem-2vcpu",
              sourceImageId: SOURCE_OPENSTACK_IMAGE_NAME,
              sshUserName: "cloud-user"
            ]
          ]
        ]
      ]
    ]

    openstackBakeryDefaults = new ObjectMapper().convertValue(openstackBakeryDefaultsJson, RoscoOpenstackConfiguration.OpenstackBakeryDefaults)
  }

  void 'can identify openstack builds'() {
    setup:
      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      def logsContent =
        "    openstack: Discovering enabled extensions...\n"

      Boolean producer = openstackBakeHandler.isProducerOf(logsContent)

    then:
      producer
  }

  void 'rejects non openstack builds'() {
    setup:
      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      def logsContent =
        "    somesystem-thing: doing the harlem shake ...\n"

      Boolean openstackProducer = openstackBakeHandler.isProducerOf(logsContent)

    then:
      !openstackProducer
  }

  void 'can scrape packer logs for image name'() {
    setup:
      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      def logsContent = """
        |==> openstack: Discovering enabled extensions...
        |==> openstack: Loading flavor: smem-2vcpu
        |    openstack: Verified flavor. ID: 89a74515-7072-49f8-87ab-28d3f70de59d
        |==> openstack: Creating temporary keypair: packer 573d433f-278f-3e22-d2ce-e5720f906cf8 ...
        |==> openstack: Created temporary keypair: packer 573d433f-278f-3e22-d2ce-e5720f906cf8
        |==> openstack: Launching server...
        |    openstack: Server ID: 732520f1-af79-45be-bcda-ea1089e80f0d
        |==> openstack: Waiting for server to become ready...
        |==> openstack: Creating floating IP...
        |    openstack: Pool: ext_vlan1472_net
        |    openstack: Created floating IP: 1.2.3.4
        |==> openstack: Associating floating IP with server...
        |    openstack: IP: 1.2.3.4
        |    openstack: Added floating IP 1.2.3.4 to instance!
        |==> openstack: Waiting for SSH to become available...
        |==> openstack: Connected to SSH!
        |==> openstack: Pausing 30s before the next provisioner...
        |==> openstack: Provisioning with shell script: rosco-web/config/packer/install_packages.sh
        |    openstack: repository=
        |    openstack: package_type=
        |    openstack: packages=
        |    openstack: upgrade=
        |==> openstack: Stopping server: 732520f1-af79-45be-bcda-ea1089e80f0d ...
        |    openstack: Waiting for server to stop: 732520f1-af79-45be-bcda-ea1089e80f0d ...
        |==> openstack: Creating the image: dan-packer-test-3
        |    openstack: Image: 1f28b46b-b36f-4b7c-bc34-40e2371886fa
        |==> openstack: Waiting for image dan-packer-test-3 (image id: 1f28b46b-b36f-4b7c-bc34-40e2371886fa) to become ready...
        |==> openstack: Terminating the source server: 732520f1-af79-45be-bcda-ea1089e80f0d ...
        |==> openstack: Deleting temporary keypair: packer 573d433f-278f-3e22-d2ce-e5720f906cf8 ...
        |Build 'openstack' finished.
        |
        |==> Builds finished. The artifacts of successful builds are:
        |--> openstack: An image was created: 1f28b46b-b36f-4b7c-bc34-40e2371886fa
      """.stripMargin()

      Bake bake = openstackBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

    then:
      with(bake) {
        id == "123"
        !ami
        image_name == "1f28b46b-b36f-4b7c-bc34-40e2371886fa"
      }
  }

  void 'scraping returns null for missing image name'() {
    setup:
      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      def logsContent = """
        |==> openstack: Discovering enabled extensions...
        |==> openstack: Loading flavor: smem-2vcpu
        |    openstack: Verified flavor. ID: 89a74515-7072-49f8-87ab-28d3f70de59d
        |==> openstack: Creating temporary keypair: packer 573d433f-278f-3e22-d2ce-e5720f906cf8 ...
        |==> openstack: Created temporary keypair: packer 573d433f-278f-3e22-d2ce-e5720f906cf8
        |==> openstack: Launching server...
        |    openstack: Server ID: 732520f1-af79-45be-bcda-ea1089e80f0d
        |==> openstack: Waiting for server to become ready...
        |==> openstack: Creating floating IP...
        |    openstack: Pool: ext_vlan1472_net
        |    openstack: Created floating IP: 1.2.3.4
        |==> openstack: Associating floating IP with server...
        |    openstack: IP: 1.2.3.4
        |    openstack: Added floating IP 1.2.3.4 to instance!
        |==> openstack: Waiting for SSH to become available...
        |==> openstack: Connected to SSH!
        |==> openstack: Pausing 30s before the next provisioner...
        |==> openstack: Provisioning with shell script: rosco-web/config/packer/install_packages.sh
        |    openstack: repository=
        |    openstack: package_type=
        |    openstack: packages=
        |    openstack: upgrade=
        |==> openstack: Stopping server: 732520f1-af79-45be-bcda-ea1089e80f0d ...
        |    openstack: Waiting for server to stop: 732520f1-af79-45be-bcda-ea1089e80f0d ...
        |==> openstack: Creating the image: dan-packer-test-3
        |    openstack: Image: 1f28b46b-b36f-4b7c-bc34-40e2371886fa
        |==> openstack: Waiting for image dan-packer-test-3 (image id: 1f28b46b-b36f-4b7c-bc34-40e2371886fa) to become ready...
        |==> openstack: Terminating the source server: 732520f1-af79-45be-bcda-ea1089e80f0d ...
        |==> openstack: Deleting temporary keypair: packer 573d433f-278f-3e22-d2ce-e5720f906cf8 ...
        |Build 'openstack' finished.
        |
        |==> Builds finished. The artifacts of successful builds are:
      """.stripMargin()

      Bake bake = openstackBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

    then:
      with(bake) {
        id == "123"
        !ami
        !image_name
      }
  }

  void 'produces packer command with all required parameters'() {
    setup:
      String instanceType = openstackBakeryDefaults.baseImages[0].virtualizationSettings[0].instanceType
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: 'someuser@gmail.com',
        package_name: PACKAGES_NAME,
        base_os: 'ubuntu',
        cloud_provider_type: BakeRequest.CloudProviderType.openstack)
      def targetImageName = '1f28b46b-b36f-4b7c-bc34-40e2371886fa'
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def parameterMap = [
        openstack_auth_url: AUTH_URL,
        openstack_region: REGION,
        openstack_ssh_username: 'ubuntu',
        openstack_instance_type: instanceType,
        openstack_source_image_id: SOURCE_UBUNTU_IMAGE_NAME,
        openstack_image_name: targetImageName,
        openstack_username: openstackBakeryDefaults.username,
        openstack_password: openstackBakeryDefaults.password,
        openstack_domain_name: openstackBakeryDefaults.domainName,
        openstack_insecure: openstackBakeryDefaults.insecure,
        openstack_network_id: openstackBakeryDefaults.networkId,
        openstack_floating_ip_pool: openstackBakeryDefaults.floatingIpPool,
        openstack_security_groups: openstackBakeryDefaults.securityGroups,
        openstack_project_name: openstackBakeryDefaults.projectName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(configDir: configDir,
        openstackBakeryDefaults: openstackBakeryDefaults,
        imageNameFactory: imageNameFactoryMock,
        packerCommandFactory: packerCommandFactoryMock,
        debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$openstackBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters including appversion, build_host and build_info_url for trusty'() {
    setup:
    String instanceType = openstackBakeryDefaults.baseImages[0].virtualizationSettings[0].instanceType
    def imageNameFactoryMock = Mock(ImageNameFactory)
    def packerCommandFactoryMock = Mock(PackerCommandFactory)
    def appVersionStr = "nflx-djangobase-enhanced-0.1-170cdbd.h12"
    def buildHost = "http://some-build-server:8080"
    def buildInfoUrl = "http://some-build-server:8080/repogroup/repo/builds/320282"
    def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
            package_name: PACKAGES_NAME,
            base_os: "ubuntu",
            build_host: buildHost,
            build_info_url: buildInfoUrl,
            cloud_provider_type: BakeRequest.CloudProviderType.openstack)
    def targetImageName = "1f28b46b-b36f-4b7c-bc34-40e2371886fa"
    def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
    def parameterMap = [
            openstack_auth_url: AUTH_URL,
            openstack_region: REGION,
            openstack_ssh_username: "ubuntu",
            openstack_instance_type: instanceType,
            openstack_source_image_id: SOURCE_UBUNTU_IMAGE_NAME,
            openstack_image_name: targetImageName,
            openstack_username: openstackBakeryDefaults.username,
            openstack_password: openstackBakeryDefaults.password,
            openstack_domain_name: openstackBakeryDefaults.domainName,
            openstack_insecure: openstackBakeryDefaults.insecure,
            openstack_floating_ip_pool: openstackBakeryDefaults.floatingIpPool,
            openstack_network_id: openstackBakeryDefaults.networkId,
            openstack_security_groups: openstackBakeryDefaults.securityGroups,
            openstack_project_name: openstackBakeryDefaults.projectName,
            repository: DEBIAN_REPOSITORY,
            package_type: BakeRequest.PackageType.DEB.packageType,
            packages: PACKAGES_NAME,
            configDir: configDir,
            appversion: appVersionStr,
            build_host: buildHost,
            build_info_url: buildInfoUrl
    ]

    @Subject
    OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(configDir: configDir,
            openstackBakeryDefaults: openstackBakeryDefaults,
            imageNameFactory: imageNameFactoryMock,
            packerCommandFactory: packerCommandFactoryMock,
            debianRepository: DEBIAN_REPOSITORY)

    when:
    openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
    1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
    1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages) >> appVersionStr
    1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
    1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$openstackBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters, overriding base ami'() {
    setup:
      String instanceType = openstackBakeryDefaults.baseImages[0].virtualizationSettings[0].instanceType
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
        package_name: PACKAGES_NAME,
        base_os: "ubuntu",
        base_ami: "ubuntu-natty",
        cloud_provider_type: BakeRequest.CloudProviderType.openstack)
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def targetImageName = "1f28b46b-b36f-4b7c-bc34-40e2371886fa"
      def parameterMap = [
        openstack_auth_url: AUTH_URL,
        openstack_region: REGION,
        openstack_ssh_username: "ubuntu",
        openstack_instance_type: instanceType,
        openstack_source_image_id: "ubuntu-natty",
        openstack_image_name: targetImageName,
        openstack_username: openstackBakeryDefaults.username,
        openstack_password: openstackBakeryDefaults.password,
        openstack_domain_name: openstackBakeryDefaults.domainName,
        openstack_insecure: openstackBakeryDefaults.insecure,
        openstack_floating_ip_pool: openstackBakeryDefaults.floatingIpPool,
        openstack_network_id: openstackBakeryDefaults.networkId,
        openstack_security_groups: openstackBakeryDefaults.securityGroups,
        openstack_project_name: openstackBakeryDefaults.projectName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(configDir: configDir,
        openstackBakeryDefaults: openstackBakeryDefaults,
        imageNameFactory: imageNameFactoryMock,
        packerCommandFactory: packerCommandFactoryMock,
        debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$openstackBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters, overriding template filename'() {
    setup:
      String template_file_name = "somePackerTemplate.json"
      String instanceType = openstackBakeryDefaults.baseImages[0].virtualizationSettings[0].instanceType
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
        package_name: PACKAGES_NAME,
        base_os: "ubuntu",
        cloud_provider_type: BakeRequest.CloudProviderType.openstack,
        template_file_name: template_file_name)
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def targetImageName = "1f28b46b-b36f-4b7c-bc34-40e2371886fa"
      def parameterMap = [
        openstack_auth_url: AUTH_URL,
        openstack_region: REGION,
        openstack_ssh_username: "ubuntu",
        openstack_instance_type: instanceType,
        openstack_source_image_id: SOURCE_UBUNTU_IMAGE_NAME,
        openstack_image_name: targetImageName,
        openstack_username: openstackBakeryDefaults.username,
        openstack_password: openstackBakeryDefaults.password,
        openstack_domain_name: openstackBakeryDefaults.domainName,
        openstack_insecure: openstackBakeryDefaults.insecure,
        openstack_floating_ip_pool: openstackBakeryDefaults.floatingIpPool,
        openstack_network_id: openstackBakeryDefaults.networkId,
        openstack_security_groups: openstackBakeryDefaults.securityGroups,
        openstack_project_name: openstackBakeryDefaults.projectName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(configDir: configDir,
        openstackBakeryDefaults: openstackBakeryDefaults,
        imageNameFactory: imageNameFactoryMock,
        packerCommandFactory: packerCommandFactoryMock,
        debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$template_file_name")
  }

  void 'produces packer command with all required parameters, adding extended attributes'() {
    setup:
      String instanceType = openstackBakeryDefaults.baseImages[0].virtualizationSettings[0].instanceType
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
        package_name: PACKAGES_NAME,
        base_os: "ubuntu",
        cloud_provider_type: BakeRequest.CloudProviderType.openstack,
        extended_attributes: [someAttr1: "someValue1", someAttr2: "someValue2"])
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def targetImageName = "1f28b46b-b36f-4b7c-bc34-40e2371886fa"
      def parameterMap = [
        openstack_auth_url: AUTH_URL,
        openstack_region: REGION,
        openstack_ssh_username: "ubuntu",
        openstack_instance_type: instanceType,
        openstack_source_image_id: SOURCE_UBUNTU_IMAGE_NAME,
        openstack_image_name: targetImageName,
        openstack_username: openstackBakeryDefaults.username,
        openstack_password: openstackBakeryDefaults.password,
        openstack_domain_name: openstackBakeryDefaults.domainName,
        openstack_insecure: openstackBakeryDefaults.insecure,
        openstack_floating_ip_pool: openstackBakeryDefaults.floatingIpPool,
        openstack_network_id: openstackBakeryDefaults.networkId,
        openstack_security_groups: openstackBakeryDefaults.securityGroups,
        openstack_project_name: openstackBakeryDefaults.projectName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        someAttr1: "someValue1",
        someAttr2: "someValue2"
      ]

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(configDir: configDir,
        openstackBakeryDefaults: openstackBakeryDefaults,
        imageNameFactory: imageNameFactoryMock,
        packerCommandFactory: packerCommandFactoryMock,
        debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$openstackBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters, and overrides native attributes via extended attributes'() {
    setup:
      String instanceType = openstackBakeryDefaults.baseImages[0].virtualizationSettings[0].instanceType
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
        package_name: PACKAGES_NAME,
        base_os: "ubuntu",
        cloud_provider_type: BakeRequest.CloudProviderType.openstack,
        extended_attributes: [openstack_instance_type: "mmem-6vcpu", openstack_domain_name: "domain2"])
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def targetImageName = "1f28b46b-b36f-4b7c-bc34-40e2371886fa"
      def parameterMap = [
        openstack_auth_url: AUTH_URL,
        openstack_region: REGION,
        openstack_ssh_username: "ubuntu",
        openstack_instance_type: "mmem-6vcpu",
        openstack_source_image_id: SOURCE_UBUNTU_IMAGE_NAME,
        openstack_image_name: targetImageName,
        openstack_username: openstackBakeryDefaults.username,
        openstack_password: openstackBakeryDefaults.password,
        openstack_domain_name: 'domain2',
        openstack_insecure: openstackBakeryDefaults.insecure,
        openstack_floating_ip_pool: openstackBakeryDefaults.floatingIpPool,
        openstack_network_id: openstackBakeryDefaults.networkId,
        openstack_security_groups: openstackBakeryDefaults.securityGroups,
        openstack_project_name: openstackBakeryDefaults.projectName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(configDir: configDir,
        openstackBakeryDefaults: openstackBakeryDefaults,
        imageNameFactory: imageNameFactoryMock,
        packerCommandFactory: packerCommandFactoryMock,
        debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$openstackBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters including upgrade'() {
    setup:
      String instanceType = openstackBakeryDefaults.baseImages[0].virtualizationSettings[0].instanceType
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
        package_name: PACKAGES_NAME,
        base_os: "ubuntu",
        cloud_provider_type: BakeRequest.CloudProviderType.openstack,
        upgrade: true)
      def osPackages = parseDebOsPackageNames(PACKAGES_NAME)
      def targetImageName = "1f28b46b-b36f-4b7c-bc34-40e2371886fa"
      def parameterMap = [
        openstack_auth_url: AUTH_URL,
        openstack_region: REGION,
        openstack_ssh_username: "ubuntu",
        openstack_instance_type: instanceType,
        openstack_source_image_id: SOURCE_UBUNTU_IMAGE_NAME,
        openstack_image_name: targetImageName,
        openstack_username: openstackBakeryDefaults.username,
        openstack_password: openstackBakeryDefaults.password,
        openstack_domain_name: openstackBakeryDefaults.domainName,
        openstack_insecure: openstackBakeryDefaults.insecure,
        openstack_floating_ip_pool: openstackBakeryDefaults.floatingIpPool,
        openstack_network_id: openstackBakeryDefaults.networkId,
        openstack_security_groups: openstackBakeryDefaults.securityGroups,
        openstack_project_name: openstackBakeryDefaults.projectName,
        repository: DEBIAN_REPOSITORY,
        package_type: DEB_PACKAGE_TYPE.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        upgrade: true
      ]

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(configDir: configDir,
        openstackBakeryDefaults: openstackBakeryDefaults,
        imageNameFactory: imageNameFactoryMock,
        packerCommandFactory: packerCommandFactoryMock,
        debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
      1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages) >> null
      1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> PACKAGES_NAME
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, null, "$configDir/$openstackBakeryDefaults.templateFile")
  }

  void 'throws exception when virtualization settings are not found for specified operating system'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
        package_name: PACKAGES_NAME,
        base_os: "centos",
        cloud_provider_type: BakeRequest.CloudProviderType.openstack)

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults,
        imageNameFactory: imageNameFactoryMock,
        packerCommandFactory: packerCommandFactoryMock,
        debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      IllegalArgumentException e = thrown()
      e.message == "No virtualization settings found for 'centos'."
  }

  void 'throws exception when virtualization settings are not found for specified region and operating system'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
        package_name: PACKAGES_NAME,
        base_os: "trusty",
        cloud_provider_type: BakeRequest.CloudProviderType.openstack)

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults,
        imageNameFactory: imageNameFactoryMock,
        packerCommandFactory: packerCommandFactoryMock,
        debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION + '999', bakeRequest)

    then:
      IllegalArgumentException e = thrown()
      e.message == "No virtualization settings found for region 'RegionOne999' and operating system 'trusty'."
  }

  void 'produce a default openstack bakeKey without base ami'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
        package_name: PACKAGES_NAME,
        base_os: "centos",
        cloud_provider_type: BakeRequest.CloudProviderType.openstack)

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      String bakeKey = openstackBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:openstack:centos:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:RegionOne"
  }

  @Unroll
  void 'produce a default openstack bakeKey without base ami, even when no packages are specified'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
        package_name: packageName,
        base_os: "centos",
        cloud_provider_type: BakeRequest.CloudProviderType.openstack)

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      String bakeKey = openstackBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:openstack:centos::RegionOne"

    where:
      packageName << [null, ""]
  }

  void 'produce a default openstack bakeKey with base ami'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
        package_name: PACKAGES_NAME,
        base_os: "centos",
        cloud_provider_type: BakeRequest.CloudProviderType.openstack,
        base_ami: "foobar")

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      String bakeKey = openstackBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:openstack:centos:foobar:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:RegionOne"
  }

  void 'produce a default openstack bakeKey with ami name'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
        package_name: PACKAGES_NAME,
        base_os: "centos",
        cloud_provider_type: BakeRequest.CloudProviderType.openstack,
        ami_name: "kato-app")
      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      String bakeKey = openstackBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:openstack:centos:kato-app:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:RegionOne"
  }

  void 'do not consider ami suffix when composing bake key'() {
    setup:
      def bakeRequest1 = new BakeRequest(user: "someuser@gmail.com",
        package_name: PACKAGES_NAME,
        base_os: "centos",
        vm_type: BakeRequest.VmType.hvm,
        cloud_provider_type: BakeRequest.CloudProviderType.openstack,
        ami_suffix: "1.0")
      def bakeRequest2 = new BakeRequest(user: "someuser@gmail.com",
        package_name: PACKAGES_NAME,
        base_os: "centos",
        cloud_provider_type: BakeRequest.CloudProviderType.openstack,
        ami_suffix: "2.0")
      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      String bakeKey1 = openstackBakeHandler.produceBakeKey(REGION, bakeRequest1)
      String bakeKey2 = openstackBakeHandler.produceBakeKey(REGION, bakeRequest2)

    then:
      bakeKey1 == "bake:openstack:centos:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:RegionOne"
      bakeKey2 == bakeKey1
  }
}