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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.securitygroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.instance.OpenstackInstancesDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.securitygroup.OpenstackSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import spock.lang.Specification

class UpsertOpenstackSecurityGroupAtomicOperationSpec extends Specification {

  private static final String ACCOUNT_NAME = 'account'
  def credentials
  def provider

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackNamedAccountCredentials namedAccountCredentials = Mock(OpenstackNamedAccountCredentials)
    OpenstackProviderFactory.createProvider(namedAccountCredentials) >> { provider }
    credentials = new OpenstackCredentials(namedAccountCredentials)
  }

  def "upsert a security group"() {
    setup:
    def id = UUID.randomUUID().toString()
    def name = "name"
    def desc = "description"
    def description = new OpenstackSecurityGroupDescription(account: ACCOUNT_NAME, credentials: credentials, id: id, name: name, description: desc, rules: [])
    def operation = new UpsertOpenstackSecurityGroupAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * provider.upsertSecurityGroup(id, name, desc, [])
  }


}
