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

package com.netflix.spinnaker.clouddriver.ecs.provider.view

import com.amazonaws.services.secretsmanager.model.SecretListEntry
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsCluster
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Secret
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.EcsClusterCachingAgent
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.SecretCachingAgent
import spock.lang.Specification
import spock.lang.Subject

class EcsSecretProviderSpec extends Specification {
  private static String ACCOUNT = 'test-account'
  private static String REGION = 'us-west-1'

  private Cache cacheView = Mock(Cache)
  @Subject
  private EcsSecretProvider secretProvider = new EcsSecretProvider(cacheView)

  def 'should get no secrets'() {
    given:
    cacheView.getAll(_) >> Collections.emptySet()

    when:
    def secrets = secretProvider.getAllSecrets()

    then:
    secrets.size() == 0
  }

  def 'should get a secret'() {
    given:
    def secretName = "my-secret"
    def secretArn = "arn:aws:secretsmanager:" + REGION + ":012345678910:secret:" + secretName
    def key = Keys.getSecretKey(ACCOUNT, REGION, secretName)

    HashSet keys = [key]

    SecretListEntry secretEntry = new SecretListEntry(
      name: secretName,
      aRN: secretArn
    )

    def attributes = SecretCachingAgent.convertSecretToAttributes(ACCOUNT, REGION, secretEntry)
    def cacheData = new HashSet()
    cacheData.add(new DefaultCacheData(key, attributes, Collections.emptyMap()))

    cacheView.getAll(_) >> cacheData

    when:
    Collection<Secret> secrets = secretProvider.getAllSecrets()

    then:
    secrets.size() == 1
    secrets[0].getName() == secretName
  }

  def 'should get multiple secrets'() {
    given:
    int numberOfSecrets = 5
    Set<String> secretNames = new HashSet<>()
    Collection<CacheData> cacheData = new HashSet<>()
    Set<String> keys = new HashSet<>()

    numberOfSecrets.times { x ->
      String secretName = "test-secret-" + x
      String secretArn = "arn:aws:secretsmanager:" + REGION + ":012345678910:secret:" + secretName
      String key = Keys.getSecretKey(ACCOUNT, REGION, secretName)

      keys.add(key)
      secretNames.add(secretName)

      SecretListEntry secretEntry = new SecretListEntry(
        name: secretName,
        aRN: secretArn
      )

      Map<String, Object> attributes = SecretCachingAgent.convertSecretToAttributes(ACCOUNT, REGION, secretEntry)
      cacheData.add(new DefaultCacheData(key, attributes, Collections.emptyMap()))
    }

    cacheView.getAll(_) >> cacheData

    when:
    Collection<Secret> secrets = secretProvider.getAllSecrets()

    then:
    secrets.size() == numberOfSecrets
    secretNames.containsAll(secrets*.getName())
    secrets*.getName().containsAll(secretNames)
  }
}
