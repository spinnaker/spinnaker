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

import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Subject

class DeployDcosServerGroupDescriptionValidatorSpec extends BaseSpecification {
  private static final DESCRIPTION = "deployDcosServerGroupDescription"

  def testCredentials = defaultCredentialsBuilder().build()

  def accountCredentialsProvider = Stub(AccountCredentialsProvider) {
    getCredentials(testCredentials.name) >> testCredentials
  }

  @Subject
  DeployDcosServerGroupDescriptionValidator validator = new DeployDcosServerGroupDescriptionValidator(accountCredentialsProvider)

  void "validate should give errors when given an empty DeployDcosServerGroupDescription"() {
    setup:
    def description = new DeployDcosServerGroupDescription(account: null, dcosCluster: null, credentials: null, application: null, desiredCapacity: -1,
                                                           cpus: -1, mem: -1, disk: -1, gpus: -1)
    def errorsMock = Mock(ValidationErrors)
    when:
    validator.validate([], description, errorsMock)
    then:
    1 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.empty")
    0 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.invalid")
    1 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
    1 * errorsMock.rejectValue("dcosCluster", "${DESCRIPTION}.dcosCluster.empty")
    1 * errorsMock.rejectValue("application", "${DESCRIPTION}.application.empty")
    0 * errorsMock.rejectValue("application", "${DESCRIPTION}.application.invalid")
    1 * errorsMock.rejectValue("desiredCapacity", "${DESCRIPTION}.desiredCapacity.invalid")
    1 * errorsMock.rejectValue("cpus", "${DESCRIPTION}.cpus.invalid")
    1 * errorsMock.rejectValue("mem", "${DESCRIPTION}.mem.invalid")
    1 * errorsMock.rejectValue("disk", "${DESCRIPTION}.disk.invalid")
    1 * errorsMock.rejectValue("gpus", "${DESCRIPTION}.gpus.invalid")
    0 * errorsMock._
  }

  void "validate should give errors when given an invalid DeployDcosServerGroupDescription"() {
    setup:
    def description = new DeployDcosServerGroupDescription(region: '-iNv.aLiD-', credentials: defaultCredentialsBuilder().account(BAD_ACCOUNT).build(),
                                                           application: '-iNv.aLiD-', dcosCluster: "", desiredCapacity: 1, cpus: 1, mem: 512, disk: 0, gpus: 0)
    def errorsMock = Mock(ValidationErrors)
    when:
    validator.validate([], description, errorsMock)
    then:
    0 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.empty")
    1 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.invalid")
    0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
    1 * errorsMock.rejectValue("dcosCluster", "${DESCRIPTION}.dcosCluster.empty")
    0 * errorsMock.rejectValue("application", "${DESCRIPTION}.application.empty")
    1 * errorsMock.rejectValue("application", "${DESCRIPTION}.application.invalid")
    0 * errorsMock.rejectValue("desiredCapacity", "${DESCRIPTION}.desiredCapacity.invalid")
    0 * errorsMock.rejectValue("cpus", "${DESCRIPTION}.cpus.invalid")
    0 * errorsMock.rejectValue("mem", "${DESCRIPTION}.mem.invalid")
    0 * errorsMock.rejectValue("disk", "${DESCRIPTION}.disk.invalid")
    0 * errorsMock.rejectValue("gpus", "${DESCRIPTION}.gpus.invalid")
    0 * errorsMock._
  }

  void "validate should give no errors when given an valid DeployDcosServerGroupDescription"() {
    setup:
    def description = new DeployDcosServerGroupDescription(region: DEFAULT_REGION, dcosCluster: DEFAULT_REGION, credentials: testCredentials, application: "test",
                                                           desiredCapacity: 1, cpus: 1, mem: 512, disk: 0, gpus: 0)
    def errorsMock = Mock(ValidationErrors)
    when:
    validator.validate([], description, errorsMock)
    then:
    0 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.empty")
    0 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.invalid")
    0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
    0 * errorsMock.rejectValue("dcosCluster", "${DESCRIPTION}.dcosCluster.empty")
    0 * errorsMock.rejectValue("application", "${DESCRIPTION}.application.empty")
    0 * errorsMock.rejectValue("application", "${DESCRIPTION}.application.invalid")
    0 * errorsMock.rejectValue("desiredCapacity", "${DESCRIPTION}.desiredCapacity.invalid")
    0 * errorsMock.rejectValue("cpus", "${DESCRIPTION}.cpus.invalid")
    0 * errorsMock.rejectValue("mem", "${DESCRIPTION}.mem.invalid")
    0 * errorsMock.rejectValue("disk", "${DESCRIPTION}.disk.invalid")
    0 * errorsMock.rejectValue("gpus", "${DESCRIPTION}.gpus.invalid")
    0 * errorsMock._
  }
}
