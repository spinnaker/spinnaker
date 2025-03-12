/*
 * Copyright 2024 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.azure.health

import com.netflix.spinnaker.clouddriver.azure.config.AzureConfigurationProperties
import com.netflix.spinnaker.clouddriver.azure.client.AzureResourceManagerClient
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import com.netflix.spinnaker.clouddriver.azure.security.AzureNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.boot.actuate.health.Status
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class AzureHealthIndicatorSpec extends Specification {

  @Shared
  AccountCredentialsProvider accountCredentialsProvider

  private void setupMocks(def mockResourceManager, def mockCredentials, def mockAccountCredentials) {
    def credentialsField = AzureNamedAccountCredentials.getDeclaredField("credentials")
    credentialsField.accessible = true
    credentialsField.set(mockAccountCredentials, mockCredentials)

    def resourceManagerField = AzureCredentials.getDeclaredField("resourceManagerClient")
    resourceManagerField.accessible = true
    resourceManagerField.set(mockCredentials, mockResourceManager)

    accountCredentialsProvider = Mock(AccountCredentialsProvider)
    accountCredentialsProvider.all >> [mockAccountCredentials]
  }

  @Unroll
  def "health succeeds when azure is reachable"() {
    setup:
    def mockResourceManager = Mock(AzureResourceManagerClient)
    def mockCredentials = Mock(AzureCredentials)
    def mockAccountCredentials = Mock(AzureNamedAccountCredentials)
    setupMocks(mockResourceManager, mockCredentials, mockAccountCredentials)

    def indicator = new AzureHealthIndicator(azureConfigurationProperties: new AzureConfigurationProperties())
    indicator.accountCredentialsProvider = accountCredentialsProvider

    when:
    indicator.checkHealth()
    def health = indicator.health()

    then:
    health.status == Status.UP
    health.details.isEmpty()
  }

  @Unroll
  def "health fails when azure is unreachable - verifyAccountHealth:true"() {
    setup:
    def mockResourceManager = Mock(AzureResourceManagerClient)
    def mockCredentials = Mock(AzureCredentials)
    def mockAccountCredentials = Mock(AzureNamedAccountCredentials)
    setupMocks(mockResourceManager, mockCredentials, mockAccountCredentials)

    def indicator = new AzureHealthIndicator(azureConfigurationProperties: new AzureConfigurationProperties())
    indicator.accountCredentialsProvider = accountCredentialsProvider

    when:
    mockResourceManager.healthCheck() >> { throw new IOException("Azure is unreachable") }
    indicator.checkHealth()
    indicator.health()

    then:
    thrown(AzureHealthIndicator.AzureIOException)
  }

  @Unroll
  def "health fails when no azure credentials are found - verifyAccountHealth:true"() {
    setup:
    accountCredentialsProvider = Mock(AccountCredentialsProvider)
    accountCredentialsProvider.all >> []

    def indicator = new AzureHealthIndicator(azureConfigurationProperties: new AzureConfigurationProperties())
    indicator.accountCredentialsProvider = accountCredentialsProvider

    when:
    indicator.checkHealth()
    indicator.health()

    then:
    thrown(AzureHealthIndicator.AzureCredentialsNotFoundException)
  }

  @Unroll
  def "health succeeds when verifyAccountHealth flag is disabled"() {
    setup:
    def azureConfigProps = new AzureConfigurationProperties()
    azureConfigProps.health.verifyAccountHealth = false

    def indicator = new AzureHealthIndicator(azureConfigurationProperties: azureConfigProps)
    indicator.accountCredentialsProvider = accountCredentialsProvider

    when:
    indicator.checkHealth()
    def health = indicator.health()

    then:
    health.status == Status.UP
    health.details.isEmpty()
  }

  @Unroll
  def "health succeeds when azure is unreachable - verifyAccountHealth:false"() {
    setup:
    def mockResourceManager = Mock(AzureResourceManagerClient)
    def mockCredentials = Mock(AzureCredentials)
    def mockAccountCredentials = Mock(AzureNamedAccountCredentials)
    setupMocks(mockResourceManager, mockCredentials, mockAccountCredentials)

    def azureConfigProps = new AzureConfigurationProperties()
    azureConfigProps.health.verifyAccountHealth = false
    def indicator = new AzureHealthIndicator(azureConfigurationProperties: azureConfigProps)
    indicator.accountCredentialsProvider = accountCredentialsProvider

    when:
    mockResourceManager.healthCheck() >> { throw new IOException("Azure is unreachable") }
    indicator.checkHealth()
    def health = indicator.health()

    then:
    health.status == Status.UP
    health.details.isEmpty()
  }
}
