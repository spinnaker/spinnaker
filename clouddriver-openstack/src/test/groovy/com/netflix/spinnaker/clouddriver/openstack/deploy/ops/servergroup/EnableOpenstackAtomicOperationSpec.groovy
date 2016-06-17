/*
* Copyright 2016 Veritas Technologies LLC.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.OpenstackServerGroupAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.network.ext.LbPool
import spock.lang.Specification
import spock.lang.Subject

class EnableOpenstackAtomicOperationSpec extends Specification {

  private static final String ACCOUNT_NAME = 'myaccount'
  private static final STACK = "stack"
  private static final REGION = "region"

  def credentials
  def description
  def provider

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackNamedAccountCredentials creds = Mock(OpenstackNamedAccountCredentials)
    OpenstackProviderFactory.createProvider(creds) >> { provider }
    credentials = new OpenstackCredentials(creds)
    description = new OpenstackServerGroupAtomicOperationDescription(serverGroupName: STACK, region: REGION, credentials: credentials)
  }

  def "enable stack adds instances to load balancer"() {
    given:
    @Subject def operation = new EnableOpenstackAtomicOperation(description)
    List<String> ids = ["foo", "bar"]
    LbPool mockPool = Mock(LbPool)
    List<? extends LbPool> pools = [mockPool]

    when:
    operation.operate([])

    then:
    1 * provider.getInstanceIdsForStack(description.region, description.serverGroupName) >> ids
    1 * provider.getAllLoadBalancerPools(description.region) >> pools
    pools.each {
      ids.each {
        1 * provider.getIpForInstance(description.region, it)
        1 * provider.getInternalLoadBalancerPort(mockPool)
        1 * provider.addMemberToLoadBalancerPool(description.region, _, _, _, _)
      }
    }
    noExceptionThrown()
  }
}
