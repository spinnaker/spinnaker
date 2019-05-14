/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.security;

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsLoader;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Scope;

@Configuration
public class EcsCredentialsInitializer implements CredentialsInitializerSynchronizable {

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("ecs")
  public ECSCredentialsConfig ecsCredentialsConfig() {
    return new ECSCredentialsConfig();
  }

  @Bean
  @DependsOn("netflixAmazonCredentials")
  public List<? extends NetflixAmazonCredentials> netflixECSCredentials(
      CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader,
      ECSCredentialsConfig credentialsConfig,
      AccountCredentialsRepository accountCredentialsRepository)
      throws Throwable {
    return synchronizeECSAccounts(
        credentialsLoader, credentialsConfig, accountCredentialsRepository);
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @DependsOn("netflixAmazonCredentials")
  public List<? extends NetflixAmazonCredentials> synchronizeECSAccounts(
      CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader,
      ECSCredentialsConfig ecsCredentialsConfig,
      AccountCredentialsRepository accountCredentialsRepository)
      throws Throwable {

    // TODO: add support for mutable accounts.
    // List deltaAccounts = ProviderUtils.calculateAccountDeltas(accountCredentialsRepository,
    // NetflixAmazonCredentials.class, accounts);
    List<NetflixAmazonCredentials> credentials = new LinkedList<>();

    for (AccountCredentials accountCredentials : accountCredentialsRepository.getAll()) {
      if (accountCredentials instanceof NetflixAmazonCredentials) {
        for (ECSCredentialsConfig.Account ecsAccount : ecsCredentialsConfig.getAccounts()) {
          if (ecsAccount.getAwsAccount().equals(accountCredentials.getName())) {

            NetflixAmazonCredentials netflixAmazonCredentials =
                (NetflixAmazonCredentials) accountCredentials;

            // TODO: accountCredentials should be serializable or somehow cloneable.
            CredentialsConfig.Account account =
                EcsAccountBuilder.build(netflixAmazonCredentials, ecsAccount.getName(), "ecs");

            CredentialsConfig ecsCopy = new CredentialsConfig();
            ecsCopy.setAccounts(Collections.singletonList(account));

            NetflixECSCredentials ecsCredentials =
                new NetflixAssumeRoleEcsCredentials(
                    (NetflixAssumeRoleAmazonCredentials) credentialsLoader.load(ecsCopy).get(0),
                    ecsAccount.getAwsAccount());
            credentials.add(ecsCredentials);

            accountCredentialsRepository.save(ecsAccount.getName(), ecsCredentials);
            break;
          }
        }
      }
    }

    return credentials;
  }

  @Override
  public String getCredentialsSynchronizationBeanName() {
    return "synchronizeECSAccounts";
  }
}
