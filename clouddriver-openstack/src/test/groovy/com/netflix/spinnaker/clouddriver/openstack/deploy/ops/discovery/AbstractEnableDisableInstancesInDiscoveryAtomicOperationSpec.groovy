/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.discovery

import com.netflix.spinnaker.clouddriver.consul.config.ConsulConfig
import com.netflix.spinnaker.clouddriver.consul.deploy.ops.EnableDisableConsulInstance
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.instance.OpenstackInstancesDescription
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Unroll
class AbstractEnableDisableInstancesInDiscoveryAtomicOperationSpec extends Specification {

  private static final String ACCOUNT_NAME = 'myaccount'
  private static final INSTANCE_IDS = ['instance1', 'instance2', 'instance3']

  def credentials
  def description
  String region = 'region1'

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    initDescription()
  }

  def initDescription(boolean consulEnabled = true) {
    OpenstackClientProvider provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global: true)
    ConsulConfig consulConfig = Mock(ConsulConfig) {
      getEnabled() >> consulEnabled
      applyDefaults() >> {}
    }
    OpenstackNamedAccountCredentials credz = new OpenstackNamedAccountCredentials("name", "test", "main", "user", "pw", "tenant", "domain", "endpoint", [], false, "", new OpenstackConfigurationProperties.LbaasConfig(pollTimeout: 60, pollInterval: 5), consulConfig, null)
    OpenstackProviderFactory.createProvider(credz) >> { provider }
    credentials = new OpenstackCredentials(credz)
    description = new OpenstackInstancesDescription(credentials: credentials, region: region, instanceIds: INSTANCE_IDS, account: ACCOUNT_NAME)
  }

  def "should perform #opClass"() {
    given:
    GroovyMock(EnableDisableConsulInstance, global: true)
    @Subject def operation = opClass.newInstance(description)

    when:
    operation.operate([])

    then:
    INSTANCE_IDS.each {
      1 * credentials.provider.getIpForInstance(region, it) >> '10.0.0.0'
      1 * EnableDisableConsulInstance.operate(_, _, _) >> {}
    }

    where:
    opClass << [EnableInstancesInDiscoveryOperation, DisableInstancesInDiscoveryOperation]
  }

  def "should not perform with missing address #opClass"() {
    given:
    GroovyMock(EnableDisableConsulInstance, global: true)
    @Subject def operation = opClass.newInstance(description)

    when:
    operation.operate([])

    then:
    INSTANCE_IDS.each {
      1 * credentials.provider.getIpForInstance(region, it) >> ''
      0 * EnableDisableConsulInstance.operate(_, _, _) >> {}
    }

    where:
    opClass << [EnableInstancesInDiscoveryOperation, DisableInstancesInDiscoveryOperation]
  }
}
