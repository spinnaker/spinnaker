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

package com.netflix.spinnaker.oort.data.aws.cachers.updaters

import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.data.aws.cachers.ClusterCachingAgent
import com.netflix.spinnaker.oort.data.aws.cachers.Front50ApplicationCachingAgent
import com.netflix.spinnaker.oort.data.aws.cachers.InfrastructureCachingAgentFactory
import com.netflix.spinnaker.oort.model.CacheService
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class Front50OnDemandCacheUpdaterSpec extends Specification {
  @Shared
  CacheService cacheService

  @Shared
  NetflixAmazonCredentials credentials

  @Shared
  ConfigurableListableBeanFactory beanFactory = Stub(ConfigurableListableBeanFactory)

  @Subject
  Front50OnDemandCacheUpdater updater

  void setup() {
    GroovyMock(InfrastructureCachingAgentFactory, global: true)

    credentials = Stub(NetflixAmazonCredentials)
    AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider)
    accountCredentialsProvider.getCredentials(_) >> credentials
    ApplicationContext ctx = Stub(ApplicationContext) {
      getAutowireCapableBeanFactory() >> beanFactory
    }

    cacheService = Mock(CacheService)
    updater = new Front50OnDemandCacheUpdater(accountCredentialsProvider: accountCredentialsProvider, applicationContext: ctx)
  }

  void "should invoke the front50 caching agent to update applications for this account and region"() {
    setup:
    def mockAgent = Mock(Front50ApplicationCachingAgent)
    InfrastructureCachingAgentFactory.getFront50CachingAgent(credentials) >> mockAgent

    when:
    updater.handle('Front50Applications', [account: "test"])

    then:
    1 * mockAgent.load()

    where:
    region = "us-west-1"
  }
}
