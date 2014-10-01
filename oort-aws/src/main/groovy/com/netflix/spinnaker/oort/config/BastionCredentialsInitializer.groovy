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



package com.netflix.spinnaker.oort.config

import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.oort.security.aws.BastionCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Configuration

import javax.annotation.PostConstruct

@Configuration
@ConditionalOnExpression('${bastion.enabled:false}')
class BastionCredentialsInitializer implements CredentialsInitializer {
  @Autowired
  AccountCredentialsRepository accountCredentialsRepository

  @Autowired
  BastionConfiguration bastionConfiguration

  @Autowired
  AwsConfigurationProperties awsConfigurationProperties

  @PostConstruct
  void init() {
    def provider = new BastionCredentialsProvider(bastionConfiguration.user, bastionConfiguration.host, bastionConfiguration.port, bastionConfiguration.proxyCluster,
        bastionConfiguration.proxyRegion, awsConfigurationProperties.accountIamRole)

    provider.refresh()

    for (account in awsConfigurationProperties.accounts) {
      account.assumeRole = account.assumeRole ?: awsConfigurationProperties.assumeRole
      account.credentialsProvider = provider
      accountCredentialsRepository.save(account.name, account)
    }
  }


}
