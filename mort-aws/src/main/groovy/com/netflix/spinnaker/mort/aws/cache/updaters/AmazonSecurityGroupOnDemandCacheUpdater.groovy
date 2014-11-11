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


package com.netflix.spinnaker.mort.aws.cache.updaters

import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.mort.aws.cache.AmazonSecurityGroupCachingAgent
import com.netflix.spinnaker.mort.model.CacheService
import com.netflix.spinnaker.mort.model.OnDemandCacheUpdater
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AmazonSecurityGroupOnDemandCacheUpdater implements OnDemandCacheUpdater {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  CacheService cacheService

  @Override
  boolean handles(String type) {
    type == "AmazonSecurityGroup"
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

    def ec2 = amazonClientProvider.getAmazonEC2(credentials, region)
    def cachingAgent = new AmazonSecurityGroupCachingAgent(account: credentials.name, region: region, ec2: ec2,
        cacheService: cacheService)

    // Because of the need to support dual-mode (EC2-Classic/VPC) cloud presence, we need to just refresh the whole
    // cache for this region. VPC & EC2-Classic Security Groups need to be described in different ways, and we don't
    // have enough detail at this point to ascertain what that is.
    println "Force updating ${account}/${region} Amazon Security Group Cache..."
    cachingAgent.call()
  }
}
