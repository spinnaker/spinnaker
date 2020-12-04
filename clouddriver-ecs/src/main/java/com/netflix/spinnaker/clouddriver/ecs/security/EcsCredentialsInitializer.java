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
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.BasicCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import com.netflix.spinnaker.credentials.poller.Poller;
import javax.annotation.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
public class EcsCredentialsInitializer {

  @Bean
  @ConfigurationProperties("ecs")
  public ECSCredentialsConfig ecsCredentialsConfig() {
    return new ECSCredentialsConfig();
  }

  @Bean
  @DependsOn("amazonCredentialsLoader")
  @ConditionalOnMissingBean(
      value = NetflixECSCredentials.class,
      parameterizedContainer = CredentialsRepository.class)
  CredentialsRepository<NetflixECSCredentials> ecsCredentialsRepository(
      CredentialsLifecycleHandler<NetflixECSCredentials> eventHandler) {
    return new MapBackedCredentialsRepository<>(EcsCloudProvider.ID, eventHandler);
  }

  @Bean
  @DependsOn("amazonCredentialsLoader")
  @ConditionalOnMissingBean(name = "ecsCredentialsParser")
  CredentialsParser<ECSCredentialsConfig.Account, NetflixECSCredentials> ecsCredentialsParser(
      ECSCredentialsConfig ecsCredentialsConfig,
      CompositeCredentialsRepository<AccountCredentials> compositeCredentialsRepository,
      CredentialsParser<CredentialsConfig.Account, NetflixAmazonCredentials>
          amazonCredentialsParser,
      NamerRegistry namerRegistry) {
    return new EcsCredentialsParser<>(
        ecsCredentialsConfig,
        compositeCredentialsRepository,
        amazonCredentialsParser,
        namerRegistry);
  }

  @Bean
  @DependsOn("ecsCredentialsParser")
  @ConditionalOnMissingBean(name = "ecsCredentialsLoader")
  AbstractCredentialsLoader<NetflixECSCredentials> ecsCredentialsLoader(
      CredentialsParser<ECSCredentialsConfig.Account, NetflixECSCredentials>
          amazonCredentialsParser,
      CredentialsRepository<NetflixECSCredentials> repository,
      ECSCredentialsConfig ecsCredentialsConfig,
      @Nullable CredentialsDefinitionSource<ECSCredentialsConfig.Account> ecsCredentialsSource) {
    if (ecsCredentialsSource == null) {
      ecsCredentialsSource = ecsCredentialsConfig::getAccounts;
    }
    return new BasicCredentialsLoader<>(ecsCredentialsSource, amazonCredentialsParser, repository);
  }

  @Bean
  @ConditionalOnMissingBean(name = "ecsCredentialsInializerSynchronizable")
  CredentialsInitializerSynchronizable ecsCredentialsInializerSynchronizable(
      AbstractCredentialsLoader<NetflixECSCredentials> ecsCredentialsLoader) {
    final Poller<NetflixECSCredentials> poller = new Poller<>(ecsCredentialsLoader);
    return new CredentialsInitializerSynchronizable() {
      @Override
      public void synchronize() {
        poller.run();
      }
    };
  }
}
