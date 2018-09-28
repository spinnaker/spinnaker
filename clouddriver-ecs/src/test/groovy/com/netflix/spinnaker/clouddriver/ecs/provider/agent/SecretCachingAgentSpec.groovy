/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.agent

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace
import com.amazonaws.services.secretsmanager.AWSSecretsManager
import com.amazonaws.services.secretsmanager.model.ListSecretsResult
import com.amazonaws.services.secretsmanager.model.SecretListEntry
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Secret
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SECRETS

class SecretCachingAgentSpec extends Specification {
  def secretsManager = Mock(AWSSecretsManager)
  def clientProvider = Mock(AmazonClientProvider)
  def providerCache = Mock(ProviderCache)
  def credentialsProvider = Mock(AWSCredentialsProvider)
  def objectMapper = new ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  @Subject
  SecretCachingAgent agent = new SecretCachingAgent(CommonCachingAgent.netflixAmazonCredentials, 'us-west-1', clientProvider, credentialsProvider, objectMapper)

  def 'should get a list of secrets'() {
    given:
    def account = 'test-account'
    def region = 'us-west-1'
    def givenSecrets = []
    def secretsEntries = []
    0.upto(4, {
      def secretName = "test-secret-${it}"
      givenSecrets << new SecretListEntry(
        name: secretName,
        aRN: "arn:aws:secretsmanager:us-west-1:0123456789012:secret:${secretName}"
      )
    })
    secretsManager.listSecrets(_) >> new ListSecretsResult().withSecretList(givenSecrets)

    when:
    def retrievedSecrets = agent.fetchSecrets(secretsManager)

    then:
    retrievedSecrets.containsAll(givenSecrets)
    givenSecrets.containsAll(retrievedSecrets)
  }

  def 'should generate fresh data'() {
    given:
    Set givenSecrets = []
    Set secretsEntries = []
    0.upto(4, {
      def secretName = "test-secret-${it}"
      givenSecrets << new Secret(
        account: 'test-account',
        region: 'us-west-1',
        name: secretName,
        arn: "arn:aws:secretsmanager:us-west-1:0123456789012:secret:${secretName}"
      )
      secretsEntries << new SecretListEntry(
        name: secretName,
        aRN: "arn:aws:secretsmanager:us-west-1:0123456789012:secret:${secretName}"
      )
    })

    when:
    def cacheData = agent.generateFreshData(secretsEntries)

    then:
    cacheData.size() == 1
    cacheData.get(SECRETS.ns).size() == givenSecrets.size()
    givenSecrets*.account.containsAll(cacheData.get(SECRETS.ns)*.getAttributes().account)
    givenSecrets*.region.containsAll(cacheData.get(SECRETS.ns)*.getAttributes().region)
    givenSecrets*.name.containsAll(cacheData.get(SECRETS.ns)*.getAttributes().secretName)
    givenSecrets*.arn.containsAll(cacheData.get(SECRETS.ns)*.getAttributes().secretArn)
  }
}
