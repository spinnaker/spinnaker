/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.aws.security;

import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.util.CollectionUtils;
import com.netflix.spinnaker.clouddriver.aws.security.config.AccountsConfiguration;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.definition.BasicCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class AmazonBasicCredentialsLoader<
        T extends AccountsConfiguration.Account, U extends NetflixAmazonCredentials>
    extends BasicCredentialsLoader<T, U> {
  protected final CredentialsConfig credentialsConfig;
  protected final AccountsConfiguration accountsConfig;
  protected final DefaultAccountConfigurationProperties defaultAccountConfigurationProperties;
  protected String defaultEnvironment;
  protected String defaultAccountType;

  public AmazonBasicCredentialsLoader(
      CredentialsDefinitionSource<T> definitionSource,
      CredentialsParser<T, U> parser,
      CredentialsRepository<U> credentialsRepository,
      CredentialsConfig credentialsConfig,
      AccountsConfiguration accountsConfig,
      DefaultAccountConfigurationProperties defaultAccountConfigurationProperties) {
    super(definitionSource, parser, credentialsRepository);
    this.credentialsConfig = credentialsConfig;
    this.accountsConfig = accountsConfig;
    this.defaultAccountConfigurationProperties = defaultAccountConfigurationProperties;
    this.defaultEnvironment =
        defaultAccountConfigurationProperties.getEnvironment() != null
            ? defaultAccountConfigurationProperties.getEnvironment()
            : defaultAccountConfigurationProperties.getEnv();
    this.defaultAccountType =
        defaultAccountConfigurationProperties.getAccountType() != null
            ? defaultAccountConfigurationProperties.getAccountType()
            : defaultAccountConfigurationProperties.getEnv();
    if (!StringUtils.isEmpty(credentialsConfig.getAccessKeyId())) {
      System.setProperty(
          SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY, credentialsConfig.getAccessKeyId());
    }
    if (!StringUtils.isEmpty(credentialsConfig.getSecretAccessKey())) {
      System.setProperty(
          SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY,
          credentialsConfig.getSecretAccessKey());
    }
  }

  @Override
  public void load() {
    if (CollectionUtils.isNullOrEmpty(accountsConfig.getAccounts())
        && (StringUtils.isEmpty(credentialsConfig.getDefaultAssumeRole()))) {
      accountsConfig.setAccounts(
          Collections.singletonList(
              new AccountsConfiguration.Account() {
                {
                  setName(defaultAccountConfigurationProperties.getEnv());
                  setEnvironment(defaultEnvironment);
                  setAccountType(defaultAccountType);
                }
              }));
      if (CollectionUtils.isNullOrEmpty(credentialsConfig.getDefaultRegions())) {
        List<Regions> regions =
            new ArrayList<>(
                Arrays.asList(
                    Regions.US_EAST_1, Regions.US_WEST_1, Regions.US_WEST_2, Regions.EU_WEST_1));
        credentialsConfig.setDefaultRegions(
            regions.stream()
                .map(
                    it ->
                        new CredentialsConfig.Region() {
                          {
                            setName(it.getName());
                          }
                        })
                .collect(Collectors.toList()));
      }
    }
    this.parse(definitionSource.getCredentialsDefinitions());
  }
}
