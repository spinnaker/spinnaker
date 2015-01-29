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

import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.providers.aws.AWSBakeHandler
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import javax.annotation.PostConstruct

@ConditionalOnProperty('aws.enabled')
@EnableConfigurationProperties
@Configuration
@CompileStatic
class RoscoAWSConfiguration {

  @Autowired
  CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry

  @Autowired
  AWSBakeHandler awsBakeHandler

  @Bean
  @ConfigurationProperties('aws.bakeryDefaults')
  AWSBakeryDefaults awsBakeryDefaults(@Value('${aws.bakeryDefaults.defaultVirtualizationType:hvm}') BakeRequest.VmType defaultVirtualizationType) {
    new AWSBakeryDefaults(defaultVirtualizationType: defaultVirtualizationType)
  }

  static class AWSBakeryDefaults {
    String awsAccessKey
    String awsSecretKey
    String templateFile
    BakeRequest.VmType defaultVirtualizationType
    List<AWSOperatingSystemVirtualizationSettings> operatingSystemVirtualizationSettings = []
  }

  static class AWSOperatingSystemVirtualizationSettings {
    BakeRequest.OperatingSystem os
    List<AWSVirtualizationSettings> virtualizationSettings = []
  }

  static class AWSVirtualizationSettings {
    String region
    BakeRequest.VmType virtualizationType
    String instanceType
    String sourceAmi
    String sshUserName
  }

  @PostConstruct
  void init() {
    cloudProviderBakeHandlerRegistry.register(
      BakeRequest.CloudProviderType.aws, awsBakeHandler)
  }

}
