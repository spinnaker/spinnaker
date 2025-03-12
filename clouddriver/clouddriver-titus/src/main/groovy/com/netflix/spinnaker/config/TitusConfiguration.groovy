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

package com.netflix.spinnaker.config

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.event.SpinnakerEvent
import com.netflix.spinnaker.clouddriver.saga.config.SagaAutoConfiguration
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.client.SimpleGrpcChannelFactory
import com.netflix.spinnaker.clouddriver.titus.client.TitusJobCustomizer
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion
import com.netflix.spinnaker.clouddriver.titus.client.model.GrpcChannelFactory
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer.SubtypeLocator
import groovy.util.logging.Slf4j
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

import java.util.regex.Pattern

@Configuration
@ConditionalOnProperty('titus.enabled')
@EnableConfigurationProperties
@ComponentScan('com.netflix.spinnaker.clouddriver.titus')
@Import(SagaAutoConfiguration)
@Slf4j
class TitusConfiguration {

  @Bean
  @ConfigurationProperties("titus")
  TitusCredentialsConfig titusCredentialsConfig() {
    new TitusCredentialsConfig()
  }

  @Bean
  List<NetflixTitusCredentials> netflixTitusCredentials(TitusCredentialsConfig titusCredentialsConfig,
                                                        AccountCredentialsRepository repository) {
    List<NetflixTitusCredentials> accounts = new ArrayList<>()
    for (TitusCredentialsConfig.Account account in titusCredentialsConfig.accounts) {
      List<TitusRegion> regions = account.regions.collect {
        new TitusRegion(it.name, account.name, it.endpoint, it.applicationName, it.url, it.port, it.featureFlags, it.eurekaName , it.eurekaRegion)
      }
      if (!account.bastionHost && titusCredentialsConfig.defaultBastionHostTemplate) {
        account.bastionHost = titusCredentialsConfig.defaultBastionHostTemplate.replaceAll(Pattern.quote('{{environment}}'), account.environment)
      }
      NetflixTitusCredentials credentials = new NetflixTitusCredentials(
        account.name,
        account.environment,
        account.accountType,
        regions,
        account.bastionHost,
        account.registry,
        account.awsAccount,
        account.awsVpc ?: titusCredentialsConfig.awsVpc,
        account.discoveryEnabled,
        account.discovery,
        account.stack ?: 'mainvpc',
        account.requiredGroupMembership,
        account.getPermissions(),
        account.eurekaName
      )
      accounts.add(credentials)
      repository.save(account.name, credentials)
    }
    return accounts
  }

  @Bean
  TitusClientProvider titusClientProvider(Registry registry, Optional<List<TitusJobCustomizer>> titusJobCustomizers, GrpcChannelFactory grpcChannelFactory, RetrySupport retrySupport) {
    return new TitusClientProvider(registry, titusJobCustomizers.orElse(Collections.emptyList()), grpcChannelFactory, retrySupport)
  }

  @Bean
  @ConditionalOnMissingBean(GrpcChannelFactory)
  GrpcChannelFactory simpleGrpcChannelFactory() {
    new SimpleGrpcChannelFactory()
  }

  static class TitusCredentialsConfig {
    String defaultBastionHostTemplate
    String awsVpc
    List<Account> accounts
    static class Account {
      String name
      String environment
      String accountType
      String bastionHost
      Boolean discoveryEnabled
      String discovery
      String awsAccount
      String registry
      List<Region> regions
      String awsVpc
      String stack
      List<String> requiredGroupMembership
      //see getPermissions for the reasoning behind
      //the generic types on here..
      Map<String, Map<String, String>> permissions
      String eurekaName

      Permissions getPermissions() {
        //boot yaml mapping is weird..
        //READ:
        //  - teamdl@company.org
        //WRITE:
        //  - teamdl@company.org
        //
        //ends up as: [
        // READ: [0: teamdl@company.org],
        // WRITE: [0: teamdl@company.org]
        //]

        if (!permissions) {
          return Permissions.EMPTY
        }

        def builder = new Permissions.Builder()
        permissions.each { String authType, Map<String, String> roles ->
          //make sure we don't blow up on unknown enum values:
          def auth =  Authorization.ALL.find { it.toString() == authType.toString() }
          if (auth) {
            builder.add(auth, roles.values() as List<String>)
          }
        }
        return builder.build()
      }
    }

    static class Region {
      String name
      String endpoint
      String applicationName
      String url
      Integer port
      List<String> featureFlags
      String eurekaName
      String eurekaRegion
    }
  }

  @Bean
  SubtypeLocator titusEventSubtypeLocator() {
    return new ObjectMapperSubtypeConfigurer.ClassSubtypeLocator(
      SpinnakerEvent.class,
      Collections.singletonList("com.netflix.spinnaker.clouddriver.titus")
    );
  }
}
