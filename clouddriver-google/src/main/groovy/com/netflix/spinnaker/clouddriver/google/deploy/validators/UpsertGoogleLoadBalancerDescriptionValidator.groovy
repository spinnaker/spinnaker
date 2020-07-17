/*
 * Copyright 2015 Google, Inc.
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

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.google.GoogleOperation
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerType
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@GoogleOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("upsertGoogleLoadBalancerDescriptionValidator")
class UpsertGoogleLoadBalancerDescriptionValidator extends
    DescriptionValidator<UpsertGoogleLoadBalancerDescription> {
  private static final List<String> SUPPORTED_IP_PROTOCOLS = ["TCP", "UDP"]

  private static final List<Integer> SUPPORTED_SSL_PROXY_PORTS = [25, 43, 110, 143, 195, 443, 465, 587, 700, 993, 995]
  private static final List<Integer> SUPPORTED_TCP_PROXY_PORTS = [25, 43, 110, 143, 195, 443, 465, 587, 700, 993, 995]

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, UpsertGoogleLoadBalancerDescription description, ValidationErrors errors) {
    def helper = new StandardGceAttributeValidator("upsertGoogleLoadBalancerDescription", errors)

    helper.validateCredentials(description.accountName, accountCredentialsProvider)
    helper.validateName(description.loadBalancerName, "loadBalancerName")

    switch (description.loadBalancerType) {
      case GoogleLoadBalancerType.NETWORK:
        helper.validateRegion(description.region, description.credentials)

        // If the IP protocol is specified, it must be contained in the list of supported protocols.
        if (description.ipProtocol && !SUPPORTED_IP_PROTOCOLS.contains(description.ipProtocol)) {
          errors.rejectValue("ipProtocol",
            "upsertGoogleLoadBalancerDescription.ipProtocol.notSupported")
        }
        break
      case GoogleLoadBalancerType.HTTP:

        // portRange must be a single port.
        try {
          Integer.parseInt(description.portRange)
        } catch (NumberFormatException _) {
          errors.rejectValue("portRange",
            "upsertGoogleLoadBalancerDescription.portRange.requireSinglePort")
        }

        // Each backend service must have a health check.
        def googleHttpLoadBalancer = new GoogleHttpLoadBalancer(
            name: description.loadBalancerName,
            defaultService: description.defaultService,
            hostRules: description.hostRules,
            certificate: description.certificate,
            ipAddress: description.ipAddress,
            ipProtocol: description.ipProtocol,
            portRange: description.portRange
        )
        List<GoogleBackendService> services = Utils.getBackendServicesFromHttpLoadBalancerView(googleHttpLoadBalancer.view)
        services?.each { GoogleBackendService service ->
          if (!service.healthCheck) {
            errors.rejectValue("defaultService OR hostRules.pathMatcher.defaultService OR hostRules.pathMatcher.pathRules.backendService",
              "upsertGoogleLoadBalancerDescription.backendServices.healthCheckRequired")
          }
        }
        break
      case GoogleLoadBalancerType.INTERNAL:
        helper.validateRegion(description.region, description.credentials)

        // If the IP protocol is specified, it must be contained in the list of supported protocols.
        if (description.ipProtocol && !SUPPORTED_IP_PROTOCOLS.contains(description.ipProtocol)) {
          errors.rejectValue("ipProtocol",
            "upsertGoogleLoadBalancerDescription.ipProtocol.notSupported")
        }

        def bs = description.backendService
        if (!bs) {
          errors.rejectValue("backendService",
            "upsertGoogleLoadBalancerDescription.backendServiceRequired")
        }

        def hc = bs.healthCheck
        if (!hc) {
          errors.rejectValue("backendService.healthCheck",
            "upsertGoogleLoadBalancerDescription.backendService.healthCheckRequired")
        }

        if (!description.network || !description.subnet) {
          errors.rejectValue("network OR subnet",
            "upsertGoogleLoadBalancerDescription.networkOrSubnet.networkAndSubnetRequired")
        }
        break
      case GoogleLoadBalancerType.SSL:
        def bs = description.backendService
        if (!bs) {
          errors.rejectValue("backendService",
            "upsertGoogleLoadBalancerDescription.backendServiceRequired")
        } else {
          def hc = bs.healthCheck
          if (!hc) {
            errors.rejectValue("backendService.healthCheck",
              "upsertGoogleLoadBalancerDescription.backendService.healthCheckRequired")
          }
        }

        try {
          def pr = Integer.parseInt(description.portRange)
          if (!SUPPORTED_SSL_PROXY_PORTS.contains(pr)) {
            errors.rejectValue("portRange",
              "upsertGoogleLoadBalancerDescription.portRange.notSupported")
          }
        } catch (NumberFormatException _) {
          errors.rejectValue("portRange",
            "upsertGoogleLoadBalancerDescription.portRange.requireSinglePort")
        }

        if (!description.certificate) {
          errors.rejectValue("certificate",
            "upsertGoogleLoadBalancerDescription.certificate.certificateRequired")
        }
        break
      case GoogleLoadBalancerType.TCP:
        def bs = description.backendService
        if (!bs) {
          errors.rejectValue("backendService",
            "upsertGoogleLoadBalancerDescription.backendServiceRequired")
        } else {
          def hc = bs.healthCheck
          if (!hc) {
            errors.rejectValue("backendService.healthCheck",
              "upsertGoogleLoadBalancerDescription.backendService.healthCheckRequired")
          }
        }

        try {
          def pr = Integer.parseInt(description.portRange)
          if (!SUPPORTED_TCP_PROXY_PORTS.contains(pr)) {
            errors.rejectValue("portRange",
              "upsertGoogleLoadBalancerDescription.portRange.notSupported")
          }
        } catch (NumberFormatException _) {
          errors.rejectValue("portRange",
            "upsertGoogleLoadBalancerDescription.portRange.requireSinglePort")
        }
        break
      default:
        // TODO(jacobkiefer): Fail here once frontend calls are modified.
        break
    }
  }
}
