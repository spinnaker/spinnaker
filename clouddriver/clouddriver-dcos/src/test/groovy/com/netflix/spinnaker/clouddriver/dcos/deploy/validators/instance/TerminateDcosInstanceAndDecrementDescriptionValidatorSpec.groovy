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

package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.instance

import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.instance.TerminateDcosInstancesAndDecrementDescription
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
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
            def errorsMock = Mock(ValidationErrors)
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
