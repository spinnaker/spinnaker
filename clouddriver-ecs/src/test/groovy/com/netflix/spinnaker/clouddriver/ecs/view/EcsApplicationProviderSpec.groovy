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
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ApplicationCacheClient
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys
import com.netflix.spinnaker.clouddriver.ecs.model.EcsApplication
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TestServiceCachingAgentFactory
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials
import com.netflix.spinnaker.clouddriver.model.Application
import spock.lang.Specification
import spock.lang.Subject

class EcsApplicationProviderSpec extends Specification {
  def cache = Mock(Cache)
  def applicationCacheClient = new ApplicationCacheClient(cache)
  @Subject
  def provider = new EcsApplicationProvider(applicationCacheClient)

  def 'should return an application'() {
    given:
    def accountName = 'test-account'
    def region = 'us-east-1'
    def credentials = new NetflixECSCredentials(TestCredential.named(accountName))
    def appName = 'testapp'
    def serviceName = appName + '-kcats-liated'
    Map<String, Set<String>> clusterNames = new HashMap<>()
    clusterNames.put(accountName, Collections.singleton(serviceName))

    def givenApp = (Application) new EcsApplication(appName,
      [
        name: appName
      ],
      clusterNames)

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
    attributes.put("name", appName)
    attributes.put("account", accountName)
    attributes.put("region", region)

    def relationships = [Keys.getServiceKey(accountName, region, serviceName)]

    cache.filterIdentifiers(_, _) >> []
    cache.get(_, _, _) >> new DefaultCacheData(appName, attributes, [(Keys.Namespace.SERVICES.ns):relationships])

    when:
    def retrievedApp = provider.getApplication(appName)

    then:
    retrievedApp == givenApp
  }
}
