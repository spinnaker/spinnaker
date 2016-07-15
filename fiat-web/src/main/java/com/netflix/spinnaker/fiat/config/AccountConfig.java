/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.fiat.providers.CloudProviderAccounts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccountConfig {

  @Bean
  @ConfigurationProperties("aws")
  @ConditionalOnProperty("providers.aws.enabled")
  public CloudProviderAccounts awsAccounts() {
    return new CloudProviderAccounts("aws");
  }

  @Bean
  @ConfigurationProperties("azure")
  @ConditionalOnProperty("providers.azure.enabled")
  public CloudProviderAccounts azureAccounts() {
    return new CloudProviderAccounts("azure");
  }

  @Bean
  @ConfigurationProperties("cf")
  @ConditionalOnProperty("providers.cf.enabled")
  public CloudProviderAccounts cfAccounts() {
    return new CloudProviderAccounts("cf");
  }

  @Bean
  @ConfigurationProperties("google")
  @ConditionalOnProperty("providers.google.enabled")
  public CloudProviderAccounts googleAccounts() {
    return new CloudProviderAccounts("google");
  }

  @Bean
  @ConfigurationProperties("kubernetes")
  @ConditionalOnProperty("providers.kubernetes.enabled")
  public CloudProviderAccounts kubernetesAccounts() {
    return new CloudProviderAccounts("kubernetes");
  }
}
