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

package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.servergroup

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.PollingDcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.ResizeDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup.ResizeDcosServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import mesosphere.dcos.client.DCOS
import spock.lang.Subject

class ResizeDcosServerGroupAtomicOperationConverterSpec extends BaseSpecification {

  DCOS dcosClient = Mock(DCOS)
  PollingDcosDeploymentMonitor deploymentMonitor = Mock(PollingDcosDeploymentMonitor)

  DcosAccountCredentials testCredentials = defaultCredentialsBuilder().build()

  DcosClientProvider dcosClientProvider = Stub(DcosClientProvider) {
    getDcosClient(testCredentials, DEFAULT_REGION) >> dcosClient
  }

  AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider) {
    getCredentials(testCredentials.account) >> testCredentials
  }

  @Subject
  AbstractAtomicOperationsCredentialsSupport atomicOperationConverter = new ResizeDcosServerGroupAtomicOperationConverter(dcosClientProvider, deploymentMonitor)

  void 'convertDescription should return a valid ResizeDcosServerGroupDescription'() {
    given:
    atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
    atomicOperationConverter.objectMapper = new ObjectMapper()
    Map input = [
      account: DEFAULT_ACCOUNT,
      cluster: DEFAULT_REGION,
      serverGroupName: 'api',
      targetSize: 1
    ]

    when:
    def description = atomicOperationConverter.convertDescription(input)

    then:
    noExceptionThrown()
    description != null
    description instanceof ResizeDcosServerGroupDescription
  }

  void 'convertOperation should return a ResizeDcosServerGroupAtomicOperation with ResizeDcosServerGroupDescription'() {
    given:
    atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
    atomicOperationConverter.objectMapper = new ObjectMapper()
    Map input = [
      account: DEFAULT_ACCOUNT,
      cluster: DEFAULT_REGION,
      serverGroupName: 'api',
      targetSize: 1
    ]

    when:
    AtomicOperation atomicOperation = atomicOperationConverter.convertOperation(input)

    then:
    noExceptionThrown()
    atomicOperation != null
    atomicOperation instanceof ResizeDcosServerGroupAtomicOperation
  }
}
