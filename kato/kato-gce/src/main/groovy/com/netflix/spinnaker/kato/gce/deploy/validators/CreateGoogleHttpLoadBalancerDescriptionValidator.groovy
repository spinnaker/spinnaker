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

package com.netflix.spinnaker.kato.gce.deploy.validators

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.kato.deploy.DescriptionValidator
import com.netflix.spinnaker.kato.gce.deploy.description.CreateGoogleHttpLoadBalancerDescription
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
    helper.validateName(description.loadBalancerName, "loadBalancerName")

    // An HTTP load balancer must have a health check, but all needed health check fields have defaults. So, it
    // is not necessary to specify one in the description. Thus, we don't enforce it here. Also, HTTP load
    // balancers may be global, so we don't require a zone field.

    // If we have hostRules we must have pathMatchers and vice versa. Also, hostRules must have pathMatcher fields
    // that match the names of pathMatchers in the description.
    // TODO(bklingher): Enforce these rules more strictly. The validation for this below is only very basic so far.

    boolean havePathMatchers = false
    if (description.pathMatchers && description.pathMatchers.size() > 0) {
      havePathMatchers = true
    }
    boolean haveHostRules = false
    if (description.hostRules && description.hostRules.size() > 0) {
      haveHostRules = true
    }

    if (havePathMatchers && !haveHostRules) {
      errors.rejectValue "hostRules", "createGoogleHttpLoadBalancerDescription.hostRules.empty but createGoogleHttpLoadBalancerDescription.pathMatchers.size > 0"
    }

    if (!havePathMatchers && haveHostRules) {
      errors.rejectValue "pathMatchers", "createGoogleHttpLoadBalancerDescription.pathMatchers.empty but createGoogleHttpLoadBalancerDescription.hostRules.size > 0"
    }

    if (havePathMatchers && haveHostRules && description.hostRules.size() < description.pathMatchers.size()) {
      errors.rejectValue "pathMatchers", "createGoogleHttpLoadBalancerDescription.hostRules.size < createGoogleHttpLoadBalancerDescription.pathMatchers.size"
    }

    for (pathMatcher in description.pathMatchers) {
      if (!pathMatcher.name) {
        errors.rejectValue "pathMatchers", "createGoogleHttpLoadBalancerDescription.pathMatchers[i].name.empty"
      }
    }

    for (hostRule in description.hostRules) {
      if (!hostRule.pathMatcher) {
        errors.rejectValue "hostRules", "createGoogleHttpLoadBalancerDescription.hostRule[i].pathMatcher.empty"
      }
    }
  }
}
