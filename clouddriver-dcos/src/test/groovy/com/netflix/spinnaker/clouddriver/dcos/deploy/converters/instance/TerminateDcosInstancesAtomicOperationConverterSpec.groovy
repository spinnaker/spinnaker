package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.instance

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.converters.instances.TerminateDcosInstancesAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.instance.TerminateDcosInstancesDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.instance.TerminateDcosInstancesAtomicOperation
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import mesosphere.dcos.client.DCOS
import spock.lang.Subject

class TerminateDcosInstancesAtomicOperationConverterSpec extends BaseSpecification {

    DCOS dcosClient = Mock(DCOS)

    DcosAccountCredentials testCredentials = defaultCredentialsBuilder().build()

    DcosClientProvider dcosClientProvider = Stub(DcosClientProvider) {
        getDcosClient(testCredentials, DEFAULT_REGION) >> dcosClient
    }

    AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider) {
        getCredentials(testCredentials.name) >> testCredentials
    }

    @Subject
    AbstractAtomicOperationsCredentialsSupport atomicOperationConverter = new TerminateDcosInstancesAtomicOperationConverter(dcosClientProvider)

    void 'convertDescription should return a valid TerminateDcosInstancesDescription'() {
        given:
        atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
        atomicOperationConverter.objectMapper = new ObjectMapper()
        def input = [
                account: "test",
                cluster: "us-test-1",
                appId: "test/dev/app-stack-detail-v000",
                hostId: "192.168.0.0",
                taskIds: ["TASK ONE"],
                force: false,
                wipe: false
        ]

        when:
        def description = atomicOperationConverter.convertDescription(input)

        then:
        noExceptionThrown()
        description != null
        description instanceof TerminateDcosInstancesDescription
    }

    void 'convertOperation should return a TerminateDcosInstancesAtomicOperation with TerminateDcosInstancesDescription'() {
        given:
        atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
        atomicOperationConverter.objectMapper = new ObjectMapper()
        def input = [
                account: "test",
                cluster: "us-test-1",
                appId: "test/dev/app-stack-detail-v000",
                hostId: "192.168.0.0",
                taskIds: ["TASK ONE"],
                force: false,
                wipe: false
        ]

        when:
        def atomicOperation = atomicOperationConverter.convertOperation(input)

        then:
        noExceptionThrown()
        atomicOperation != null
        atomicOperation instanceof TerminateDcosInstancesAtomicOperation
    }
}
