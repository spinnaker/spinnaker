/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.kayenta.configbin.config;

import com.netflix.kayenta.configbin.security.ConfigBinAccountCredentials;
import com.netflix.kayenta.configbin.security.ConfigBinNamedAccountCredentials;
import com.netflix.kayenta.configbin.service.ConfigBinRemoteService;
import com.netflix.kayenta.configbin.storage.ConfigBinStorageService;
import com.netflix.kayenta.retrofit.config.RemoteService;
import com.netflix.kayenta.retrofit.config.RetrofitClientFactory;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.squareup.okhttp.OkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Configuration
@EnableConfigurationProperties
@ConditionalOnProperty("kayenta.configbin.enabled")
@ComponentScan({"com.netflix.kayenta.configbin"})
@Slf4j
public class ConfigBinConfiguration {

  @Bean
  @ConfigurationProperties("kayenta.configbin")
  ConfigBinConfigurationProperties configbinConfigurationProperties() {
    return new ConfigBinConfigurationProperties();
  }

  @Bean
  ConfigBinStorageService configBinStorageService(ConfigBinResponseConverter configBinConverter,
                                                  ConfigBinConfigurationProperties configBinConfigurationProperties,
                                                  RetrofitClientFactory retrofitClientFactory,
                                                  OkHttpClient okHttpClient,
                                                  AccountCredentialsRepository accountCredentialsRepository) {
    log.debug("Created a ConfigBin StorageService");
    ConfigBinStorageService.ConfigBinStorageServiceBuilder configbinStorageServiceBuilder = ConfigBinStorageService.builder();

    for (ConfigBinManagedAccount configBinManagedAccount : configBinConfigurationProperties.getAccounts()) {
      String name = configBinManagedAccount.getName();
      String ownerApp = configBinManagedAccount.getOwnerApp();
      String configType = configBinManagedAccount.getConfigType();
      RemoteService endpoint = configBinManagedAccount.getEndpoint();
      List<AccountCredentials.Type> supportedTypes = configBinManagedAccount.getSupportedTypes();

      log.info("Registering ConfigBin account {} with supported types {}.", name, supportedTypes);

      ConfigBinAccountCredentials configbinAccountCredentials = ConfigBinAccountCredentials.builder().build();
      ConfigBinNamedAccountCredentials.ConfigBinNamedAccountCredentialsBuilder configBinNamedAccountCredentialsBuilder =
        ConfigBinNamedAccountCredentials.builder()
          .name(name)
          .ownerApp(ownerApp)
          .configType(configType)
          .endpoint(endpoint)
          .credentials(configbinAccountCredentials);

      if (!CollectionUtils.isEmpty(supportedTypes)) {
        if (supportedTypes.contains(AccountCredentials.Type.CONFIGURATION_STORE)) {
          ConfigBinRemoteService configBinRemoteService = retrofitClientFactory.createClient(ConfigBinRemoteService.class,
                                                                                             configBinConverter,
                                                                                             configBinManagedAccount.getEndpoint(),
                                                                                             okHttpClient);
          configBinNamedAccountCredentialsBuilder.remoteService(configBinRemoteService);
        }
        configBinNamedAccountCredentialsBuilder.supportedTypes(supportedTypes);
      }

      ConfigBinNamedAccountCredentials configbinNamedAccountCredentials = configBinNamedAccountCredentialsBuilder.build();
      accountCredentialsRepository.save(name, configbinNamedAccountCredentials);
      configbinStorageServiceBuilder.accountName(name);
    }

    ConfigBinStorageService configbinStorageService = configbinStorageServiceBuilder.build();

    log.info("Populated ConfigBinStorageService with {} ConfigBin accounts.", configbinStorageService.getAccountNames().size());

    return configbinStorageService;
  }
}
