/*
 * Copyright 2019 Alibaba Group.
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
package com.netflix.spinnaker.clouddriver.alicloud.security;

import com.netflix.spinnaker.clouddriver.alicloud.security.config.AliCloudAccountConfig;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class AliCloudCredentialsInitializer implements CredentialsInitializerSynchronizable {

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("alicloud")
  AliCloudAccountConfig aliCloudAccountConfig() {
    return new AliCloudAccountConfig();
  }

  @Bean
  List synchronizeAliCloudAccounts(
      AliCloudAccountConfig aliCloudAccountConfig,
      AccountCredentialsRepository accountCredentialsRepository,
      ApplicationContext applicationContext) {
    List<AliCloudCredentials> aliCloudCredentialsList = new ArrayList<>();
    aliCloudAccountConfig.getAccounts().stream()
        .forEach(
            account -> {
              AliCloudCredentials aliCloudCredentials = new AliCloudCredentials();
              aliCloudCredentials.setName(account.getName());
              aliCloudCredentials.setAccessSecretKey(account.getAccessSecretKey());
              aliCloudCredentials.setAccessKeyId(account.getAccessKeyId());
              aliCloudCredentials.setRegions(account.getRegions());
              accountCredentialsRepository.save(account.getName(), aliCloudCredentials);
              aliCloudCredentialsList.add(aliCloudCredentials);
            });
    return aliCloudCredentialsList;
  }
}
