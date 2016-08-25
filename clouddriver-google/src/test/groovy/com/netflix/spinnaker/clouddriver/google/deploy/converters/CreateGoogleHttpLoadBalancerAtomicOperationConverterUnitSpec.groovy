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

package com.netflix.spinnaker.clouddriver.google.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.google.deploy.description.CreateGoogleHttpLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer.CreateGoogleHttpLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer.CreateGoogleHttpLoadBalancerTestConstants.*

class CreateGoogleHttpLoadBalancerAtomicOperationConverterUnitSpec extends Specification {
  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  CreateGoogleHttpLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new CreateGoogleHttpLoadBalancerAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "createGoogleHttpLoadBalancerDescription type returns CreateGoogleHttpLoadBalancerDescription and CreateGoogleHttpLoadBalancerAtomicOperation"() {
    setup:
      def hc = [
          "name"              : "basic-check",
          "requestPath"       : "/",
          "port"              : 80,
          "checkIntervalSec"  : 1,
          "timeoutSec"        : 1,
          "healthyThreshold"  : 1,
          "unhealthyThreshold": 1
      ]
      def input = [
          "credentials"           : "my-google-account",
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

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof CreateGoogleHttpLoadBalancerDescription
      description.googleHttpLoadBalancer.name == LOAD_BALANCER_NAME
      description.googleHttpLoadBalancer.portRange == PORT_RANGE

      List<GoogleBackendService> services = Utils.getBackendServicesFromHttpLoadBalancerView(description.googleHttpLoadBalancer.view)
      services.findAll { it.healthCheck == (hc as GoogleHealthCheck) }.size == 3
      description.googleHttpLoadBalancer.defaultService.name == DEFAULT_SERVICE
      description.googleHttpLoadBalancer.hostRules[0].pathMatcher.defaultService.name == DEFAULT_PM_SERVICE
      description.googleHttpLoadBalancer.hostRules[0].pathMatcher.pathRules[0].backendService.name == PM_SERVICE

    when:
      def operation = converter.convertOperation(input)

    then:
     operation instanceof CreateGoogleHttpLoadBalancerAtomicOperation
  }
}
