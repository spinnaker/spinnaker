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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.google.deploy.converters.CreateGoogleHttpLoadBalancerAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer.CreateGoogleHttpLoadBalancerTestConstants.*

class CreateGoogleHttpLoadBalancerDescriptionValidatorSpec extends Specification {
  @Shared
  CreateGoogleHttpLoadBalancerDescriptionValidator validator

  @Shared
  def converter

  @Shared
  GoogleHealthCheck hc

  void setupSpec() {
    validator = new CreateGoogleHttpLoadBalancerDescriptionValidator()
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).credentials(new FakeGoogleCredentials()).build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    validator.accountCredentialsProvider = credentialsProvider
    converter = new CreateGoogleHttpLoadBalancerAtomicOperationConverter(
        accountCredentialsProvider: credentialsProvider,
        objectMapper: new ObjectMapper()
    )
    hc = [
        "name"              : "basic-check",
        "requestPath"       : "/",
        "port"              : 80,
        "checkIntervalSec"  : 1,
        "timeoutSec"        : 1,
        "healthyThreshold"  : 1,
        "unhealthyThreshold": 1
    ]
  }

  void "pass validation with proper description inputs"() {
    setup:
      def input = [
          accountName: ACCOUNT_NAME,
          "googleHttpLoadBalancer": [
              "name"          : LOAD_BALANCER_NAME,
              "portRange"     : PORT_RANGE,
              "defaultService": [
                  "name"       : DEFAULT_SERVICE,
                  "backends"   : [],
                  "healthCheck": hc,
              ],
              "certificate"   : "",
              "hostRules"     : [
                  [
                      "hostPatterns": [
                          "host1.com",
                          "host2.com"
                      ],
                      "pathMatcher" : [
                          "pathRules"     : [
                              [
                                  "paths"         : [
                                      "/path",
                                      "/path2/more"
                                  ],
                                  "backendService": [
                                      "name"       : PM_SERVICE,
                                      "backends"   : [],
                                      "healthCheck": hc,
                                  ]
                              ]
                          ],
                          "defaultService": [
                              "name"       : DEFAULT_PM_SERVICE,
                              "backends"   : [],
                              "healthCheck": hc,
                          ]
                      ]
                  ]
              ]
          ]
      ]
      def description = converter.convertDescription(input)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "pass validation with no host rules description inputs"() {
    setup:
      def input = [
          accountName: ACCOUNT_NAME,
          "googleHttpLoadBalancer": [
              "name"          : LOAD_BALANCER_NAME,
              "portRange"     : PORT_RANGE,
              "defaultService": [
                  "name"       : DEFAULT_SERVICE,
                  "backends"   : [],
                  "healthCheck": hc,
              ],
              "certificate"   : "",
              "hostRules"     : null,
          ]
      ]
      def description = converter.convertDescription(input)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "fail with improperly formatted ports"() {
    setup:
      def input = [
          accountName: ACCOUNT_NAME,
          "googleHttpLoadBalancer": [
              "name"          : LOAD_BALANCER_NAME,
              "portRange"     : "80-81",
              "defaultService": [
                  "name"       : DEFAULT_SERVICE,
                  "backends"   : [],
                  "healthCheck": hc,
              ],
              "certificate"   : "",
              "hostRules"     : [
                  [
                      "hostPatterns": [
                          "host1.com",
                          "host2.com"
                      ],
                      "pathMatcher" : [
                          "pathRules"     : [
                              [
                                  "paths"         : [
                                      "/path",
                                      "/path2/more"
                                  ],
                                  "backendService": [
                                      "name"       : PM_SERVICE,
                                      "backends"   : [],
                                      "healthCheck": hc,
                                  ]
                              ]
                          ],
                          "defaultService": [
                              "name"       : DEFAULT_PM_SERVICE,
                              "backends"   : [],
                              "healthCheck": hc,
                          ]
                      ]
                  ]
              ]
          ]
      ]
      def description = converter.convertDescription(input)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue("googleLoadBalancer.portRange", _)
  }

  @Unroll
  void "fail if a backend service does not have a health check"() {
    setup:
      def input = [
          accountName: ACCOUNT_NAME,
          "googleHttpLoadBalancer": [
              "name"          : LOAD_BALANCER_NAME,
              "portRange"     : "80-81",
              "defaultService": [
                  "name"       : DEFAULT_SERVICE,
                  "backends"   : [],
                  "healthCheck": hc1,
              ],
              "certificate"   : "",
              "hostRules"     : [
                  [
                      "hostPatterns": [
                          "host1.com",
                          "host2.com"
                      ],
                      "pathMatcher" : [
                          "pathRules"     : [
                              [
                                  "paths"         : [
                                      "/path",
                                      "/path2/more"
                                  ],
                                  "backendService": [
                                      "name"       : PM_SERVICE,
                                      "backends"   : [],
                                      "healthCheck": hc3,
                                  ]
                              ]
                          ],
                          "defaultService": [
                              "name"       : DEFAULT_PM_SERVICE,
                              "backends"   : [],
                              "healthCheck": hc2,
                          ]
                      ]
                  ]
              ]
          ]
      ]
      def description = converter.convertDescription(input)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue("googleLoadBalancer.defaultService OR googleLoadBalancer.hostRules.pathMatcher.defaultService OR googleLoadBalancer.hostRules.pathMatcher.pathRules.backendService", _)

    where:
      hc1    | hc2   | hc3
      hc     | hc    | null
      hc     | null  | hc
      null   | hc    | hc
  }
}
