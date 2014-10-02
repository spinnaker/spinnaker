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

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.data.aws.cachers.Front50ApplicationCachingAgent
import com.netflix.spinnaker.oort.data.aws.cachers.InfrastructureCachingAgentFactory
import com.netflix.spinnaker.oort.model.OnDemandCacheUpdater
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext

class Front50OnDemandCacheUpdater implements OnDemandCacheUpdater {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  ApplicationContext applicationContext

  @Override
  boolean handles(String type) {
    type == "Front50Applications"
  }

  @Override
  void handle(Map<String, ? extends Object> data) {
    if (!data.containsKey("account")) {
      return
    }
    if (!data.containsKey("region")) {
      return
    }

    def account = data.account as String
    def region = data.region as String

    def credentials = accountCredentialsProvider.getCredentials(account)
    if (!credentials) {
      return
    }
    if (!(credentials instanceof NetflixAmazonCredentials)) {
      return
    }

    def cachingAgent = getWiredCachingAgent(credentials, region)
    cachingAgent.load()
  }

  private def getWiredCachingAgent(NetflixAmazonCredentials credentials, String region) {
    (Front50ApplicationCachingAgent) autowire(InfrastructureCachingAgentFactory.getFront50CachingAgent(credentials, region))
  }

  def autowire(obj) {
    applicationContext.autowireCapableBeanFactory.autowireBean obj
    obj
  }
}
