/*
 * Copyright 2024 OpsMx, Inc.
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

package com.netflix.spinnaker.rosco.providers.aws.config;

import com.netflix.spinnaker.rosco.api.BakeRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("aws.enabled")
public class AWSBakeryDefaultsBean {
  @Bean
  @ConfigurationProperties("aws.bakery-defaults")
  public RoscoAWSConfiguration.AWSBakeryDefaults awsBakeryDefaults(
      @Value("${aws.bakery-defaults.default-virtualization-type:hvm}")
          BakeRequest.VmType defaultVirtualizationType) {
    RoscoAWSConfiguration.AWSBakeryDefaults defaults =
        new RoscoAWSConfiguration.AWSBakeryDefaults();
    defaults.setDefaultVirtualizationType(defaultVirtualizationType);
    return defaults;
  }
}
