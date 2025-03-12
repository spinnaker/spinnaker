/*
 * Copyright 2021 Salesforce.com, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security;

import com.github.wnameless.json.unflattener.JsonUnflattener;
import com.netflix.spinnaker.clouddriver.aws.security.config.AccountsConfiguration;
import com.netflix.spinnaker.clouddriver.config.AbstractBootstrapCredentialsConfigurationProvider;
import com.netflix.spinnaker.kork.configserver.CloudConfigResourceService;
import com.netflix.spinnaker.kork.secrets.SecretManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * If a configuration properties file has a large number of aws accounts, as-is SpringBoot
 * implementation of properties binding is inefficient. Hence, a custom logic for binding just the
 * {@link AccountsConfiguration} is written but it still uses SpringBoot's Binder class. {@link
 * CustomAccountsConfigurationProvider} class fetches the flattened aws account properties from
 * Spring Cloud Config's BootstrapPropertySource and creates an {@link AccountsConfiguration}
 * object.
 */
@Slf4j
public class CustomAccountsConfigurationProvider
    extends AbstractBootstrapCredentialsConfigurationProvider {
  private final String FIRST_ACCOUNT_NAME_KEY = "aws.accounts[0].name";

  public CustomAccountsConfigurationProvider(
      ConfigurableApplicationContext applicationContext,
      CloudConfigResourceService configResourceService,
      SecretManager secretManager) {
    super(applicationContext, configResourceService, secretManager);
  }

  public AccountsConfiguration getConfigurationProperties() {
    return getAccounts(getPropertiesMap(FIRST_ACCOUNT_NAME_KEY));
  }

  @SuppressWarnings("unchecked")
  private AccountsConfiguration getAccounts(Map<String, Object> credentialsPropertiesMap) {
    log.info("Started loading aws accounts from the configuration file");
    AccountsConfiguration accountConfig = new AccountsConfiguration();
    BindResult<?> result;

    // unflatten
    Map<String, Object> propertiesMap =
        (Map<String, Object>) JsonUnflattener.unflattenAsMap(credentialsPropertiesMap).get("aws");

    List<AccountsConfiguration.Account> accounts = new ArrayList<>();

    // loop through each account and bind
    for (Map<String, Object> unflattendAcc :
        ((List<Map<String, Object>>) propertiesMap.get("accounts"))) {
      result = bind(getFlatMap(unflattendAcc), AccountsConfiguration.Account.class);
      accounts.add((AccountsConfiguration.Account) result.get());
    }
    accountConfig.setAccounts(accounts);
    log.info("Finished loading aws accounts");
    return accountConfig;
  }
}
