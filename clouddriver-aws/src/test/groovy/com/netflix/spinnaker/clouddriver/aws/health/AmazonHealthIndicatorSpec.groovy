/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.health

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeAccountAttributesResult
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.AmazonCredentials
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import org.springframework.boot.actuate.health.Status
import spock.lang.Specification

class AmazonHealthIndicatorSpec extends Specification {

  def "health fails when no aws credentials are available"() {
    setup:
    def holder = Mock(AccountCredentialsProvider)
    def creds = []
    holder.getAll() >> creds
    def indicator = new AmazonHealthIndicator(accountCredentialsProvider: holder)

    when:
    indicator.checkHealth()
    indicator.health()

    then:
    thrown AmazonHealthIndicator.AmazonCredentialsNotFoundException
  }

  def "health fails when amazon appears unreachable"() {
    setup:
    def creds = [credential('foo')]
    def holder = Stub(AccountCredentialsProvider) {
      getAll() >> creds
      getCredentials("foo") >> creds[0]
    }
    def mockEc2 = Stub(AmazonEC2) {
      describeAccountAttributes() >> { throw new AmazonServiceException("fail") }
    }
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAmazonEC2(_, _) >> mockEc2
    }
    def indicator = new AmazonHealthIndicator(accountCredentialsProvider: holder, amazonClientProvider: mockAmazonClientProvider)

    when:
    indicator.checkHealth()
    indicator.health()

    then:
    thrown AmazonHealthIndicator.AmazonUnreachableException
  }

  def "health succeeds when amazon is reachable"() {
    setup:
    def creds = [credential('foo')]
    def holder = Stub(AccountCredentialsProvider) {
      getAll() >> creds
      getCredentials("foo") >> creds[0]
    }
    def mockEc2 = Stub(AmazonEC2) {
      describeAccountAttributes() >> { Mock(DescribeAccountAttributesResult) }
    }
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAmazonEC2(_, _) >> mockEc2
    }
    def indicator = new AmazonHealthIndicator(accountCredentialsProvider: holder, amazonClientProvider: mockAmazonClientProvider)

    when:
    indicator.checkHealth()
    def health = indicator.health()

    then:
    health.status == Status.UP
  }

  NetflixAmazonCredentials credential(String name = 'foo') {
    new NetflixAmazonCredentials(name, '12345', 'key', [new AmazonCredentials.AWSRegion('us-east-1', ['us-east-1c', 'us-east-1d'])], null, false, null, false, null, false)
  }
}
