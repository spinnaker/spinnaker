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

package com.netflix.apinnaker.oort.model.aws

import com.codahale.metrics.Timer
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.data.aws.Keys.Namespace
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.model.aws.AmazonApplication
import com.netflix.spinnaker.oort.model.aws.AmazonApplicationProvider
import spock.lang.Shared
import spock.lang.Specification

class AmazonApplicationProviderSpec extends Specification {

  @Shared
  AmazonApplicationProvider provider

  @Shared
  CacheService cacheService

  Timer timer = new Timer()

  def setup() {
    provider = new AmazonApplicationProvider()
    cacheService = Mock(CacheService)
    provider.cacheService = cacheService
    AmazonApplicationProvider.declaredFields.findAll { it.type == Timer }.each {
      provider.setProperty(it.name, timer)
    }
  }

  void "compose application-cluster relationship from cache keys"() {
    setup:
    def app1 = Mock(AmazonApplication)
    def appName = "kato"
    def cluster = "kato-main"
    def account = "test"
    cacheService.keys() >> [Keys.getApplicationKey(appName), Keys.getClusterKey(cluster, appName, account)]

    when:
    def app = provider.getApplication(appName)

    then:
    _ * app1.getName() >> appName
    1 * cacheService.retrieve(Keys.getApplicationKey(appName), _) >> app1
  }

  void "compose application-cluster relationship from cache keys for all applications"() {
    setup:
    def app1 = Mock(AmazonApplication)
    def appName1 = "kato"
    def cluster1 = "kato-main"
    def account1 = "test"
    def app2 = Mock(AmazonApplication)
    def appName2 = "oort"
    def cluster2 = "oort-main"
    def account2 = "prod"
    cacheService.keysByType(Namespace.APPLICATIONS) >> [Keys.getApplicationKey(appName1), Keys.getApplicationKey(appName2)]
    cacheService.keysByType(Namespace.CLUSTERS) >> [Keys.getClusterKey(cluster1, appName1, account1), Keys.getClusterKey(cluster2, appName2, account2)]

    when:
    def apps = provider.getApplications()

    then:
    "should check that the app is actually still around"
    _ * app1.getName() >> appName1
    _ * app2.getName() >> appName2
    1 * cacheService.retrieve(Keys.getApplicationKey(appName1), _) >> app1
    1 * cacheService.retrieve(Keys.getApplicationKey(appName2), _) >> app2
    1 * app1.setProperty('clusterNames', [(account1): [cluster1] as Set])
    1 * app2.setProperty('clusterNames', [(account2): [cluster2] as Set])
    apps.size() == 2
  }
}
