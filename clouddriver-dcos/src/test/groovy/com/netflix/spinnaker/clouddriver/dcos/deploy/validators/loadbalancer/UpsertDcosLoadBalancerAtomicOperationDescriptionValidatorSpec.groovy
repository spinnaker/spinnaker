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

package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.loadbalancer

import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.UpsertDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.UpsertDcosLoadBalancerAtomicOperationDescription.PortRange

class UpsertDcosLoadBalancerAtomicOperationDescriptionValidatorSpec extends BaseSpecification {
  private static final DESCRIPTION = "upsertDcosLoadBalancerAtomicOperationDescription"
  private static final ACCOUNT = "my-test-account"

  @Shared
  @Subject
  UpsertDcosLoadBalancerAtomicOperationDescriptionValidator validator

  @Shared
  DcosAccountCredentials testCredentials = defaultCredentialsBuilder().account(ACCOUNT).build()

  def setupSpec() {
    def accountCredentialsProvider = Stub(AccountCredentialsProvider) {
      getCredentials(testCredentials.name) >> testCredentials
    }
    validator = new UpsertDcosLoadBalancerAtomicOperationDescriptionValidator(accountCredentialsProvider)
  }

  void "successfully validates when no fields are missing or invalid"() {
    setup:
    def description = new UpsertDcosLoadBalancerAtomicOperationDescription().with {
      name = "lb"
      credentials = testCredentials
      dcosCluster = DEFAULT_REGION
      cpus = 1
      mem = 256
      instances = 1
      bindHttpHttps = true
      acceptedResourceRoles = ["resource"]
      portRange = new PortRange(protocol: "tcp", minPort: 10000, maxPort: 10100)
      it
    }

    def errorsMock = Mock(ValidationErrors)

    when:
    validator.validate([], description, errorsMock)

    then:
    0 * errorsMock._
  }

  void "reports an error when the load balance name is invalid"() {
    setup:
    def description = new UpsertDcosLoadBalancerAtomicOperationDescription().with {
      name = "-iNv.aLid-"
      credentials = testCredentials
      dcosCluster = DEFAULT_REGION
      cpus = 1
      mem = 256
      instances = 1
      bindHttpHttps = true
      acceptedResourceRoles = ["resource"]
      portRange = new PortRange(protocol: "tcp", minPort: 10000, maxPort: 10100)
      it
    }

    def errorsMock = Mock(ValidationErrors)

    when:
    validator.validate([], description, errorsMock)

    then:
    1 * errorsMock.rejectValue("name", "${DESCRIPTION}.name.invalid")
    0 * errorsMock._
  }

  void "reports an error when no credentials are present"() {
    setup:
    def description = new UpsertDcosLoadBalancerAtomicOperationDescription().with {
      name = "lb"
      cpus = 1
      mem = 256
      instances = 1
      bindHttpHttps = true
      acceptedResourceRoles = ["resource"]
      portRange = new PortRange(protocol: "tcp", minPort: 10000, maxPort: 10100)
      it
    }

    def errorsMock = Mock(ValidationErrors)

    when:
    validator.validate([], description, errorsMock)

    then:
    1 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
    1 * errorsMock.rejectValue("dcosCluster", "${DESCRIPTION}.dcosCluster.empty")
    0 * errorsMock._
  }

  void "reports an error when the name is not provided"() {
    setup:
    def description = new UpsertDcosLoadBalancerAtomicOperationDescription().with {
      credentials = testCredentials
      dcosCluster = DEFAULT_REGION
      cpus = 1
      mem = 256
      instances = 1
      bindHttpHttps = true
      acceptedResourceRoles = ["resource"]
      portRange = new PortRange(protocol: "tcp", minPort: 10000, maxPort: 10100)
      it
    }

    def errorsMock = Mock(ValidationErrors)

    when:
    validator.validate([], description, errorsMock)

    then:
    1 * errorsMock.rejectValue("name", "${DESCRIPTION}.name.empty")
    0 * errorsMock._
  }

  void "reports errors for invalid resource capacities"() {
    setup:
    def description = new UpsertDcosLoadBalancerAtomicOperationDescription().with {
      name = "lb"
      credentials = testCredentials
      dcosCluster = DEFAULT_REGION
      cpus = -1
      mem = -1
      instances = -1
      bindHttpHttps = true
      acceptedResourceRoles = ["resource"]
      portRange = new PortRange(protocol: "tcp", minPort: 10000, maxPort: 10100)
      it
    }

    def errorsMock = Mock(ValidationErrors)

    when:
    validator.validate([], description, errorsMock)

    then:
    1 * errorsMock.rejectValue("cpus", "${DESCRIPTION}.cpus.invalid")
    1 * errorsMock.rejectValue("mem", "${DESCRIPTION}.mem.invalid")
    1 * errorsMock.rejectValue("instances", "${DESCRIPTION}.instances.invalid")
    0 * errorsMock._
  }

  void "reports an error when acceptedResourceRoles contains a null value"() {
    setup:
    def description = new UpsertDcosLoadBalancerAtomicOperationDescription().with {
      name = "lb"
      credentials = testCredentials
      dcosCluster = DEFAULT_REGION
      cpus = 1
      mem = 256
      instances = 1
      bindHttpHttps = true
      acceptedResourceRoles = [null]
      portRange = new PortRange(protocol: "tcp", minPort: 10000, maxPort: 10100)
      it
    }

    def errorsMock = Mock(ValidationErrors)

    when:
    validator.validate([], description, errorsMock)

    then:
    1 * errorsMock.rejectValue("acceptedResourceRoles", "${DESCRIPTION}.acceptedResourceRoles.invalid (Must not contain null or empty values)")
    0 * errorsMock._
  }

  void "reports an error if portRange is not provided"() {
    setup:
    def description = new UpsertDcosLoadBalancerAtomicOperationDescription().with {
      name = "lb"
      credentials = testCredentials
      dcosCluster = DEFAULT_REGION
      cpus = 1
      mem = 256
      instances = 1
      bindHttpHttps = true
      acceptedResourceRoles = ["resource"]
      it
    }

    def errorsMock = Mock(ValidationErrors)

    when:
    validator.validate([], description, errorsMock)

    then:
    1 * errorsMock.rejectValue("portRange", "${DESCRIPTION}.portRange.empty")
    0 * errorsMock._
  }

  void "reports an error if portRange has invalid port definitions"() {
    setup:
    def description = new UpsertDcosLoadBalancerAtomicOperationDescription().with {
      name = "lb"
      credentials = testCredentials
      dcosCluster = DEFAULT_REGION
      cpus = 1
      mem = 256
      instances = 1
      bindHttpHttps = true
      acceptedResourceRoles = ["resource"]
      portRange = new PortRange(protocol: "", minPort: 9999, maxPort: 25)

      it
    }

    def errorsMock = Mock(ValidationErrors)

    when:
    validator.validate([], description, errorsMock)

    then:
    1 * errorsMock.rejectValue("portRange", "${DESCRIPTION}.portRange.protocol.invalid")
    1 * errorsMock.rejectValue("portRange", "${DESCRIPTION}.portRange.minPort.invalid (minPort < 10000)")
    1 * errorsMock.rejectValue("portRange", "${DESCRIPTION}.portRange.invalid (maxPort < minPort)")
    0 * errorsMock._
  }
}
