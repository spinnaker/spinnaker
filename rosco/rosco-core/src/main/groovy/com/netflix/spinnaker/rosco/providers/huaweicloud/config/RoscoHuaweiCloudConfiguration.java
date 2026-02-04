/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
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

package com.netflix.spinnaker.rosco.providers.huaweicloud.config;

import com.netflix.spinnaker.rosco.api.BakeOptions;
import com.netflix.spinnaker.rosco.api.BakeRequest;
import com.netflix.spinnaker.rosco.providers.huaweicloud.HuaweiCloudBakeHandler;
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
@ConditionalOnProperty("huaweicloud.enabled")
@ComponentScan("com.netflix.spinnaker.rosco.providers.huaweicloud")
public class RoscoHuaweiCloudConfiguration {

  private final CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry;

  private final HuaweiCloudBakeHandler huaweicloudBakeHandler;

  @Autowired
  public RoscoHuaweiCloudConfiguration(
      CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry,
      HuaweiCloudBakeHandler huaweicloudBakeHandler) {
    this.cloudProviderBakeHandlerRegistry = cloudProviderBakeHandlerRegistry;
    this.huaweicloudBakeHandler = huaweicloudBakeHandler;
  }

  @Bean
  @ConfigurationProperties("huaweicloud.bakery-defaults")
  HuaweiCloudBakeryDefaults huaweicloudBakeryDefaults() {
    return new HuaweiCloudBakeryDefaults();
  }

  @Data
  public static class HuaweiCloudBakeryDefaults {
    private String authUrl;
    private String username;
    private String password;
    private String projectName;
    private String domainName;
    private Boolean insecure;

    private String templateFile;
    private String vpcId;
    private String subnetId;
    private String securityGroup;
    private Integer eipBandwidthSize;
    private List<HuaweiCloudOperatingSystemVirtualizationSettings> baseImages = new ArrayList<>();
  }

  @Data
  public static class HuaweiCloudOperatingSystemVirtualizationSettings {
    private BakeOptions.BaseImage baseImage;
    private List<HuaweiCloudVirtualizationSettings> virtualizationSettings = new ArrayList<>();
  }

  @Data
  public static class HuaweiCloudVirtualizationSettings implements Cloneable {
    private String region;
    private String eipType;
    private String instanceType;
    private String sourceImageId;
    private String sshUserName;

    @Override
    public Object clone() throws CloneNotSupportedException {
      return super.clone();
    }
  }

  @PostConstruct
  void init() {
    cloudProviderBakeHandlerRegistry.register(
        BakeRequest.CloudProviderType.huaweicloud, huaweicloudBakeHandler);
  }
}
