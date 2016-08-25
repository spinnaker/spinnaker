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
import com.netflix.spinnaker.clouddriver.google.deploy.description.CreateGoogleHttpLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("createGoogleHttpLoadBalancerDescriptionValidator")
class CreateGoogleHttpLoadBalancerDescriptionValidator extends DescriptionValidator<CreateGoogleHttpLoadBalancerDescription> {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, CreateGoogleHttpLoadBalancerDescription description, Errors errors) {
    def helper = new StandardGceAttributeValidator("createGoogleHttpLoadBalancerDescription", errors)

    helper.validateCredentials(description.accountName, accountCredentialsProvider)
    helper.validateName(description.googleHttpLoadBalancer.name, "googleLoadBalancer.name")

    // portRange must be a single port.
    try {
      Integer.parseInt(description.googleHttpLoadBalancer.portRange)
    } catch (NumberFormatException _) {
      errors.rejectValue("googleLoadBalancer.portRange",
          "googleLoadBalancer.portRange.requireSinglePort")
    }

    // Each backend service must have a health check.
    List<GoogleBackendService> services = Utils.getBackendServicesFromHttpLoadBalancerView(description.googleHttpLoadBalancer.view)
    services?.each { GoogleBackendService service ->
      if (!service.healthCheck) {
        errors.rejectValue("googleLoadBalancer.defaultService OR googleLoadBalancer.hostRules.pathMatcher.defaultService OR googleLoadBalancer.hostRules.pathMatcher.pathRules.backendService",
            "createGoogleHttpLoadBalancerDescription.backendServices.healthCheckRequired")
      }
    }
  }
}
