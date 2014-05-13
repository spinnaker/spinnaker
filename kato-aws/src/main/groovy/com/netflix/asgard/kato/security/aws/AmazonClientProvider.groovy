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

package com.netflix.asgard.kato.security.aws

import com.amazonaws.AmazonServiceException
import com.amazonaws.AmazonWebServiceClient
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient
import com.amazonaws.services.autoscaling.model.*
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.*
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient
import com.amazonaws.services.route53.AmazonRoute53
import com.amazonaws.services.route53.AmazonRoute53Client
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.asgard.kato.data.aws.AmazonObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

import javax.annotation.PostConstruct
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

/**
 * Provider of Amazon Clients. If an edda host is configured in the format: http://edda.<region>.<environment>.domain.com, its cache will be used for lookup calls.
 *
 * @author Dan Woods
 */
@Component
class AmazonClientProvider {

  @Value('${edda.host.format:null}')
  String edda

  RestTemplate restTemplate
  ObjectMapper objectMapper

  @PostConstruct
  void init() {
    this.restTemplate = new RestTemplate()
    this.objectMapper = new AmazonObjectMapper()
  }

  AmazonEC2 getAmazonEC2(AmazonCredentials amazonCredentials, String region) {
    def client = new AmazonEC2Client(amazonCredentials.credentials)
    if (!edda) {
      return client
    } else {
      return (AmazonEC2) java.lang.reflect.Proxy.newProxyInstance(getClass().classLoader, [AmazonEC2] as Class[], getInvocationHandler(client, region, amazonCredentials))
    }
  }

  AmazonAutoScaling getAutoScaling(AmazonCredentials amazonCredentials, String region) {
    def client = new AmazonAutoScalingClient(amazonCredentials.credentials)
    if (!edda) {
      return client
    } else {
      return (AmazonAutoScaling) java.lang.reflect.Proxy.newProxyInstance(getClass().classLoader, [AmazonAutoScaling] as Class[], getInvocationHandler(client, region, amazonCredentials))
    }
  }

  AmazonRoute53 getAmazonRoute53(AmazonCredentials credentials, String region) {
    def amazonRoute53 = new AmazonRoute53Client(credentials.credentials)
    if (region) {
      amazonRoute53.setRegion(Region.getRegion(Regions.fromName(region)))
    }
    amazonRoute53
  }

  AmazonElasticLoadBalancing getAmazonElasticLoadBalancing(AmazonCredentials credentials, String region) {
    def amazonElasticLoadBalancing = new AmazonElasticLoadBalancingClient(credentials.credentials)
    if (region) {
      amazonElasticLoadBalancing.setRegion(Region.getRegion(Regions.fromName(region)))
    }
    amazonElasticLoadBalancing
  }

  private GeneralAmazonClientInvocationHandler getInvocationHandler(AmazonWebServiceClient client, String region, AmazonCredentials amazonCredentials) {
    if (region) {
      client.setRegion(Region.getRegion(Regions.fromName(region)))
    }
    new GeneralAmazonClientInvocationHandler(client, String.format(edda, region, amazonCredentials.environment), restTemplate, objectMapper)
  }

  static class GeneralAmazonClientInvocationHandler implements InvocationHandler {

    private final String edda
    private final RestTemplate restTemplate
    private final AmazonWebServiceClient delegate
    private final ObjectMapper objectMapper

    GeneralAmazonClientInvocationHandler(AmazonWebServiceClient delegate, String edda, RestTemplate restTemplate, ObjectMapper objectMapper) {
      this.edda = edda
      this.restTemplate = restTemplate
      this.objectMapper = objectMapper
      this.delegate = delegate
    }

    @Override
    Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      def metaMethod = getMetaClass().getMetaMethod(method.name, args)
      if (metaMethod) {
        return metaMethod.invoke(this, args)
      } else {
        return method.invoke(delegate, args)
      }
    }

    DescribeLaunchConfigurationsResult describeLaunchConfigurations(DescribeLaunchConfigurationsRequest request = null) {
      new DescribeLaunchConfigurationsResult().withLaunchConfigurations(describe(request, "launchConfigurationNames", "launchConfigurations", LaunchConfiguration,
        new TypeReference<List<LaunchConfiguration>>() {}))
    }

    DescribeSecurityGroupsResult describeSecurityGroups(DescribeSecurityGroupsRequest request = null) {
      new DescribeSecurityGroupsResult().withSecurityGroups(describe(request, "groupIds", "securityGroups", SecurityGroup, new TypeReference<List<SecurityGroup>>() {}))
    }

    DescribeSubnetsResult describeSubnets(DescribeSubnetsRequest request = null) {
      new DescribeSubnetsResult().withSubnets(describe(request, "subnetIds", "subnets", Subnet, new TypeReference<List<Subnet>>() {}))
    }

    DescribeAutoScalingGroupsResult describeAutoScalingGroups(DescribeAutoScalingGroupsRequest request = null) {
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups(describe(request, "autoScalingGroupNames", "autoScalingGroups", AutoScalingGroup, new TypeReference<List<AutoScalingGroup>>() {}))
    }

    def <T> T describe(request, String idKey, String object, Class singleType, TypeReference<T> collectionType) {
      try {
        if (request) {
          request."$idKey".collect { String id ->
            objectMapper.readValue(getJson(object, id) as String, singleType)
          }
        } else {
          objectMapper.readValue(getJson(object) as String, collectionType)
        }
      } catch (HttpClientErrorException e) {
        if (e.statusCode == HttpStatus.NOT_FOUND) {
          def ex = new AmazonServiceException("400 Bad Request -- Edda could not find one of the keys requested.", e)
          ex.statusCode = 400
          ex.serviceName = delegate.serviceName
          ex.errorType = AmazonServiceException.ErrorType.Unknown
          throw ex
        } else {
          throw e
        }
      }
    }

    private String getJson(String objectName, String key = null) {
      if (key) {
        restTemplate.getForObject("$edda/REST/v2/aws/$objectName/$key", String)
      } else {
        restTemplate.getForObject("$edda/REST/v2/aws/$objectName;_expand", String)
      }
    }
  }
}
