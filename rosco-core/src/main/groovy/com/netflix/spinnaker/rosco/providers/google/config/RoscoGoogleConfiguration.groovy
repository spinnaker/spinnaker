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

package com.netflix.spinnaker.rosco.providers.google.config

import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.google.GCEBakeHandler
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import groovy.transform.AutoClone
import groovy.transform.AutoCloneStyle
import groovy.transform.ToString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

import javax.annotation.PostConstruct

@Configuration
@ConditionalOnProperty('google.enabled')
@ComponentScan('com.netflix.spinnaker.rosco.providers.google')
class RoscoGoogleConfiguration {

  @Autowired
  CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry

  @Autowired
  GCEBakeHandler gceBakeHandler

  @Bean
  @ConfigurationProperties('google.gce.bakeryDefaults')
  GCEBakeryDefaults deprecatedGCEBakeryDefaults() {
    new GCEBakeryDefaults()
  }

  @Bean
  @ConfigurationProperties('google.bakeryDefaults')
  GCEBakeryDefaults gceBakeryDefaults() {
    new GCEBakeryDefaults()
  }

  @AutoClone(style = AutoCloneStyle.SIMPLE)
  @ToString(includeNames = true)
  static class GCEBakeryDefaults {
    String zone
    String network
    String subnetwork
    Boolean useInternalIp
    String templateFile
    List<GCEOperatingSystemVirtualizationSettings> baseImages = []
  }

  static class GCEBaseImage extends BakeOptions.BaseImage {
    boolean isImageFamily
  }

  static class GCEOperatingSystemVirtualizationSettings {
    GCEBaseImage baseImage
    GCEVirtualizationSettings virtualizationSettings
  }

  @AutoClone(style = AutoCloneStyle.SIMPLE)
  static class GCEVirtualizationSettings {
    // Either sourceImage or sourceImageFamily should be set. If both are set, sourceImage will take precedence.
    String sourceImage
    String sourceImageFamily
  }

  @PostConstruct
  void init() {
    cloudProviderBakeHandlerRegistry.register(BakeRequest.CloudProviderType.gce, gceBakeHandler)
  }

  @Bean
  @ConfigurationProperties("google")
  GoogleConfigurationProperties googleConfigurationProperties() {
    new GoogleConfigurationProperties()
  }

  static class GoogleConfigurationProperties {
    List<ManagedGoogleAccount> accounts = []
  }

  static class ManagedGoogleAccount {
    String name
    String project
    String jsonPath
  }
}
