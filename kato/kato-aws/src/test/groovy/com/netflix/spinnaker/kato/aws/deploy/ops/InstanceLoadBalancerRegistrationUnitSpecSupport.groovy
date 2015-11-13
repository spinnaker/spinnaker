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

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.InstanceLoadBalancerRegistrationDescription
import com.netflix.spinnaker.kato.aws.services.AsgService
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import spock.lang.Shared
import spock.lang.Specification

class InstanceLoadBalancerRegistrationUnitSpecSupport extends Specification {
  @Shared
  def description = new InstanceLoadBalancerRegistrationDescription([
      asgName: "oort-main-v000",
      region: "us-west-1",
      credentials: TestCredential.named('test')
  ])

  @Shared
  AbstractInstanceLoadBalancerRegistrationAtomicOperation op

  @Shared
  AsgService asgService

  @Shared
  AmazonElasticLoadBalancing loadBalancing

  def setup() {
    asgService = Mock(AsgService)
    loadBalancing = Mock(AmazonElasticLoadBalancing)
    wireOpMocks(op)
  }

  def wireOpMocks(AbstractInstanceLoadBalancerRegistrationAtomicOperation op) {
    def rsp = Mock(RegionScopedProviderFactory.RegionScopedProvider)
    rsp.getAsgService() >> asgService

    def rspf = Mock(RegionScopedProviderFactory)
    rspf.forRegion(_, _) >> rsp
    op.regionScopedProviderFactory = rspf

    def provider = Mock(AmazonClientProvider) {
      getAmazonElasticLoadBalancing(_, _, true) >> loadBalancing
    }
    op.amazonClientProvider = provider
  }
}
