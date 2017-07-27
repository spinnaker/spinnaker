package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.job

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.job.RunDcosJobDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.job.RunDcosJobAtomicOperation
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import mesosphere.dcos.client.DCOS
import spock.lang.Subject

class RunDcosJobAtomicOperationConverterSpec extends BaseSpecification {

    DCOS dcosClient = Mock(DCOS)

    DcosAccountCredentials testCredentials = defaultCredentialsBuilder().build()

    DcosClientProvider dcosClientProvider = Stub(DcosClientProvider) {
        getDcosClient(testCredentials, DEFAULT_REGION) >> dcosClient
    }

    AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider) {
        getCredentials(testCredentials.name) >> testCredentials
    }

    @Subject
    AbstractAtomicOperationsCredentialsSupport atomicOperationConverter = new RunDcosJobAtomicOperationConverter(dcosClientProvider)

    void 'convertDescription should return a valid RunDcosJobDescription'() {
        given:
        atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
        atomicOperationConverter.objectMapper = new ObjectMapper()
        def input = [
                account: "test",
                cluster: "us-test-1",
                general: [id: "testjob"]
        ]

        when:
        def description = atomicOperationConverter.convertDescription(input)

        then:
        noExceptionThrown()
        description != null
        description instanceof RunDcosJobDescription
    }

    void 'convertOperation should return a RunDcosJobAtomicOperation with a RunDcosJobDescription'() {
        given:
        atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
        atomicOperationConverter.objectMapper = new ObjectMapper()
        def input = [
                account: "test",
                cluster: "us-test-1",
                general: [id: "testjob"]
        ]

        when:
        def atomicOperation = atomicOperationConverter.convertOperation(input)

        then:
        noExceptionThrown()
        atomicOperation != null
        atomicOperation instanceof RunDcosJobAtomicOperation
    }
}
