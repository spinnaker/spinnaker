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

package com.netflix.spinnaker.clouddriver.google.security

import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.config.GoogleConfiguration
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.googlecommon.GoogleExecutor
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import spock.lang.Specification

class GoogleCredentialsInitializerSpec extends Specification {

  def "load clouddriver when unable to connect to non-required accounts"() {
    given:
    GoogleExecutor.globalRegistry = new DefaultRegistry()
    def init = new GoogleCredentialsInitializer()
    def configProps = new GoogleConfigurationProperties(
      accounts: [
           new GoogleConfigurationProperties.ManagedAccount(
             name: "spec",
             project: "a-project-that-doesnot-exist",
           )
      ]
    )
    def accountRepo = Mock(AccountCredentialsRepository)
    def deployDefaults = new GoogleConfiguration.DeployDefaults()


    when:
    init.synchronizeGoogleAccounts("clouddriver", configProps, null, null, accountRepo, [], deployDefaults)

    then:
    noExceptionThrown()
  }

  def "do not load clouddriver when unable to connect to required accounts"() {
    given:
    GoogleExecutor.globalRegistry = new DefaultRegistry()
    def init = new GoogleCredentialsInitializer()
    def configProps = new GoogleConfigurationProperties(
      accounts: [
        new GoogleConfigurationProperties.ManagedAccount(
          name: "spec",
          project: "a-project-that-doesnot-exist",
          required: true,
        )
      ]
    )
    def accountRepo = Mock(AccountCredentialsRepository)
    def deployDefaults = new GoogleConfiguration.DeployDefaults()


    when:
    init.synchronizeGoogleAccounts("clouddriver", configProps, null, null, accountRepo, [], deployDefaults)

    then:
    thrown(IllegalArgumentException)
  }
}
