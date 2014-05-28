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



package com.netflix.amazoncomponents.security

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.ec2.AmazonEC2
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import spock.lang.Specification

class AmazonClientProviderSpec extends Specification {

  void "client proxies to edda when available"() {
    setup:
    def mockHttp = Mock(HttpClient)
    def provider = new AmazonClientProvider("edda", mockHttp)

    when:
    def client = provider.getAutoScaling(new AmazonCredentials(Mock(AWSCredentials), "bar"), "us-east-1")
    client.describeAutoScalingGroups()

    then:
    client instanceof AmazonAutoScaling
    1 * mockHttp.execute(_) >> {
      def mock = Mock(HttpResponse)
      def entity = Mock(HttpEntity)
      entity.getContent() >> { new ByteArrayInputStream('[{ "autoScalingGroupName": "my-app-v000" }]'.bytes) }
      mock.getEntity() >> entity
      mock
    }
  }

  void "edda requests handle parameters from request objects"() {
    setup:
    def asgName = "foo"
    def mockHttp = Mock(HttpClient)
    def provider = new AmazonClientProvider("edda", mockHttp)

    when:
    def client = provider.getAutoScaling(new AmazonCredentials(Mock(AWSCredentials), "bar"), "us-east-1")
    client.describeAutoScalingGroups()

    then:
    client instanceof AmazonAutoScaling
    1 * mockHttp.execute(_) >> { HttpGet get ->
      assert get.URI.rawPath.endsWith("_expand")
      def mock = Mock(HttpResponse)
      def entity = Mock(HttpEntity)
      entity.getContent() >> { new ByteArrayInputStream('[{ "autoScalingGroupName": "my-app-v000" }]'.bytes) }
      mock.getEntity() >> entity
      mock
    }

    when:
    client.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(asgName))

    then:
    client instanceof AmazonAutoScaling
    1 * mockHttp.execute(_) >> { HttpGet get ->
      assert get.URI.rawPath.endsWith(asgName)
      def mock = Mock(HttpResponse)
      def entity = Mock(HttpEntity)
      entity.getContent() >> { new ByteArrayInputStream('{ "autoScalingGroupName": "my-app-v000" }'.bytes) }
      mock.getEntity() >> entity
      mock
    }
  }

  void "client goes directly to amazon when edda is unavailable"() {
    setup:
    def provider = new AmazonClientProvider()
    def count = 0
    AmazonEC2.metaClass.describeSecurityGroups = { count++ }

    when:
    def client = provider.getAmazonEC2(new AmazonCredentials(Mock(AWSCredentials), "bar"), "us-east-1")
    client.describeSecurityGroups()

    then:
    count
  }

  void "unmapped describe calls fall back to aws client"() {
    setup:
    def provider = new AmazonClientProvider("edda")
    def count = 0
    AmazonEC2.metaClass.describeAccountAttributes = { count++ }

    when:
    def client = provider.getAmazonEC2(new AmazonCredentials(Mock(AWSCredentials), "bar"), "us-east-1")
    client.describeAccountAttributes()

    then:
    count
  }

  void "describe call with no keys calls for the full list"() {
    setup:
    def mockHttp = Mock(HttpClient)
    def provider = new AmazonClientProvider("edda", mockHttp)

    when:
    def client = provider.getAutoScaling(new AmazonCredentials(Mock(AWSCredentials), "bar"), "us-east-1")
    client.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest())

    then:
    client instanceof AmazonAutoScaling
    1 * mockHttp.execute(_) >> { HttpGet get ->
      assert get.URI.rawPath.endsWith("_expand")
      def mock = Mock(HttpResponse)
      def entity = Mock(HttpEntity)
      entity.getContent() >> { new ByteArrayInputStream('[{ "autoScalingGroupName": "my-app-v000" }]'.bytes) }
      mock.getEntity() >> entity
      mock
    }
  }
}
