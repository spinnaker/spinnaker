/*
 * Copyright 2019 Alibaba Group, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rosco.providers.alicloud.config;

import com.netflix.spinnaker.rosco.api.BakeOptions;
import com.netflix.spinnaker.rosco.api.BakeRequest;
import com.netflix.spinnaker.rosco.providers.alicloud.AliCloudBakeHandler;
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("alicloud.enabled")
@ComponentScan("com.netflix.spinnaker.rosco.providers.alicloud")
public class RoscoAliCloudConfiguration {

  private final CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry;

  private final AliCloudBakeHandler alicloudBakeHandler;

  @Autowired
  public RoscoAliCloudConfiguration(
      CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry,
      AliCloudBakeHandler alicloudBakeHandler) {
    this.cloudProviderBakeHandlerRegistry = cloudProviderBakeHandlerRegistry;
    this.alicloudBakeHandler = alicloudBakeHandler;
  }

  @Bean
  @ConfigurationProperties("alicloud.bakery-defaults")
  AliCloudBakeryDefaults alicloudBakeryDefaults() {
    return new AliCloudBakeryDefaults();
  }

  @Data
  public static class AliCloudBakeryDefaults {
    private String alicloudAccessKey;
    private String alicloudSecretKey;
    private String alicloudVSwitchId;
    private String alicloudVpcId;
    private String templateFile;
    private List<AliCloudOperatingSystemVirtualizationSettings> baseImages = new ArrayList<>();
  }

  @Data
  public static class AliCloudOperatingSystemVirtualizationSettings {
    private BakeOptions.BaseImage baseImage;
    private List<AliCloudVirtualizationSettings> virtualizationSettings = new ArrayList<>();
  }

  @Data
  public static class AliCloudVirtualizationSettings implements Cloneable {
    private String region;
    private String instanceType;
    private String sourceImage;
    private String sshUserName;

    @Override
    public Object clone() throws CloneNotSupportedException {
      return super.clone();
    }
  }

  @PostConstruct
  void init() {
    cloudProviderBakeHandlerRegistry.register(
        BakeRequest.CloudProviderType.alicloud, alicloudBakeHandler);
  }
}
