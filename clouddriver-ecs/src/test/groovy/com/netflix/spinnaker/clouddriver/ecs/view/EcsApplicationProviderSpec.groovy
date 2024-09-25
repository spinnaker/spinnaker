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

import com.amazonaws.services.ecs.model.DeploymentConfiguration
import com.amazonaws.services.ecs.model.Service
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
    clusterNames.put(accountName, Collections.singleton(serviceName))
    Map<String, Set<String>> clusterNameMetadata = new HashMap<>()
    clusterNameMetadata.put(accountName, Collections.singleton(monikerCluster))



    def givenApp = (Application) new EcsApplication(appName,
      [
        name: appName
      ],
      clusterNames,
      clusterNameMetadata)

    def service = new Service(
      serviceName: serviceName,
      deploymentConfiguration: new DeploymentConfiguration()
        .withMaximumPercent(100)
        .withMinimumHealthyPercent(0),
      desiredCount: 1,
      createdAt: new Date()
    )
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
}
