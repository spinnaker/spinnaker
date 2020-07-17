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

package com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.ops.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model.UpsertAzureSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.azure.common.StandardAzureAttributeValidator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component("upsertAzureSecurityGroupDescriptionValidator")
class UpsertAzureSecurityGroupDescriptionValidator extends
  DescriptionValidator<UpsertAzureSecurityGroupDescription> {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, UpsertAzureSecurityGroupDescription description, ValidationErrors errors) {
    def helper = new StandardAzureAttributeValidator("UpsertAzureSecurityGroupDescription", errors)

    helper.validateCredentials(description.credentials, accountCredentialsProvider)
    helper.validateRegion(description.region)
    helper.validateName(description.securityGroupName, "securityGroupName")
    helper.validateName(description.appName, "appName")
  }
}
