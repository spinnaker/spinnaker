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

package com.netflix.spinnaker.oort.config

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.netflix.bluespar.amazon.security.AmazonClientProvider
import com.netflix.bluespar.amazon.security.AmazonCredentials
import com.netflix.spinnaker.oort.remoting.AggregateRemoteResource
import com.netflix.spinnaker.oort.remoting.DiscoverableRemoteResource
import com.netflix.spinnaker.oort.remoting.RemoteResource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AmazonConfig {
  @Value('${discovery.url.format:#{null}}')
  String discoveryUrlFormat

  @Bean
  RemoteResource front50() {
    def appName = "front50"
    new DiscoverableRemoteResource(appName, String.format(discoveryUrlFormat, "us-west-1", appName))
  }

  @Bean
  AggregateRemoteResource edda() {
    def appName = "entrypoints_v2"
    def remoteResources = ["us-east-1", "us-west-1", "us-west-2", "eu-west-1"].collectEntries {
      [(it): new DiscoverableRemoteResource(appName, String.format(discoveryUrlFormat, it, appName))]
    }
    new AggregateRemoteResource(remoteResources)
  }

  @Bean
  AmazonClientProvider amazonClientProvider(@Value('${edda.url.format:#{null}}') String edda) {
    new AmazonClientProvider(edda)
  }

  @Bean
  AmazonCredentials amazonCredentials(AWSCredentialsProvider awsCredentialsProvider, @Value('${environment:#{null}}') String environment) {
    AWSCredentials awsCredentials
    try {
      awsCredentials = awsCredentialsProvider.credentials
    } catch (IGNORE) {
      // "Read-only" mode
      awsCredentials = new BasicAWSCredentials("foo", "bar")
    }
    new AmazonCredentials(awsCredentials, environment)
  }
}
