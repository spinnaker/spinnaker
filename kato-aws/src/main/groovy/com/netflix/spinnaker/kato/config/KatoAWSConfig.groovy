/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.kato.config
import com.amazonaws.auth.AWSCredentialsProvider
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.amos.aws.NetflixAssumeRoleAmazonCredentials
import com.netflix.spinnaker.amos.aws.config.CredentialsConfig
import com.netflix.spinnaker.amos.aws.config.CredentialsLoader
import com.netflix.spinnaker.kato.aws.deploy.userdata.NullOpUserDataProvider
import com.netflix.spinnaker.kato.aws.deploy.userdata.UserDataProvider
import com.netflix.spinnaker.kato.aws.model.AmazonInstanceClassBlockDevice
import com.netflix.spinnaker.kato.aws.security.BastionCredentialsProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.AbstractEnvironment
import org.springframework.core.env.MapPropertySource

import static com.amazonaws.regions.Regions.*

@ConditionalOnProperty('aws.enabled')
@EnableConfigurationProperties
@ComponentScan('com.netflix.spinnaker.kato.aws')
@Configuration
class KatoAWSConfig {

  @Bean
  @ConditionalOnMissingBean(UserDataProvider)
  UserDataProvider userDataProvider() {
    new NullOpUserDataProvider()
  }

  @Bean
  AmazonClientProvider amazonClientProvider() {
    new AmazonClientProvider()
  }

  @Bean
  @ConfigurationProperties("aws")
  CredentialsConfig credentialsConfig() {
    new CredentialsConfig()
  }

  @Bean
  @ConfigurationProperties("bastion")
  BastionConfiguration bastionConfiguration() {
    new BastionConfiguration()
  }

  static class BastionConfiguration {
    Boolean enabled
    String host
    String user
    Integer port
    String proxyCluster
    String proxyRegion
    String accountIamRole
  }

  @Bean
  @ConditionalOnProperty('bastion.enabled')
  AWSCredentialsProvider bastionCredentialsProvider(BastionConfiguration bastionConfiguration) {
    def provider = new BastionCredentialsProvider(bastionConfiguration.user, bastionConfiguration.host, bastionConfiguration.port, bastionConfiguration.proxyCluster,
      bastionConfiguration.proxyRegion, bastionConfiguration.accountIamRole)

    provider.refresh()

    provider
  }

  @Bean
  Class<? extends NetflixAmazonCredentials> credentialsType(CredentialsConfig credentialsConfig) {
    if (!credentialsConfig.accounts) {
      NetflixAmazonCredentials
    } else {
      NetflixAssumeRoleAmazonCredentials
    }
  }

  @Bean
  CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader(AWSCredentialsProvider awsCredentialsProvider, AbstractEnvironment environment, Class<? extends NetflixAmazonCredentials> credentialsType) {
    Map<String, String> envProps = environment.getPropertySources().findAll {
      it instanceof MapPropertySource
    }.collect { MapPropertySource mps ->
      mps.propertyNames.collect {
        [(it): environment.getConversionService().convert(mps.getProperty(it), String)]
      }
    }.flatten().collectEntries()
    new CredentialsLoader<? extends NetflixAmazonCredentials>(awsCredentialsProvider, credentialsType, envProps)
  }

  @Bean
  List<? extends NetflixAmazonCredentials> netflixAmazonCredentials(CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader,
                                                                    CredentialsConfig credentialsConfig,
                                                                    AccountCredentialsRepository accountCredentialsRepository,
                                                                    @Value('${default.account.env:default}') String defaultEnv) {

    if (!credentialsConfig.accounts) {
      credentialsConfig = new CredentialsConfig(
              defaultRegions: [US_EAST_1, US_WEST_1, US_WEST_2, EU_WEST_1].collect { new CredentialsConfig.Region(name: it.name) },
              accounts: [new CredentialsConfig.Account(name: defaultEnv)])
    }

    List<? extends NetflixAmazonCredentials> accounts = credentialsLoader.load(credentialsConfig)

    for (act in accounts) {
      accountCredentialsRepository.save(act.name, act)
    }

    accounts
  }

  @Bean
  @ConfigurationProperties('aws.defaults')
  DeployDefaults deployDefaults() {
    new DeployDefaults()
  }

  static class DeployDefaults {
    String iamRole
    List<AmazonInstanceClassBlockDevice> instanceClassBlockDevices = []
  }
}
