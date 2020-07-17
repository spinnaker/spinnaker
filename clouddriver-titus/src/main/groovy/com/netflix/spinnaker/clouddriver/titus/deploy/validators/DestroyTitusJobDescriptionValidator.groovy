/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.clouddriver.titus.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.titus.TitusOperation
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DestroyTitusJobDescription
import org.springframework.stereotype.Component

@Component
@TitusOperation(AtomicOperations.DESTROY_JOB)
class DestroyTitusJobDescriptionValidator extends AbstractTitusDescriptionValidatorSupport<DestroyTitusJobDescription> {

  DestroyTitusJobDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "destroyTitusJobDescription")
  }

  @Override
  void validate(List priorDescriptions, DestroyTitusJobDescription description, ValidationErrors errors) {
    super.validate(priorDescriptions, description, errors)

    if (!description.region) {
      errors.rejectValue "region", "destroyTitusJobDescription.region.empty"
    }

    def credentials = getAccountCredentials(description?.credentials?.name)
    if (credentials && !((NetflixTitusCredentials) credentials).regions.name.contains(description.region)) {
      errors.rejectValue "region", "destroyTitusJobDescription.region.not.configured", description.region, "Region not configured"
    }

    if (!description.jobId) {
      errors.rejectValue "jobId", "destroyTitusJobDescription.jobId.empty"
    }
  }
}
