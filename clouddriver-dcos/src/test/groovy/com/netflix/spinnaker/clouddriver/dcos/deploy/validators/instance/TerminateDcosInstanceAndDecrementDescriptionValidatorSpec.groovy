package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.instance

import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.instance.TerminateDcosInstancesAndDecrementDescription
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Subject

class TerminateDcosInstanceAndDecrementDescriptionValidatorSpec extends BaseSpecification {
    private static final DESCRIPTION = "terminateDcosInstancesAndDecrementDescription"

    DcosAccountCredentials testCredentials = defaultCredentialsBuilder().build()

    AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider) {
        getCredentials(testCredentials.name) >> testCredentials
    }

    @Subject
    DescriptionValidator<TerminateDcosInstancesAndDecrementDescription> validator = new TerminateDcosInstanceAndDecrementDescriptionValidator(accountCredentialsProvider)

    void "validate should give errors when given an empty TerminateDcosInstancesAndDecrementDescription"() {
        setup:
            def description = new TerminateDcosInstancesAndDecrementDescription(credentials: null, region: null,  instanceIds: [])
            def errorsMock = Mock(Errors)
        when:
            validator.validate([], description, errorsMock)
        then:
            1 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
            1 * errorsMock.rejectValue("dcosCluster", "${DESCRIPTION}.dcosCluster.empty")
            1 * errorsMock.rejectValue("instanceIds", "${DESCRIPTION}.instanceIds.empty")
            0 * errorsMock._
    }

    void "validate should give no errors when given a TerminateDcosInstancesAndDecrementDescription with instanceId(s)"() {
        setup:
            def description = new TerminateDcosInstancesAndDecrementDescription(credentials: testCredentials,
                    dcosCluster: DEFAULT_REGION, instanceIds: ["TASK ONE"])
            def errorsMock = Mock(Errors)
        when:
            validator.validate([], description, errorsMock)
        then:
            0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
            0 * errorsMock.rejectValue("dcosCluster", "${DESCRIPTION}.dcosCluster.empty")
            0 * errorsMock.rejectValue("instanceIds", "${DESCRIPTION}.instanceIds.empty")
            0 * errorsMock._
    }
}
