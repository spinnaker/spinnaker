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

package com.netflix.spinnaker.kato.aws.deploy.ops

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.EnableDisableAsgDescription
import com.netflix.spinnaker.kato.aws.deploy.description.EnableDisableInstanceDiscoveryDescription
import com.netflix.spinnaker.kato.aws.deploy.ops.discovery.DiscoverySupport
import com.netflix.spinnaker.kato.aws.services.AsgService
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Specification

abstract class EnableDisableAtomicOperationUnitSpecSupport extends Specification {
  @Shared
  def description = new EnableDisableAsgDescription([
      asgName    : "kato-main-v000",
      regions    : ["us-west-1"],
      credentials: TestCredential.named('foo')
  ])

  @Shared
  AbstractEnableDisableAtomicOperation op

  @Shared
  Task task

  @Shared
  RestTemplate restTemplate

  @Shared
  AsgService asgService

  @Shared
  AmazonElasticLoadBalancing loadBalancing

  def setup() {
    task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)
    restTemplate = Mock(RestTemplate)
    asgService = Mock(AsgService)
    loadBalancing = Mock(AmazonElasticLoadBalancing)
    wireOpMocks(op)
  }

  def wireOpMocks(AbstractEnableDisableAtomicOperation op) {
    def regionScopedProviderFactory = Mock(RegionScopedProviderFactory) {
      _ * getAmazonClientProvider() >> {
        return Mock(AmazonClientProvider)
      }
      _ * forRegion(_, _) >> {
        return Mock(RegionScopedProviderFactory.RegionScopedProvider)
      }
    }

    op.discoverySupport = new DiscoverySupport(
      restTemplate: restTemplate,
      regionScopedProviderFactory: regionScopedProviderFactory
    )
    op.discoverySupport.metaClass.verifyInstanceAndAsgExist = {
      AmazonEC2 amazonEC2, AsgService asgService, String instanceId, String asgName -> true
    }

    def rspf = Mock(RegionScopedProviderFactory)
    def rsp = Mock(RegionScopedProviderFactory.RegionScopedProvider)
    rsp.getAsgService() >> asgService
    rspf.forRegion(_, _) >> rsp
    op.regionScopedProviderFactory = rspf

    def provider = Mock(AmazonClientProvider)
    provider.getAmazonElasticLoadBalancing(_, _) >> loadBalancing
    op.amazonClientProvider = provider
  }
}
