package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.loadbalancer

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.DeleteDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.loadbalancer.DeleteDcosLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.DcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Subject

class DeleteDcosLoadBalancerAtomicOperationConverterSpec extends BaseSpecification {
  private static final LOAD_BALANCER_NAME = "external"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  @Subject
  DeleteDcosLoadBalancerAtomicOperationConverter converter

  @Shared
  DcosAccountCredentials mockCredentials = Mock()

  def setupSpec() {
    converter = new DeleteDcosLoadBalancerAtomicOperationConverter(Mock(DcosClientProvider), Mock(DcosDeploymentMonitor))
    converter.setObjectMapper(mapper)
    converter.accountCredentialsProvider = Stub(AccountCredentialsProvider) {
      getCredentials(DEFAULT_ACCOUNT) >> mockCredentials
    }
  }

  void "deleteDcosLoadBalancerAtomicOperationConverter type returns DeleteDcosLoadBalancerAtomicOperationDescription and DeleteDcosLoadBalancerAtomicOperation"() {
    setup:
    def input = [loadBalancerName: LOAD_BALANCER_NAME,
                 dcosCluster     : DEFAULT_REGION,
                 account         : DEFAULT_ACCOUNT]
    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof DeleteDcosLoadBalancerAtomicOperationDescription
    description.loadBalancerName == LOAD_BALANCER_NAME
    description.credentials == mockCredentials

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof DeleteDcosLoadBalancerAtomicOperation
  }
}
