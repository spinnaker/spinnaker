/*
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.spinnaker.kato.cf.deploy.validators

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kato.cf.deploy.description.CloudFoundryDeployDescription
import com.netflix.spinnaker.kato.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.kato.deploy.DescriptionValidator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

/**
 * Validator for Cloud Foundry deploy description
 *
 *
 */
@Component("cloudFoundryDeployDescriptionValidator")
class CloudFoundryDeployDescriptionValidator extends DescriptionValidator<CloudFoundryDeployDescription> {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, CloudFoundryDeployDescription description, Errors errors) {
    def credentials = null

    if (!description.credentials) {
      errors.rejectValue "credentials", "cloudFoundryDeployDescription.credentials.empty"
    } else {
      credentials = accountCredentialsProvider.getCredentials(description?.credentials?.name)
      if (!(credentials instanceof CloudFoundryAccountCredentials)) {
        errors.rejectValue("credentials", "cloudFoundryDeployDescription.credentials.invalid")
      }
    }
    // TODO Reinstate the validator after the operation if verified
//    if (!description.api) {
//      errors.rejectValue "api", "cloudFoundryDepoyDescription.api.empty"
//    }
//    if (!description.org) {
//      errors.rejectValue "org", "cloudFoundryDepoyDescription.org.empty"
//    }
//    if (!description.space) {
//      errors.rejectValue "space", "cloudFoundryDepoyDescription.space.empty"
//    }
//    if (!description.application) {
//      errors.rejectValue "application", "cloudFoundryDeployDescription.application.empty"
//    }
//    if (!description.artifact) {
//      errors.rejectValue "artifact", "cloudFoundryDepoyDescription.artifact.empty"
//    }
//    if (description.instances != null && description.instances < 1) {
//      errors.rejectValue "instances", "cloudFoundryDeployDescription.instances.invalid", description.instances as String
//    }
  }
}
