/*
 * Copyright 2016 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.appgateway.ops.validators

import com.netflix.spinnaker.clouddriver.azure.common.StandardAzureAttributeValidator
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.model.AzureAppGatewayDescription
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("upsertAzureAppGatewayDescriptionValidator")
class UpsertAzureAppGatewayAtomicOperationValidator extends
  DescriptionValidator<AzureAppGatewayDescription> {
  private static final List<String> SUPPORTED_IP_PROTOCOLS = ["HTTP"]
  private static final List<String> SUPPORTED_PROBE_PROTOCOLS = ["HTTP"]
  // TODO: add support for the remaining protocols: HTTPS, TCP, UDP

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, AzureAppGatewayDescription description, Errors errors) {
    def helper = new StandardAzureAttributeValidator("upsertAzureAppGatewayDescriptionValidator", errors)

    helper.validateCredentials(description.credentials, accountCredentialsProvider)
    helper.validateRegion(description.region)
    helper.validateName(description.loadBalancerName, "loadBalancerName")
    helper.validateName(description.appName, "appName")
    description.rules?.each { rule ->
      if(!SUPPORTED_IP_PROTOCOLS.contains(rule.protocol.toString().toUpperCase())) {
        errors.rejectValue "protocolType", "upsertAzureAppGatewayDescriptionValidator.rule.protocolType.not_supported"
      }
    }
    description.probes?.each { probe ->
      if(!SUPPORTED_PROBE_PROTOCOLS.contains(probe.protocol.toString().toUpperCase())) {
        errors.rejectValue "protocolType", "upsertAzureAppGatewayDescriptionValidator.probe.protocolType.not_supported"
      }
    }
  }
}
