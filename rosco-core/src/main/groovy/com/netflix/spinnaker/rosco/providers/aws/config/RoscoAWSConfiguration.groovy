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

package com.netflix.spinnaker.rosco.providers.aws.config

import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.aws.AWSBakeHandler
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import groovy.transform.AutoClone
import groovy.transform.AutoCloneStyle
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

import javax.annotation.PostConstruct

@Configuration
@ConditionalOnProperty('aws.enabled')
@ComponentScan('com.netflix.spinnaker.rosco.providers.aws')
class RoscoAWSConfiguration {

  @Autowired
  CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry

  @Autowired
  AWSBakeHandler awsBakeHandler

  @Bean
  @ConfigurationProperties('aws.bakery-defaults')
  AWSBakeryDefaults awsBakeryDefaults(@Value('${aws.bakery-defaults.default-virtualization-type:hvm}') BakeRequest.VmType defaultVirtualizationType) {
    new AWSBakeryDefaults(defaultVirtualizationType: defaultVirtualizationType)
  }

  static class AWSBakeryDefaults {
    String awsAccessKey
    String awsSecretKey
    String awsSubnetId
    String awsVpcId
    Boolean awsAssociatePublicIpAddress
    String templateFile
    BakeRequest.VmType defaultVirtualizationType
    List<AWSOperatingSystemVirtualizationSettings> baseImages = []
  }

  static class AWSOperatingSystemVirtualizationSettings {
    BakeOptions.BaseImage baseImage
    List<AWSVirtualizationSettings> virtualizationSettings = []
  }

  @AutoClone(style = AutoCloneStyle.SIMPLE)
  static class AWSVirtualizationSettings {
    String region
    BakeRequest.VmType virtualizationType
    String instanceType
    String sourceAmi
    boolean mostRecent
    String sshUserName
    String winRmUserName
    String spotPrice
    String spotPriceAutoProduct
  }

  static class AWSNamedImage {
    String imageName
    AWSImageAttributes attributes
    Map<String, Map<String, String>> tagsByImageId = [:]
    Set<String> accounts = []
    Map<String, Collection<String>> amis = [:]
  }

  static class AWSImageAttributes {
    Date creationDate
    BakeRequest.VmType virtualizationType
  }

  @PostConstruct
  void init() {
    cloudProviderBakeHandlerRegistry.register(BakeRequest.CloudProviderType.aws, awsBakeHandler)
  }
}
