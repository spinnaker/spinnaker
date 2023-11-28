/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.rosco.providers.oracle

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.oracle.config.OracleBakeryDefaults
import com.netflix.spinnaker.rosco.providers.oracle.config.OracleConfigurationProperties
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class OCIBakeHandlerSpec extends Specification implements TestDefaults {
  private static final String ACCOUNT1 = "account1"
  private static final String ACCOUNT2 = "account2"

  private static final String REGION = "some-region"
  private static final String CONFIG_DIR = "/some/path"

  @Shared
  OracleBakeryDefaults oracleBakeryDefaults

  @Shared
  OracleConfigurationProperties oracleConfigurationProperties

  void setupSpec() {
    def oracleBakeryDefaultsJson = [
      availabilityDomain: "lrFb:US-ASHBURN-AD-1",
      subnetId: "ocid1.subnet.oc1.iad.subnet",
      instanceShape: "VM.Standard1.1",
      templateFile: "oracle_template.json",
      baseImages: [
        [
          baseImage: [
            id: "ubuntu16_04",
            packageType: "DEB",
          ],
          virtualizationSettings: [
            baseImageId: "ocid1.image.oc1.iad.ubuntu",
            sshUserName: "ubuntu",
          ]
        ],
        [
          baseImage: [
            id: "centos_7",
            packageType: "DEB",
          ],
          virtualizationSettings: [
            baseImageId: "ocid1.image.oc1.iad.centos",
            sshUserName: "root",
          ]
        ]
      ]
    ]

    oracleBakeryDefaults = new ObjectMapper().convertValue(oracleBakeryDefaultsJson, OracleBakeryDefaults)

    def oracleConfigurationPropertiesJson = [
      accounts: [
        [
          name: ACCOUNT1,
          compartmentId: "ocid1.compartment.oc1..compartment",
          userId: "ocid1.user.oc1..user",
          fingerprint: "myfingerprint",
          sshPrivateKeyFilePath: "mysshPrivateKeyFilePath",
          privateKeyPassphrase: "mypassphrase",
          tenancyId: "ocid1.tenancy.oc1..tenancy",
          region: "us-ashburn-1"
        ],
        [
          name: ACCOUNT2,
          compartmentId: "compartmentId2",
          userId: "userId2",
          fingerprint: "fingerprint2",
          sshPrivateKeyFilePath: "sshPrivateKeyFilePath2",
          privateKeyPassphrase: "mypassphrase2",
          tenancyId: "tenancyId2",
          region: "region2"
        ]
      ]
    ]

    oracleConfigurationProperties = new ObjectMapper().convertValue(oracleConfigurationPropertiesJson, OracleConfigurationProperties)
  }

  def private getLogContent(boolean withImageName) {
    def logsContent = """
    ==> oracle-oci: Creating temporary ssh key for instance...
    ==> oracle-oci: Creating instance...
    ==> oracle-oci: Created instance (ocid1.instance.oc1.iad.abuwcljsfdgdpjwnjkyel3fgpyhscq5xxhhy2zd64oigclne4e3pxk3lpggq).
    ==> oracle-oci: Waiting for instance to enter 'RUNNING' state...
    ==> oracle-oci: Instance 'RUNNING'.
    ==> oracle-oci: Instance has IP: 129.213.132.11.
    ==> oracle-oci: Waiting for SSH to become available...
    ==> oracle-oci: Connected to SSH!
    ==> oracle-oci: Provisioning with shell script: /var/folders/37/ff2c51kj17x6k9szlqkw7rhc0000gn/T/packer-shell076645985
    ==> oracle-oci: Creating image from instance...
    ==> oracle-oci: Image created.
    ==> oracle-oci: Terminating instance (ocid1.instance.oc1.iad.abuwcljsfdgdpjwnjkyel3fgpyhscq5xxhhy2zd64oigclne4e3pxk3lpggq)...
    ==> oracle-oci: Terminated instance.
    Build 'oracle-oci' finished.
    
    ==> Builds finished. The artifacts of successful builds are:
          """
    if (withImageName) {
      logsContent = logsContent + """
    --> oracle-oci: An image was created: 'gzRedis' (OCID: ocid1.image.oc1.iad.aaaaaaaadf4m5nwa4lfm4eiakbxfyxpyt7ttvhx5srn5ptxgbdeh3res3aoa) in region 'us-ashburn-1' "" +
          """
    }
    return logsContent.stripMargin()
  }

  void 'packer logs'() {
    setup:
    def bakeId = "some-bake-id"
    @Subject
    OCIBakeHandler ociBakeHandler = new OCIBakeHandler(oracleBakeryDefaults: oracleBakeryDefaults,
      oracleConfigurationProperties: oracleConfigurationProperties
    )

    when:
    Bake bake = ociBakeHandler.scrapeCompletedBakeResults(REGION, bakeId, getLogContent(logContainingImageName))

    then:
    with(bake) {
      id == bakeId
      ami == expectedImageId
      image_name == expectedImageName
    }

    where:
    logContainingImageName | expectedImageName | expectedImageId
    true                   | "gzRedis"         | "ocid1.image.oc1.iad.aaaaaaaadf4m5nwa4lfm4eiakbxfyxpyt7ttvhx5srn5ptxgbdeh3res3aoa"
    false                  | null              | null
  }


  def 'packer command'() {
    setup:
    def imageNameFactoryMock = Mock(ImageNameFactory)
    def packerCommandFactoryMock = Mock(PackerCommandFactory)
    def osPackages = parseDebOsPackageNames(packageName)
    def targetImageName = 'some-image-name'
    def expectedParameterMap = [
      oracle_compartment_id: oracleConfigurationProperties.accounts.get(accountIndex).compartmentId,
      oracle_tenancy_id: oracleConfigurationProperties.accounts.get(accountIndex).tenancyId,
      oracle_user_id: oracleConfigurationProperties.accounts.get(accountIndex).userId,
      oracle_fingerprint: oracleConfigurationProperties.accounts.get(accountIndex).fingerprint,
      oracle_ssh_private_key_file_path: oracleConfigurationProperties.accounts.get(accountIndex).sshPrivateKeyFilePath,
      oracle_pass_phrase: oracleConfigurationProperties.accounts.get(accountIndex).privateKeyPassphrase,
      oracle_region: oracleConfigurationProperties.accounts.get(accountIndex).region,
      oracle_availability_domain: oracleBakeryDefaults.availabilityDomain,
      oracle_subnet_id: oracleBakeryDefaults.subnetId,
      oracle_instance_shape: oracleBakeryDefaults.instanceShape,
      oracle_base_image_id: oracleBakeryDefaults.baseImages.get(baseImageIndex).virtualizationSettings.baseImageId,
      oracle_ssh_user_name: oracleBakeryDefaults.baseImages.get(baseImageIndex).virtualizationSettings.sshUserName,
      oracle_image_name: targetImageName,
      repository: DEBIAN_REPOSITORY,
      package_type: DEB_PACKAGE_TYPE.util.packageType,
      packages: packageName,
      configDir: CONFIG_DIR
    ]

    @Subject
    OCIBakeHandler ociBakeHandler = new OCIBakeHandler(configDir: CONFIG_DIR,
      oracleBakeryDefaults: oracleBakeryDefaults,
      oracleConfigurationProperties: oracleConfigurationProperties,
      imageNameFactory: imageNameFactoryMock,
      packerCommandFactory: packerCommandFactoryMock,
      debianRepository: DEBIAN_REPOSITORY)

    when:
    ociBakeHandler.produceBakeRecipe(REGION, bakeRequest)

    then:
    1 * imageNameFactoryMock.buildImageName(bakeRequest, osPackages) >> targetImageName
    1 * imageNameFactoryMock.buildAppVersionStr(bakeRequest, osPackages, DEB_PACKAGE_TYPE) >> null
    1 * imageNameFactoryMock.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages) >> packageName
    1 * packerCommandFactoryMock.buildPackerCommand(*_) >> { arguments ->
      final Map<String, String> argumentParameterMap = arguments[1]
      assert argumentParameterMap == expectedParameterMap
      final String argumentTemplateFilePath = arguments[3]
      assert argumentTemplateFilePath == "$CONFIG_DIR/" + templateFileName
    }

    where:
    accountIndex | baseImageIndex | templateFileName                     | packageName   | bakeRequest
    0            | 0              | "$oracleBakeryDefaults.templateFile" | PACKAGES_NAME | buildBakeRequest(null, 'ubuntu16_04', null, packageName)
    0            | 1              | "$oracleBakeryDefaults.templateFile" | PACKAGES_NAME | buildBakeRequest(ACCOUNT1, 'centos_7', null, packageName)
    1            | 0              | "$oracleBakeryDefaults.templateFile" | PACKAGES_NAME | buildBakeRequest(ACCOUNT2, 'ubuntu16_04', null, packageName)
    1            | 1              | "$oracleBakeryDefaults.templateFile" | PACKAGES_NAME | buildBakeRequest(ACCOUNT2, 'centos_7', null, packageName)
    0            | 0              | "custom-template.json"               | PACKAGES_NAME | buildBakeRequest(null, 'ubuntu16_04', "custom-template.json", packageName)
    0            | 0              | "custom-template.json"               | "" | buildBakeRequest(null, 'ubuntu16_04', "custom-template.json", packageName)
  }

  void 'bakeKey'() {
    setup:
    @Subject
    OCIBakeHandler ociBakeHandler = new OCIBakeHandler(oracleBakeryDefaults: oracleBakeryDefaults,
      oracleConfigurationProperties: oracleConfigurationProperties)

    when:
    String bakeKey = ociBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
    bakeKey == expectedBakeKey

    where:
    bakeRequest                                                                  | expectedBakeKey
    buildBakeRequest(null, 'ubuntu16_04', null, PACKAGES_NAME)                   | "bake:oracle:ubuntu16_04:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:" + ACCOUNT1
    buildBakeRequest(null, 'ubuntu16_04', "custom-template.json", PACKAGES_NAME) | "bake:oracle:ubuntu16_04:custom-template.json:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:" + ACCOUNT1
    buildBakeRequest(null, 'ubuntu16_04', null, null)                            | "bake:oracle:ubuntu16_04::" + ACCOUNT1
    buildBakeRequest(null, 'ubuntu16_04', null, "")                              | "bake:oracle:ubuntu16_04::" + ACCOUNT1
  }

  def private buildBakeRequest(String accountName, String baseImage, String templateFileName, String packageName) {
    return new BakeRequest(user: 'someuser@gmail.com',
      package_name: packageName,
      account_name: accountName,
      base_os: baseImage,
      template_file_name: templateFileName,
      cloud_provider_type: BakeRequest.CloudProviderType.oracle)
  }

  void 'bakery options'() {
    setup:
    @Subject
    OCIBakeHandler ociBakeHandler = new OCIBakeHandler(oracleBakeryDefaults: oracleBakeryDefaults,
      oracleConfigurationProperties: oracleConfigurationProperties
    )

    when:
    BakeOptions options = ociBakeHandler.getBakeOptions()

    then:
    with(options) {
      cloudProvider == 'oracle'
      baseImages.size() == 2
      baseImages[0].id == 'ubuntu16_04'
      baseImages[1].id == 'centos_7'
    }
  }

}
