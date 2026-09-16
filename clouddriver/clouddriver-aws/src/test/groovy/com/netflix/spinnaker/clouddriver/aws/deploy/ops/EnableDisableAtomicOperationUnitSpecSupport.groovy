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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.elasticloadbalancing.ElasticLoadBalancingClient
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.EnableDisableAsgDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.discovery.AwsEurekaSupport
import com.netflix.spinnaker.clouddriver.eureka.api.Eureka
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.EurekaSupportConfigurationProperties
import spock.lang.Shared
import spock.lang.Specification

abstract class EnableDisableAtomicOperationUnitSpecSupport extends Specification {
  @Shared
  def description = new EnableDisableAsgDescription([
      asgs       : [[
        serverGroupName: "kato-main-v000",
        region         : "us-west-1"
      ]],
      credentials: TestCredential.named('foo')
  ])

  @Shared
  AbstractEnableDisableAtomicOperation op

  @Shared
  Task task

  @Shared
  AsgService asgService

  @Shared
  Eureka eureka

  @Shared
  ElasticLoadBalancingClient loadBalancing

  @Shared
  ElasticLoadBalancingV2Client loadBalancingV2

  @Shared
  Ec2Client amazonEc2

  def setup() {
    task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)
    eureka = Mock(Eureka)
    asgService = Mock(AsgService)
    loadBalancing = Mock(ElasticLoadBalancingClient)
    loadBalancingV2 = Mock(ElasticLoadBalancingV2Client)
    amazonEc2 = Mock(Ec2Client)
    wireOpMocks(op)
  }

  def wireOpMocks(AbstractEnableDisableAtomicOperation op) {
    def regionScopedProviderFactory = Stub(RegionScopedProviderFactory) {
      forRegion(_, _) >> {
        return Stub(RegionScopedProviderFactory.RegionScopedProvider) {
          getAsgService() >> asgService
          getAmazonElasticLoadBalancingClassicV2() >> loadBalancing
          getElasticLoadBalancingV2Client() >> loadBalancingV2
          getEureka() >> eureka
          getAmazonEC2() >> amazonEc2
        }
      }
    }

    op.discoverySupport = new AwsEurekaSupport() {
      @Override
      boolean verifyInstanceAndAsgExist(Object credentials, String region, String instanceId, String asgName) {
        true
      }
    }
    op.discoverySupport.regionScopedProviderFactory = regionScopedProviderFactory
    op.discoverySupport.eurekaSupportConfigurationProperties = new EurekaSupportConfigurationProperties()
    op.discoverySupport.eurekaSupportConfigurationProperties.retryIntervalMillis = 0
    op.discoverySupport.eurekaSupportConfigurationProperties.throttleMillis = 0
    op.regionScopedProviderFactory = regionScopedProviderFactory
  }
}
