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
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer.CreateGoogleHttpLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer.UpsertGoogleLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancer
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer.CreateGoogleHttpLoadBalancerTestConstants.*

class UpsertGoogleLoadBalancerAtomicOperationConverterUnitSpec extends Specification {
  private static final LOAD_BALANCER_NAME = "spinnaker-test-v000"
  private static final REGION = "us-central1"
  private static final ACCOUNT_NAME = "auto"
  private static final CHECK_INTERVAL_SEC = 7
  private static final INSTANCE = "inst"
  private static final IP_ADDRESS = "1.1.1.1"
  private static final IP_PROTOCOL = "TCP"
  private static final PORT_RANGE = "80-82"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  UpsertGoogleLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "upsertGoogleLoadBalancerDescription type returns UpsertGoogleLoadBalancerDescription and UpsertGoogleLoadBalancerAtomicOperation"() {
    setup:
      def input = [
        loadBalancerType: "HTTP",
        loadBalancerName: LOAD_BALANCER_NAME,
        region          : REGION,
        accountName     : ACCOUNT_NAME,
        healthCheck     : [checkIntervalSec: CHECK_INTERVAL_SEC],
        instances       : [INSTANCE],
        ipAddress       : IP_ADDRESS,
        ipProtocol      : IP_PROTOCOL,
        portRange       : PORT_RANGE
      ]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof UpsertGoogleLoadBalancerDescription
      description.loadBalancerName == LOAD_BALANCER_NAME
      description.healthCheck.checkIntervalSec == 7
      description.healthCheck.timeoutSec == null
      description.instances.size() == 1
      description.instances.get(0) == INSTANCE
      description.ipAddress == IP_ADDRESS
      description.ipProtocol == IP_PROTOCOL
      description.portRange == PORT_RANGE

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof UpsertGoogleLoadBalancerAtomicOperation
  }

  void "createGoogleHttpLoadBalancerDescription type returns UpsertGoogleLoadBalancerDescription and CreateGoogleHttpLoadBalancerAtomicOperation"() {
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
        "loadBalancerType": "HTTP",
        "credentials"     : "my-google-account",
        "loadBalancerName": LOAD_BALANCER_NAME,
        "portRange"       : PORT_RANGE,
        "defaultService"  : [
          "name"       : DEFAULT_SERVICE,
          "backends"   : [],
          "healthCheck": hc,
        ],
        "certificate"     : "",
        "hostRules"       : [
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

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof UpsertGoogleLoadBalancerDescription
      description.loadBalancerName == LOAD_BALANCER_NAME
      description.portRange == PORT_RANGE

      def httpLoadBalancer = new GoogleHttpLoadBalancer(
        name: description.loadBalancerName,
        defaultService: description.defaultService,
        hostRules: description.hostRules,
        certificate: description.certificate,
        ipAddress: description.ipAddress,
        ipProtocol: description.ipProtocol,
        portRange: description.portRange
      )
      List<GoogleBackendService> services = Utils.getBackendServicesFromHttpLoadBalancerView(httpLoadBalancer.view)
      services.findAll { it.healthCheck == (hc as GoogleHealthCheck) }.size == 3
      description.defaultService.name == DEFAULT_SERVICE
      description.hostRules[0].pathMatcher.defaultService.name == DEFAULT_PM_SERVICE
      description.hostRules[0].pathMatcher.pathRules[0].backendService.name == PM_SERVICE

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof CreateGoogleHttpLoadBalancerAtomicOperation
  }
}
