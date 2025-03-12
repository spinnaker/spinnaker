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

package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.servergroup

import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DisableDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Subject

class DisableDcosServerGroupDescriptionValidatorSpec extends BaseSpecification {
  private static final DESCRIPTION = "disableDcosServerGroupDescription"
  private static final INVALID_MARATHON_PART = "-iNv.aLid-"

  DcosAccountCredentials testCredentials = defaultCredentialsBuilder().build()

  AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider) {
    getCredentials(testCredentials.name) >> testCredentials
  }

  @Subject
  DescriptionValidator<DisableDcosServerGroupDescription> validator = new DisableDcosServerGroupDescriptionValidator(accountCredentialsProvider)

  void "validate should give errors when given an empty DestroyDcosServerGroupDescription"() {
    setup:
      def description = new DisableDcosServerGroupDescription(region: null, dcosCluster: null, credentials: null, serverGroupName: null)
      def errorsMock = Mock(ValidationErrors)
    when:
      validator.validate([], description, errorsMock)
    then:
      1 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.empty")
      0 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.invalid")
      1 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
      1 * errorsMock.rejectValue("dcosCluster", "${DESCRIPTION}.dcosCluster.empty")
      1 * errorsMock.rejectValue("serverGroupName", "${DESCRIPTION}.serverGroupName.empty")
      0 * errorsMock.rejectValue("serverGroupName", "${DESCRIPTION}.serverGroupName.invalid")
      0 * errorsMock._
  }

  void "validate should give errors when given an invalid DestroyDcosServerGroupDescription"() {
    setup:
      def description = new DisableDcosServerGroupDescription(region: INVALID_MARATHON_PART, dcosCluster: "", credentials: defaultCredentialsBuilder().account(BAD_ACCOUNT).build(), serverGroupName: INVALID_MARATHON_PART)
      def errorsMock = Mock(ValidationErrors)
    when:
      validator.validate([], description, errorsMock)
    then:
      0 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.empty")
      1 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.invalid")
      0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
      1 * errorsMock.rejectValue("dcosCluster", "${DESCRIPTION}.dcosCluster.empty")
      0 * errorsMock.rejectValue("serverGroupName", "${DESCRIPTION}.serverGroupName.empty")
      1 * errorsMock.rejectValue("serverGroupName", "${DESCRIPTION}.serverGroupName.invalid")
      0 * errorsMock._
  }

  void "validate should give no errors when given an valid DestroyDcosServerGroupDescription"() {
    setup:
    def description = new DisableDcosServerGroupDescription(region: DEFAULT_REGION, dcosCluster: DEFAULT_REGION, credentials: testCredentials, serverGroupName: 'test')
    def errorsMock = Mock(ValidationErrors)
    when:
    validator.validate([], description, errorsMock)
    then:
      0 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.empty")
      0 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.invalid")
      0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
      0 * errorsMock.rejectValue("dcosCluster", "${DESCRIPTION}.dcosCluster.empty")
      0 * errorsMock.rejectValue("serverGroupName", "${DESCRIPTION}.serverGroupName.empty")
      0 * errorsMock.rejectValue("serverGroupName", "${DESCRIPTION}.serverGroupName.invalid")
      0 * errorsMock._
  }
}
