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


import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsResponse
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Secret
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SECRETS

class SecretCachingAgentSpec extends Specification {
  def secretsManager = Mock(SecretsManagerClient)
  def clientProvider = Mock(AmazonClientProvider)
  def providerCache = Mock(ProviderCache)

  @Subject
  SecretCachingAgent agent = new SecretCachingAgent(CommonCachingAgent.netflixAmazonCredentials, 'us-west-1', clientProvider)

  def 'should get a list of secrets'() {
    given:
    def account = 'test-account'
    def region = 'us-west-1'
    def givenSecrets = []
    def secretsEntries = []
    0.upto(4, {
      def secretName = "test-secret-${it}"
      givenSecrets << SecretListEntry.builder()
        .name(secretName)
        .arn("arn:aws:secretsmanager:us-west-1:0123456789012:secret:${secretName}")
        .build()
    })
    secretsManager.listSecrets(_) >> ListSecretsResponse.builder().secretList(givenSecrets).build()

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
      secretsEntries << SecretListEntry.builder()
        .name(secretName)
        .arn("arn:aws:secretsmanager:us-west-1:0123456789012:secret:${secretName}")
        .build()
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

  def 'should use filterIdentifiers with account and region glob for evictions'() {
    given:
    def givenSecret = SecretListEntry.builder()
      .name("test-secret")
      .arn("arn:aws:secretsmanager:us-west-1:0123456789012:secret:test-secret")
      .build()
    clientProvider.getAmazonSecretsManagerV2(_, _) >> secretsManager
    secretsManager.listSecrets(_) >> ListSecretsResponse.builder().secretList([givenSecret]).build()

    def account = 'test-account'
    def region = 'us-west-1'
    def expectedGlob = com.netflix.spinnaker.clouddriver.ecs.cache.Keys.buildGlob(SECRETS, account, region)
    def oldIdentifiers = ['ecs;secrets;test-account;us-west-1;old-secret']
    providerCache.filterIdentifiers(SECRETS.ns, expectedGlob) >> oldIdentifiers

    when:
    def result = agent.loadData(providerCache)

    then:
    result.evictions[SECRETS.ns] != null
    result.evictions[SECRETS.ns].containsAll(oldIdentifiers)
  }
}
