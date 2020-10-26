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
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig
import com.netflix.spinnaker.credentials.CredentialsRepository
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource
import spock.lang.Shared
import spock.lang.Specification

class AmazonBasicCredentialsLoaderSpec extends Specification{
  @Shared
  def credentialsConfig = new CredentialsConfig(){{
    setAccessKeyId("accessKey")
    setSecretAccessKey("secret")
  }}
  @Shared
  def defaultAccountConfigurationProperties = new DefaultAccountConfigurationProperties()
  @Shared
  def credentialsRepository = Mock(CredentialsRepository) {
    getAll() >> []
  }
  @Shared
  def definitionSource = Mock(CredentialsDefinitionSource) {
    getCredentialsDefinitions() >> []
  }

  def 'should set defaults'() {
    def loader = new AmazonBasicCredentialsLoader<CredentialsConfig.Account, NetflixAmazonCredentials>(
      definitionSource, null, credentialsRepository, credentialsConfig, defaultAccountConfigurationProperties
    )

    when:
    loader.load()

    then:
    credentialsConfig.getAccounts().size() == 1
    credentialsConfig.getDefaultRegions().size() == 4
    System.getProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY) == "accessKey"
    System.getProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY) == "secret"
  }
}
