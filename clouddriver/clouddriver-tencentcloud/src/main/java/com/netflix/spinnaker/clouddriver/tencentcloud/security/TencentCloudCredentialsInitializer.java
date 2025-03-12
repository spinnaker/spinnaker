/*
 * Copyright 2019 THL A29 Limited, a Tencent company.
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

package com.netflix.spinnaker.clouddriver.tencentcloud.security;

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.clouddriver.tencentcloud.config.TencentCloudConfigurationProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class TencentCloudCredentialsInitializer implements CredentialsInitializerSynchronizable {

  @Bean
  public List<TencentCloudNamedAccountCredentials> tencentCloudNamedAccountCredentials(
      TencentCloudConfigurationProperties tencentCloudConfigurationProperties,
      AccountCredentialsRepository accountCredentialsRepository) {
    return syncAccounts(tencentCloudConfigurationProperties, accountCredentialsRepository);
  }

  private List<TencentCloudNamedAccountCredentials> syncAccounts(
      TencentCloudConfigurationProperties tencentCloudConfigurationProperties,
      AccountCredentialsRepository accountCredentialsRepository) {

    List<TencentCloudNamedAccountCredentials> credentialsList = new ArrayList<>();

    tencentCloudConfigurationProperties
        .getAccounts()
        .forEach(
            managedAccount -> {
              TencentCloudNamedAccountCredentials credentials =
                  new TencentCloudNamedAccountCredentials(managedAccount);
              accountCredentialsRepository.save(managedAccount.getName(), credentials);
              credentialsList.add(credentials);
            });

    return credentialsList;
  }
}
