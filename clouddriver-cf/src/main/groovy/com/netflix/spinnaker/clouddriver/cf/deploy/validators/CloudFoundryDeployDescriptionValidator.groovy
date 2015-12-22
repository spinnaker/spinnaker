/*
 * Copyright 2015 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cf.deploy.validators
import com.netflix.spinnaker.clouddriver.cf.CloudFoundryOperation
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.cf.deploy.description.CloudFoundryDeployDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors
/**
 * Validator for Cloud Foundry deploy description
 */
@CloudFoundryOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("cloudFoundryDeployDescriptionValidator")
class CloudFoundryDeployDescriptionValidator extends DescriptionValidator<CloudFoundryDeployDescription> {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, CloudFoundryDeployDescription description, Errors errors) {
    def helper = new StandardCfAttributeValidator("cloudFoundryDeployDescription", errors)

    helper.validateCredentials(description.credentialAccount, accountCredentialsProvider)

    helper.validateNotEmpty(description?.credentials?.api, "cloudFoundryDeployDescription.credentials.api")
    helper.validateNotEmpty(description?.credentials?.org, "cloudFoundryDeployDescription.credentials.org")
    helper.validateNotEmpty(description?.credentials?.space, "cloudFoundryDeployDescription.credentials.space")

    helper.validateNotEmpty(description?.application, "cloudFoundryDeployDescription.application")
    helper.validateNotEmpty(description?.repository, "cloudFoundryDeployDescription.repository")
    helper.validateNotEmpty(description?.artifact, "cloudFoundryDeployDescription.artifact")

    if (description?.targetSize != null) {
      helper.validatePositiveInt(description.targetSize, "cloudFoundryDeployDescription.targetSize")
    }

    if (description?.repository?.contains('{{') && description?.repository?.contains('}}') && !description?.trigger) {
      errors.rejectValue(
          'cloudFoundryDeployDescription.repository',
          'cloudFoundryDeployDescription.repository.templateWithNoParameters')
    }
  }
}
