package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.servergroup

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup.DeployDcosServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.mapper.DeployDcosServerGroupDescriptionToAppMapper
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import mesosphere.dcos.client.DCOS
import spock.lang.Subject

class DeployDcosServerGroupAtomicOperationConverterSpec extends BaseSpecification {

  DCOS dcosClient = Mock(DCOS)
  DeployDcosServerGroupDescriptionToAppMapper dcosServerGroupDescriptionToAppMapper = Mock(DeployDcosServerGroupDescriptionToAppMapper)

  DcosAccountCredentials testCredentials = defaultCredentialsBuilder().build()

  DcosClientProvider dcosClientProvider = Stub(DcosClientProvider) {
    getDcosClient(testCredentials, DEFAULT_REGION) >> dcosClient
  }

  AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider) {
    getCredentials(testCredentials.name) >> testCredentials
  }

  @Subject
  AbstractAtomicOperationsCredentialsSupport atomicOperationConverter = new DeployDcosServerGroupAtomicOperationConverter(dcosClientProvider, dcosServerGroupDescriptionToAppMapper)

  void 'convertDescription should return a valid DeployDcosServerGroupDescription'() {
    given:
    atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
    atomicOperationConverter.objectMapper = new ObjectMapper()
    Map input = [
      account: 'test',
      cluster: "us-test-1",
      serverGroupName: 'test',
      stack: "dev",
      detail: "",
      instances: 1,
      cpus: 1,
      mem: 128,
      disk: 0,
      gpus: 0,
      container: [
        docker: [
          image: 'test',
          forcePullImage: false,
          privileged: false,
          portMappings: [
            [
              containerPort: 8080,
              protocol     : "tcp"
            ]
          ],
          network: "BRIDGE"
        ]
      ]
    ]

    when:
    def description = atomicOperationConverter.convertDescription(input)

    then:
    noExceptionThrown()
    description != null
    description instanceof DeployDcosServerGroupDescription
  }

  void 'convertOperation should return a DeployDcosServerGroupAtomicOperation with DeployDcosServerGroupDescription'() {
    given:
    atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
    atomicOperationConverter.objectMapper = new ObjectMapper()
    Map input = [
      account: 'test',
      cluster: "us-test-1",
      serverGroupName: 'test',
      stack: "dev",
      detail: "",
      instances: 1,
      cpus: 1,
      mem: 128,
      disk: 0,
      gpus: 0,
      container: [
        docker: [
          image: 'test',
          forcePullImage: false,
          privileged: false,
          portMappings: [
            [
              containerPort: 8080,
              protocol     : "tcp"
            ]
          ],
          network: "BRIDGE"
        ]
      ]
    ]

    when:
    AtomicOperation atomicOperation = atomicOperationConverter.convertOperation(input)

    then:
    noExceptionThrown()
    atomicOperation != null
    atomicOperation instanceof DeployDcosServerGroupAtomicOperation
  }
}
