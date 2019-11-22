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

package com.netflix.spinnaker.clouddriver.huaweicloud.security;

import com.netflix.spinnaker.clouddriver.huaweicloud.config.HuaweiCloudConfigurationProperties;
import com.netflix.spinnaker.clouddriver.huaweicloud.config.HuaweiCloudConfigurationProperties.ManagedAccount;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class HuaweiCloudCredentialsInitializer implements CredentialsInitializerSynchronizable {

  private final Logger log = LoggerFactory.getLogger(HuaweiCloudCredentialsInitializer.class);

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("huaweicloud")
  HuaweiCloudConfigurationProperties huaweiCloudConfigurationProperties() {
    return new HuaweiCloudConfigurationProperties();
  }

  @Bean
  List<HuaweiCloudNamedAccountCredentials> synchronizeHuaweiCloudNamedAccountCredentials(
      HuaweiCloudConfigurationProperties huaweiCloudConfigurationProperties,
      AccountCredentialsRepository accountCredentialsRepository) {

    List result =
        ProviderUtils.calculateAccountDeltas(
            accountCredentialsRepository,
            HuaweiCloudNamedAccountCredentials.class,
            huaweiCloudConfigurationProperties.getAccounts());

    List<ManagedAccount> accountsToAdd = (List<ManagedAccount>) result.get(0);
    accountsToAdd.forEach(
        managedAccount -> {
          try {
            HuaweiCloudNamedAccountCredentials account =
                new HuaweiCloudNamedAccountCredentials(
                    managedAccount.getName(),
                    managedAccount.getEnvironment(),
                    managedAccount.getAccountType(),
                    managedAccount.getAuthUrl(),
                    managedAccount.getUsername(),
                    managedAccount.getPassword(),
                    managedAccount.getProjectName(),
                    managedAccount.getDomainName(),
                    managedAccount.getInsecure(),
                    managedAccount.getRegions());

            accountCredentialsRepository.save(managedAccount.getName(), account);
          } catch (Exception e) {
            log.error(
                "Could not load account:{} for huaweicloud, error={}", managedAccount.getName(), e);
          }
        });

    List<String> accountNamesToDelete = (List<String>) result.get(1);
    ProviderUtils.unscheduleAndDeregisterAgents(accountNamesToDelete, null);

    return (List<HuaweiCloudNamedAccountCredentials>)
        accountCredentialsRepository.getAll().stream()
            .filter(it -> it instanceof HuaweiCloudNamedAccountCredentials)
            .collect(Collectors.toList());
  }
}
