package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.instance

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.converters.instances.TerminateDcosInstancesAndDecrementAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.instance.TerminateDcosInstancesAndDecrementDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.instance.TerminateDcosInstancesAndDecrementAtomicOperation
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import mesosphere.dcos.client.DCOS
import spock.lang.Subject

class TerminateDcosInstancesAndDecrementAtomicOperationConverterSpec extends BaseSpecification {

    DCOS dcosClient = Mock(DCOS)

    DcosAccountCredentials testCredentials = defaultCredentialsBuilder().build()

    DcosClientProvider dcosClientProvider = Stub(DcosClientProvider) {
        getDcosClient(testCredentials, DEFAULT_REGION) >> dcosClient
    }

    AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider) {
        getCredentials(testCredentials.name) >> testCredentials
    }

    @Subject
    AbstractAtomicOperationsCredentialsSupport atomicOperationConverter = new TerminateDcosInstancesAndDecrementAtomicOperationConverter(dcosClientProvider)

    void 'convertDescription should return a valid TerminateDcosInstancesAndDecrementDescription'() {
        given:
        atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
        atomicOperationConverter.objectMapper = new ObjectMapper()
        def input = [
                account: "test",
                region: "default",
                instanceIds: ["TASK ONE"]
        ]

        when:
        def description = atomicOperationConverter.convertDescription(input)

        then:
        noExceptionThrown()
        description != null
        description instanceof TerminateDcosInstancesAndDecrementDescription
    }

    void 'convertOperation should return a TerminateDcosInstancesAndDecrementAtomicOperation with TerminateDcosInstancesAndDecrementDescription'() {
        given:
        atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
        atomicOperationConverter.objectMapper = new ObjectMapper()
        def input = [
                account: "test",
                region: "default",
                instanceIds: ["TASK ONE"]
        ]

        when:
        def atomicOperation = atomicOperationConverter.convertOperation(input)

        then:
        noExceptionThrown()
        atomicOperation != null
        atomicOperation instanceof TerminateDcosInstancesAndDecrementAtomicOperation
    }
}
