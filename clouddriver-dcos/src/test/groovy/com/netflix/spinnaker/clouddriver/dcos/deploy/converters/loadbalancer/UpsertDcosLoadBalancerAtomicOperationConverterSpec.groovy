/*
 * Copyright 2018 Cerner Corporation
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

package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.loadbalancer

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosConfigurationProperties
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.UpsertDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.loadbalancer.UpsertDcosLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.DcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Subject

class UpsertDcosLoadBalancerAtomicOperationConverterSpec extends BaseSpecification {
  private static final LOAD_BALANCER_NAME = "external"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  @Subject
  UpsertDcosLoadBalancerAtomicOperationConverter converter

  @Shared
  DcosAccountCredentials mockCredentials = Mock()

  def setupSpec() {
    converter = new UpsertDcosLoadBalancerAtomicOperationConverter(Mock(DcosClientProvider), Mock(DcosDeploymentMonitor), Mock(DcosConfigurationProperties))
    converter.setObjectMapper(mapper)
    converter.accountCredentialsProvider = Stub(AccountCredentialsProvider) {
      getCredentials(DEFAULT_ACCOUNT) >> mockCredentials
    }
  }

  void "upsertDcosLoadBalancerAtomicOperationConverter type returns UpsertDcosLoadBalancerAtomicOperationDescription and UpsertDcosLoadBalancerAtomicOperation"() {
    setup:
    def input = [name                 : LOAD_BALANCER_NAME,
                 account              : DEFAULT_ACCOUNT,
                 dcosCluster          : DEFAULT_REGION,
                 cpus                 : 0.5,
                 mem                  : 256,
                 instances            : 2,
                 bindHttpHttps        : true,
                 acceptedResourceRoles: ["resource1"],
                 portRange            : [
                         protocol: "tcp",
                         minPort : 10000,
                         maxPort : 20000,
                 ]
    ]
    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof UpsertDcosLoadBalancerAtomicOperationDescription
    description.name == LOAD_BALANCER_NAME
    description.credentials == mockCredentials
    description.cpus == 0.5d
    description.mem == 256
    description.instances == 2
    description.bindHttpHttps
    description.acceptedResourceRoles == ["resource1"]
    description.portRange.protocol == "tcp"
    description.portRange.minPort == 10000
    description.portRange.maxPort == 20000

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof UpsertDcosLoadBalancerAtomicOperation
  }
}
