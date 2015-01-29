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

import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.providers.google.GCEBakeHandler
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
  GCEBakeryDefaults gceBakeryDefaults() {
    new GCEBakeryDefaults()
  }

  static class GCEBakeryDefaults {
    String project
    String zone
    String templateFile
    List<GCEOperatingSystemVirtualizationSettings> operatingSystemVirtualizationSettings = []
  }

  static class GCEOperatingSystemVirtualizationSettings {
    BakeRequest.OperatingSystem os
    GCEVirtualizationSettings virtualizationSettings
  }

  static class GCEVirtualizationSettings {
    String sourceImage
  }

  @PostConstruct
  void init() {
    cloudProviderBakeHandlerRegistry.register(BakeRequest.CloudProviderType.gce, gceBakeHandler)
  }

}
