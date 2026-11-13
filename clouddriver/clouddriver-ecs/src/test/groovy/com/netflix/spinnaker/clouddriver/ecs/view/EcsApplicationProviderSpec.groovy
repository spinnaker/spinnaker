/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.view

import software.amazon.awssdk.services.ecs.model.DeploymentConfiguration
import java.time.Instant
import java.util.regex.Pattern
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient
import com.netflix.spinnaker.clouddriver.ecs.model.EcsApplication
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.ServiceCachingAgent
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TestServiceCachingAgentFactory
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials
import com.netflix.spinnaker.clouddriver.model.Application
import com.netflix.spinnaker.credentials.CredentialsRepository
import spock.lang.Specification
import spock.lang.Subject

class EcsApplicationProviderSpec extends Specification {
  def mapper = new ObjectMapper()
  def cache = Mock(Cache)
  def serviceCacheClient = new ServiceCacheClient(cache, mapper)
  def credentialsRepository = Mock(CredentialsRepository)
  @Subject
  def provider = new EcsApplicationProvider(credentialsRepository, serviceCacheClient)

  def 'should return an application'() {
    given:
    def accountName = 'test-account'
    def credentials = new NetflixECSCredentials(TestCredential.named(accountName))
    def appName = 'testapp'
    def serviceName = appName + '-kcats-liated-v001'
    def monikerCluster = appName + '-kcats-liated'
    Map<String, Set<String>> clusterNames = new HashMap<>()
    clusterNames.put(accountName, Collections.singleton(monikerCluster))

    def givenApp = (Application) new EcsApplication(appName,
      [
        name: appName
      ],
      clusterNames)

    def service = software.amazon.awssdk.services.ecs.model.Service.builder()
      .serviceName(serviceName)
      .deploymentConfiguration(DeploymentConfiguration.builder()
        .maximumPercent(100)
        .minimumHealthyPercent(0)
        .build())
      .desiredCount(1)
      .createdAt(Instant.now())
      .build()
    def attributes = TestServiceCachingAgentFactory.create(credentials,
      credentials.getRegions()[0].getName()).convertServiceToAttributes(service)

    credentialsRepository.getAll() >> [credentials]
    credentialsRepository.has(accountName) >> true
    cache.filterIdentifiers(_, _) >> [Keys.getServiceKey(accountName,"us-east-1",serviceName)]
    cache.getAll(_, _) >> [new DefaultCacheData(Keys.getServiceKey(accountName,"us-east-1",serviceName), attributes, [:])]

    when:
    def retrievedApp = provider.getApplication(appName)

    then:
    retrievedApp == givenApp
  }

  def 'should list the applications of each configured account and region'() {
    given:
    def accountA = new NetflixECSCredentials(TestCredential.named('account-a'))
    def accountB = new NetflixECSCredentials(TestCredential.named('account-b', [regions: [[name: 'us-east-1']]]))
    def unconfiguredAccount = new NetflixECSCredentials(TestCredential.named('account-c'))
    def cacheData = [
      serviceCacheData(accountA, 'us-east-1', 'appone-east-v001'),
      serviceCacheData(accountA, 'us-west-1', 'appone-west-v001'),
      serviceCacheData(accountB, 'us-east-1', 'apptwo-east-v001'),
      serviceCacheData(unconfiguredAccount, 'us-east-1', 'appthree-east-v001'),
      serviceCacheData(accountA, 'eu-west-1', 'appfour-west-v001'),
    ]
    credentialsRepository.getAll() >> [accountA, accountB]
    cache.filterIdentifiers(_, _) >> { String type, String glob -> cacheData*.id.findAll { it ==~ globToRegex(glob) } }
    cache.getAll(_, _) >> { String type, Collection<String> ids -> cacheData.findAll { ids.contains(it.id) } }

    when:
    def applications = provider.getApplications(expand)

    then:
    applications == expected as Set

    where:
    expand | expected
    true   | [application('appone', [('account-a'): ['appone-east']]),
              application('appone', [('account-a'): ['appone-west']]),
              application('apptwo', [('account-b'): ['apptwo-east']])]
    false  | [application('appone', [:]),
              application('apptwo', [:])]
  }

  def 'should look up the services of every account and region at once'() {
    given:
    def accountA = new NetflixECSCredentials(TestCredential.named('account-a'))
    def accountB = new NetflixECSCredentials(TestCredential.named('account-b'))
    credentialsRepository.getAll() >> [accountA, accountB]

    when:
    provider.getApplications(true)

    then:
    1 * cache.filterIdentifiers(_, _) >> []
  }

  private DefaultCacheData serviceCacheData(NetflixECSCredentials credentials, String region, String serviceName) {
    def service = software.amazon.awssdk.services.ecs.model.Service.builder()
      .serviceName(serviceName)
      .deploymentConfiguration(DeploymentConfiguration.builder()
        .maximumPercent(100)
        .minimumHealthyPercent(0)
        .build())
      .desiredCount(1)
      .createdAt(Instant.now())
      .build()
    def attributes = TestServiceCachingAgentFactory.create(credentials, region).convertServiceToAttributes(service)
    return new DefaultCacheData(Keys.getServiceKey(credentials.getName(), region, serviceName), attributes, [:])
  }

  private static String globToRegex(String glob) {
    return glob.split('\\*', -1).collect { Pattern.quote(it) }.join('.*')
  }

  private static EcsApplication application(String name, Map<String, List<String>> clusterNames) {
    Map<String, Set<String>> clusters = clusterNames.collectEntries { k, v -> [(k): v as Set] }
    return new EcsApplication(name, [name: name], clusters)
  }
}
