/*
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.
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

package com.netflix.spinnaker.rosco.providers.tencentcloud.config;

import com.netflix.spinnaker.rosco.api.BakeOptions.BaseImage;
import com.netflix.spinnaker.rosco.api.BakeRequest.CloudProviderType;
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry;
import com.netflix.spinnaker.rosco.providers.tencentcloud.TencentCloudBakeHandler;
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
@ConditionalOnProperty("tencentcloud.enabled")
@ComponentScan("com.netflix.spinnaker.rosco.providers.tencentcloud")
public class RoscoTencentCloudConfiguration {

  @Autowired private CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry;
  @Autowired private TencentCloudBakeHandler tencentCloudBakeHandler;

  @Bean
  @ConfigurationProperties("tencentcloud.bakery-defaults")
  public TencentCloudBakeryDefaults tencentCloudBakeryDefaults() {
    return new TencentCloudBakeryDefaults();
  }

  @PostConstruct
  public void init() {
    cloudProviderBakeHandlerRegistry.register(
        CloudProviderType.tencentcloud, tencentCloudBakeHandler);
  }

  @Data
  public static class TencentCloudBakeryDefaults {
    private String secretId;
    private String secretKey;
    private String subnetId;
    private String vpcId;
    private Boolean associatePublicIpAddress;
    private String templateFile;
    private List<TencentCloudOperatingSystemVirtualizationSettings> baseImages = new ArrayList<>();
  }

  @Data
  public static class TencentCloudOperatingSystemVirtualizationSettings {
    private BaseImage baseImage;
    private List<TencentCloudVirtualizationSettings> virtualizationSettings = new ArrayList<>();
  }

  @Data
  public static class TencentCloudVirtualizationSettings implements Cloneable {
    private String region;
    private String zone;
    private String instanceType;
    private String sourceImageId;
    private String imageName;
    private String sshUserName;

    @Override
    public Object clone() throws CloneNotSupportedException {
      return super.clone();
    }
  }
}
