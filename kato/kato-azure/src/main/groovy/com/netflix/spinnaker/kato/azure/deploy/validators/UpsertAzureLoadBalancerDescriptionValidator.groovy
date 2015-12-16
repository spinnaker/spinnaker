/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.kato.azure.deploy.validators

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kato.deploy.DescriptionValidator
import com.netflix.spinnaker.kato.azure.deploy.description.UpsertAzureLoadBalancerDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("upsertAzureLoadBalancerDescriptionValidator")
class UpsertAzureLoadBalancerDescriptionValidator extends
  DescriptionValidator<UpsertAzureLoadBalancerDescription> {
  private static final List<String> SUPPORTED_IP_PROTOCOLS = ["TCP", "UDP"]

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, UpsertAzureLoadBalancerDescription description, Errors errors) {
    def helper = new StandardAzureAttributeValidator("upsertAzureLoadBalancerDescription", errors)


    helper.validateCredentials(description.credentials, accountCredentialsProvider)
    helper.validateRegion(description.region)
    helper.validateName(description.loadBalancerName, "loadBalancerName")

    /*

    // If the IP protocol is specified, it must be contained in the list of supported protocols.
    if (description.ipProtocol && !SUPPORTED_IP_PROTOCOLS.contains(description.ipProtocol)) {
      errors.rejectValue("ipProtocol",
        "upsertAzureLoadBalancerDescription.ipProtocol.notSupported")
    }
    */
  }
}
