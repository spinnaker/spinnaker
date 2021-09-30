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

  @Unroll("should load and parse a large number of accounts having different regions when default regions: #defaultRegionsInConfig are specified in the config and with multi-threading: #multiThreadingEnabled")
  def 'should load and parse a large number of accounts having different regions'() {
    setup:
    def credentialsRepository = new MapBackedCredentialsRepository<NetflixAmazonCredentials>(AmazonCloudProvider.ID, null)

    // create 500 accounts having a mix of regions. Some will have regions that match default regions, some will
    // have regions that don't match default regions, and some will not have regions at all
    List<Account> accounts = new ArrayList<>()
    for (number in 0..499) {
      Account account = new Account(name: 'prod' + number, accountId: number)
      if (number == 0) {
        // test an account having a region that matches one of the default regions
        account.setRegions([
          new CredentialsConfig.Region(name: 'us-west-2')
        ])
      } else if (number == 100) {
        // test an account whose region shouldn't be there in the region cache
        account.setRegions([
          new CredentialsConfig.Region(name: 'ap-southeast-1')
        ])
      } else if (number == 200 || number == 400) {
        // test an account which has a region not contained in the region cache for account number 200, but should have
        // it for account number 400
        account.setRegions([
          new CredentialsConfig.Region(name: 'ap-southeast-1'),
          new CredentialsConfig.Region(name: 'ap-southeast-2'),
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
      defaultRegionsInConfig.stream()
        .map(
          { it ->
            new CredentialsConfig.Region() {
              {
                setName(it)
              }
            }
          })
        .collect(Collectors.toList()))

    CredentialsDefinitionSource<Account> amazonCredentialsSource = { -> accountsConfig.getAccounts() } as CredentialsDefinitionSource
    AmazonCredentialsParser<Account, NetflixAmazonCredentials> ci = new AmazonCredentialsParser<>(
      provider, lookup, NetflixAmazonCredentials.class, credentialsConfig, accountsConfig)
    def loader = new AmazonBasicCredentialsLoader<Account, NetflixAmazonCredentials>(
      amazonCredentialsSource, ci, credentialsRepository, credentialsConfig, accountsConfig, defaultAccountConfigurationProperties
    )

    when:
    loader.load()

    then:
    // verify invocations to list regions
    if (defaultRegionsInConfig.isEmpty()) {
      // just the one call to load all the regions will be made in the absence of any default regions in the config
      1 * lookup.listRegions() >> [
        new AmazonCredentials.AWSRegion('us-east-1', ['us-east-1a', 'us-east-1b']),
        new AmazonCredentials.AWSRegion('us-west-2', ['us-west-2a']),
        new AmazonCredentials.AWSRegion('ap-southeast-1', ['ap-southeast-1a', 'ap-southeast-1b']),
        new AmazonCredentials.AWSRegion('ap-southeast-2', ['ap-southeast-2a', 'ap-southeast-2b'])
      ]
    } else {
      1 * lookup.listRegions(['us-east-1', 'us-west-2']) >> [
        new AmazonCredentials.AWSRegion('us-east-1', ['us-east-1a', 'us-east-1b']),
        new AmazonCredentials.AWSRegion('us-west-2', ['us-west-2a']),
      ]

      1 * lookup.listRegions(['ap-southeast-1']) >> [
        new AmazonCredentials.AWSRegion('ap-southeast-1', ['ap-southeast-1a', 'ap-southeast-1b'])
      ]

      1 * lookup.listRegions(['ap-southeast-2']) >> [
        new AmazonCredentials.AWSRegion('ap-southeast-2', ['ap-southeast-2a', 'ap-southeast-2b'])
      ]
    }

    0 * lookup.listRegions

    // verify accounts
    accountsConfig.getAccounts().size() == 500

    // verify we have saved 500 accounts in the credentials repository
    credentialsRepository.getAll().size() == 500

    // test an account that has 1 region which is a default region
    with (accountsConfig.getAccounts().first()) { Account account ->
      account.name == "prod0"
      account.environment == "prod0"
      account.accountType == "prod0"
      account.regions.size() == 1
      account.regions.first().name == 'us-west-2'
      account.regions.first().availabilityZones.toList().sort() == ['us-west-2a']
    }

    // test an account that has 1 region which is not a default region
    with (accountsConfig.getAccounts().get(100)) { Account account ->
      account.name == "prod100"
      account.environment == "prod100"
      account.accountType == "prod100"
      account.regions.size() == 1
      account.regions.first().name == 'ap-southeast-1'
      account.regions.first().availabilityZones.toList().sort() == ['ap-southeast-1a', 'ap-southeast-1b']
    }

    // test an account that has multiple regions
    with (accountsConfig.getAccounts().get(200)) { Account account ->
      account.name == "prod200"
      account.environment == "prod200"
      account.accountType == "prod200"
      account.regions.size() == 3
      account.regions.find { it.name == 'ap-southeast-1' }.availabilityZones.size() == 2
      (!account.regions.find { it.name == 'ap-southeast-1' }.deprecated)
      account.regions.find { it.name == 'ap-southeast-2' }.availabilityZones.size() == 2
      (!account.regions.find { it.name == 'ap-southeast-2' }.deprecated)
      account.regions.find { it.name == 'us-west-2' }.availabilityZones.size() == 1
      (!account.regions.find { it.name == 'us-west-2' }.deprecated)
    }

    // test an account that did not have any default regions specified in the account definition.
    // It should use the defaults created for the accounts based on what was set as the default
    // in the credentials config
    with (accountsConfig.getAccounts().last()) { Account account ->
      account.name == "prod499"
      account.environment == "prod499"
      account.accountType == "prod499"
      if (defaultRegionsInConfig.isEmpty()) {
        account.regions.size() == 4
        account.regions.find { it.name == 'us-east-1' }.availabilityZones.size() == 2
        (!account.regions.find { it.name == 'us-east-1' }.deprecated)
        account.regions.find { it.name == 'ap-southeast-1' }.availabilityZones.size() == 2
        (!account.regions.find { it.name == 'ap-southeast-1' }.deprecated)
        account.regions.find { it.name == 'ap-southeast-2' }.availabilityZones.size() == 2
        (!account.regions.find { it.name == 'ap-southeast-2' }.deprecated)
        account.regions.find { it.name == 'us-west-2' }.availabilityZones.size() == 1
        (!account.regions.find { it.name == 'us-west-2' }.deprecated)
      } else {
        account.regions.size() == 2
        account.regions.find { it.name == 'us-east-1' }.availabilityZones.size() == 2
        (!account.regions.find { it.name == 'us-east-1' }.deprecated)
        account.regions.find { it.name == 'us-west-2' }.availabilityZones.size() == 1
        (!account.regions.find { it.name == 'us-west-2' }.deprecated)
      }
    }

    where:
    multiThreadingEnabled | defaultRegionsInConfig
    true                  | ['us-east-1','us-west-2']
    false                 | ['us-east-1','us-west-2']
    true                  | []
    false                 | []
  }
}
