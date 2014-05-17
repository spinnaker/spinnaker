package com.netflix.bluespar.amazon

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.ec2.AmazonEC2
import com.netflix.bluespar.amazon.security.AmazonClientProvider
import com.netflix.bluespar.amazon.security.AmazonCredentials
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import spock.lang.Specification

class SecurityAmazonClientProviderSpec extends Specification {

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
}
