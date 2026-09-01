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

package com.netflix.spinnaker.clouddriver.aws.security

import com.netflix.spinnaker.clouddriver.aws.AwsConfigurationProperties
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.ec2.Ec2Client
import spock.lang.Shared
import spock.lang.Specification

class AmazonClientProviderSpec extends Specification {

  @Shared def credentialsProvider = Stub(AwsCredentialsProvider) {
      resolveCredentials() >> AwsBasicCredentials.create('foo', 'bar')
  }

  @Shared AwsConfigurationProperties awsConfigurationProperties = new AwsConfigurationProperties()
  @Shared NetflixAmazonCredentials credentials = new NetflixAmazonCredentials(TestCredential.named('test'), credentialsProvider, awsConfigurationProperties)

  void "getAmazonEC2V2 builds a client directly against AWS"() {
    setup:
    def provider = new AmazonClientProvider()

    when:
    def client = provider.getAmazonEC2V2(credentials, "us-east-1")

    then:
    client instanceof Ec2Client
  }

  void "getAutoScalingV2 builds a client directly against AWS"() {
    setup:
    def provider = new AmazonClientProvider()

    when:
    def client = provider.getAutoScalingV2(credentials, "us-east-1")

    then:
    client instanceof AutoScalingClient
  }
}
