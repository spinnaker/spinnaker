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

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.SecurityGroup
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.mort.model.CacheService
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AmazonSecurityGroupOnDemandCacheUpdaterSpec extends Specification {

  @Subject
  AmazonSecurityGroupOnDemandCacheUpdater updater

  @Shared
  AccountCredentialsProvider accountCredentialsProvider

  @Shared
  AmazonClientProvider amazonClientProvider

  @Shared
  CacheService cacheService

  def setup() {
    cacheService = Mock(CacheService)
    accountCredentialsProvider = Mock(AccountCredentialsProvider)
    amazonClientProvider = Mock(AmazonClientProvider)
    updater = new AmazonSecurityGroupOnDemandCacheUpdater(accountCredentialsProvider: accountCredentialsProvider,
        amazonClientProvider: amazonClientProvider, cacheService: cacheService)
  }

  void "should retrieve new security group from AWS and invoke the caching agent"() {
    setup:
      def ec2 = Mock(AmazonEC2)

    when:
      updater.handle([securityGroupName: securityGroupName, account: "test", region: region])

    then:
      1 * accountCredentialsProvider.getCredentials(_) >> credentials
      1 * amazonClientProvider.getAmazonEC2(credentials, region) >> ec2
      1 * ec2.describeSecurityGroups() >> {
        new DescribeSecurityGroupsResult(securityGroups: [securityGroup])
      }

    where:
      credentials = Stub(NetflixAmazonCredentials)
      securityGroupName = "sg-12345a"
      securityGroup = new SecurityGroup()
      region = "us-east-1"
  }
}
