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

package com.netflix.spinnaker.clouddriver.aws.security

import com.amazonaws.SDKGlobalConfiguration
import com.amazonaws.auth.AWSCredentialsProvider
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.security.config.AmazonCredentialsParser
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig
import com.netflix.spinnaker.clouddriver.aws.security.config.AccountsConfiguration
import com.netflix.spinnaker.clouddriver.aws.security.config.AccountsConfiguration.Account
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.stream.Collectors

class AmazonBasicCredentialsLoaderSpec extends Specification{
  @Shared
  def defaultAccountConfigurationProperties = new DefaultAccountConfigurationProperties()

  def 'should set defaults'() {
    setup:
    def credentialsConfig = new CredentialsConfig(){{
      setAccessKeyId("accessKey")
      setSecretAccessKey("secret")
    }}
    def definitionSource = Mock(CredentialsDefinitionSource) {
      getCredentialsDefinitions() >> []
    }
    def credentialsRepository = new MapBackedCredentialsRepository<NetflixAmazonCredentials>(AmazonCloudProvider.ID, null)
    AccountsConfiguration accountsConfig = new AccountsConfiguration()
    def loader = new AmazonBasicCredentialsLoader<Account, NetflixAmazonCredentials>(
      definitionSource,
      null,
      credentialsRepository,
      credentialsConfig,
      accountsConfig,
      defaultAccountConfigurationProperties
    )

    when:
    loader.load()

    then:
    accountsConfig.getAccounts().size() == 1
    with (accountsConfig.getAccounts().first()) { Account account ->
      account.name == "default"
      account.environment == "default"
      account.accountType == "default"
    }

    credentialsConfig.getDefaultRegions().size() == 4
    System.getProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY) == "accessKey"
    System.getProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY) == "secret"
  }

  @Unroll("should load and parse a large number of accounts with multi-threading: #multiThreadingEnabled")
  def 'should load and parse a large number of accounts'() {
    setup:
    def credentialsRepository = new MapBackedCredentialsRepository<NetflixAmazonCredentials>(AmazonCloudProvider.ID, null)

    // create 500 accounts
    List<Account> accounts = new ArrayList<>()
    for (number in 0..499) {
      Account account = new Account(name: 'prod' + number, accountId: number)
      if (number == 0) {
        // test an account having a region that matches one of the default regions
        account.setRegions([
          new CredentialsConfig.Region(name: 'us-west-2')
        ])
      }
      // all other accounts would end up using the default regions from credentials config in this case. This is
      // to test that with multiThreading enabled, we don't run into ConcurrentModificationException errors when
      // sorting these regions per account
      accounts.add(account)
    }
    AccountsConfiguration accountsConfig = new AccountsConfiguration(accounts: accounts)

    AWSCredentialsProvider provider = Mock(AWSCredentialsProvider)
    AWSAccountInfoLookup lookup = Mock(AWSAccountInfoLookup)

    CredentialsConfig credentialsConfig = new CredentialsConfig(){{
      setAccessKeyId("accessKey")
      setSecretAccessKey("secret")
    }}
    credentialsConfig.loadAccounts.setMultiThreadingEnabled(multiThreadingEnabled)

    credentialsConfig.setDefaultRegions(
      ['us-east-1','us-west-2'] .stream()
        .map(
          { it ->
            new CredentialsConfig.Region() {
              {
                setName(it)
              }
            }
          })
        .collect(Collectors.toList())
    )

    CredentialsDefinitionSource<Account> amazonCredentialsSource = { -> accountsConfig.getAccounts() } as CredentialsDefinitionSource
    AmazonCredentialsParser<Account, NetflixAmazonCredentials> ci = new AmazonCredentialsParser<>(
      provider, lookup, NetflixAmazonCredentials.class, credentialsConfig, accountsConfig)
    def loader = new AmazonBasicCredentialsLoader<Account, NetflixAmazonCredentials>(
      amazonCredentialsSource, ci, credentialsRepository, credentialsConfig, accountsConfig, defaultAccountConfigurationProperties
    )

    when:
    loader.load()

    then:
    lookup.listRegions(['us-east-1', 'us-west-2']) >> [
      new AmazonCredentials.AWSRegion('us-east-1', ['us-east-1a', 'us-east-1b']),
      new AmazonCredentials.AWSRegion('us-west-2', ['us-west-2a']),
    ]

    // verify we have 500 accounts in the accounts config object
    accountsConfig.getAccounts().size() == 500

    // verify we have saved 500 accounts in the credentials repository
    credentialsRepository.getAll().size() == 500

    // test some random accounts
    with (accountsConfig.getAccounts().first()) { Account account ->
      account.name == "prod0"
      account.environment == "prod0"
      account.accountType == "prod0"
    }

    // test an account that has 1 region which is not a default region
    with (accountsConfig.getAccounts().get(100)) { Account account ->
      account.name == "prod100"
      account.environment == "prod100"
      account.accountType == "prod100"
    }

    // test an account that has multiple regions
    with (accountsConfig.getAccounts().get(200)) { Account account ->
      account.name == "prod200"
      account.environment == "prod200"
      account.accountType == "prod200"
    }

    // test an account that does not have any regions
    with (accountsConfig.getAccounts().last()) { Account account ->
      account.name == "prod499"
      account.environment == "prod499"
      account.accountType == "prod499"
    }

    where:
    multiThreadingEnabled | _
    true                  | _
    false                 | _
  }
}
