/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.validators

import com.netflix.spinnaker.clouddriver.google.deploy.description.CreateGoogleHttpLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer.CreateGoogleHttpLoadBalancerTestConstants.*

class CreateGoogleHttpLoadBalancerDescriptionValidatorSpec extends Specification {
  @Shared
  CreateGoogleHttpLoadBalancerDescriptionValidator validator

  void setupSpec() {
    validator = new CreateGoogleHttpLoadBalancerDescriptionValidator()
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).credentials(new FakeGoogleCredentials()).build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    validator.accountCredentialsProvider = credentialsProvider
  }

  void "pass validation with proper description inputs"() {
    setup:
      def description = new CreateGoogleHttpLoadBalancerDescription([
          loadBalancerName: LOAD_BALANCER_NAME,
          accountName: ACCOUNT_NAME,
          backendTimeoutSec: 30,
          healthCheck: [
            port: 8080,
            checkIntervalSec: 5,
            healthyThreshold: 2,
            unhealthyThreshold: 2,
            timeoutSec: 5,
            requestPath: "/"
          ],
          backends: [[group: INSTANCE_GROUP, balancingMode: BALANCING_MODE]],
          ipAddress: IP_ADDRESS,
          portRange: PORT_RANGE,
          hostRules: [[hosts: [HOST], pathMatcher: MATCHER]],
          pathMatchers: [[name: MATCHER, defaultService: SERVICE, pathRules: [[paths: [PATH], service: SERVICE]]]]
      ])
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "fail validation with pathMatchers and no hostRules"() {
    setup:
    def description = new CreateGoogleHttpLoadBalancerDescription([
        loadBalancerName: LOAD_BALANCER_NAME,
        accountName: ACCOUNT_NAME,
        backendTimeoutSec: 30,
        backends: [[group: INSTANCE_GROUP, balancingMode: BALANCING_MODE]],
        ipAddress: IP_ADDRESS,
        portRange: PORT_RANGE,
        pathMatchers: [[name: MATCHER, defaultService: SERVICE, pathRules: [[paths: [PATH], service: SERVICE]]]]
    ])
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('hostRules', _)
  }

  void "fail validation with hostRules and no pathMatchers"() {
    setup:
    def description = new CreateGoogleHttpLoadBalancerDescription([
        loadBalancerName: LOAD_BALANCER_NAME,
        accountName: ACCOUNT_NAME,
        backendTimeoutSec: 30,
        backends: [[group: INSTANCE_GROUP, balancingMode: BALANCING_MODE]],
        ipAddress: IP_ADDRESS,
        portRange: PORT_RANGE,
        hostRules: [[hosts: [HOST], pathMatcher: MATCHER]],
    ])
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('pathMatchers', _)
  }

  void "fail validation with more pathMatchers than hostRules"() {
    setup:
    def description = new CreateGoogleHttpLoadBalancerDescription([
        loadBalancerName: LOAD_BALANCER_NAME,
        accountName: ACCOUNT_NAME,
        backendTimeoutSec: 30,
        backends: [[group: INSTANCE_GROUP, balancingMode: BALANCING_MODE]],
        ipAddress: IP_ADDRESS,
        portRange: PORT_RANGE,
        hostRules: [[hosts: [HOST], pathMatcher: MATCHER]],
        pathMatchers: [[name: MATCHER, defaultService: SERVICE, pathRules: [[paths: [PATH], service: SERVICE]]],
                       [name: MATCHER, defaultService: SERVICE, pathRules: [[paths: [PATH], service: SERVICE]]]]
    ])
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('pathMatchers', _)
  }

  void "fail validation with no name for pathMatcher and no pathMacher field in hostRule"() {
    setup:
    def description = new CreateGoogleHttpLoadBalancerDescription([
        loadBalancerName: LOAD_BALANCER_NAME,
        accountName: ACCOUNT_NAME,
        backendTimeoutSec: 30,
        healthCheck: [
            port: 8080,
            checkIntervalSec: 5,
            healthyThreshold: 2,
            unhealthyThreshold: 2,
            timeoutSec: 5,
            requestPath: "/"
        ],
        backends: [[group: INSTANCE_GROUP, balancingMode: BALANCING_MODE]],
        ipAddress: IP_ADDRESS,
        portRange: PORT_RANGE,
        hostRules: [[hosts: [HOST]]],
        pathMatchers: [[defaultService: SERVICE, pathRules: [[paths: [PATH], service: SERVICE]]]]
    ])
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('pathMatchers', _)
    1 * errors.rejectValue('hostRules', _)
  }

  void "pass validation with minimal description"() {
    setup:
      def description = new CreateGoogleHttpLoadBalancerDescription(
          loadBalancerName: LOAD_BALANCER_NAME,
          accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "null input fails validation"() {
    setup:
      def description = new CreateGoogleHttpLoadBalancerDescription()
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('credentials', _)
      1 * errors.rejectValue('loadBalancerName', _)
  }
}
