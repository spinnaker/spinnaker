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

package com.netflix.spinnaker.clouddriver.titus.deploy.validators

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.titus.TitanOperation
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitanCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DestroyTitanServerGroupDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component
@TitanOperation(AtomicOperations.DESTROY_SERVER_GROUP)
class DestroyTitanServerGroupDescriptionValidator extends AbstractTitanDescriptionValidatorSupport<DestroyTitanServerGroupDescription> {

  @Autowired
  DestroyTitanServerGroupDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "destroyTitanServerGroupDescription")
  }

  @Override
  void validate(List priorDescriptions, DestroyTitanServerGroupDescription description, Errors errors) {

    super.validate(priorDescriptions, description, errors)

    if (!description.region) {
      errors.rejectValue "region", "destroyTitanServerGroupDescription.region.empty"
    }

    def credentials = getAccountCredentials(description?.credentials?.name)
    if (credentials && !((NetflixTitanCredentials) credentials).regions.name.contains(description.region)) {
      errors.rejectValue "region", "destroyTitanServerGroupDescription.region.not.configured", description.region, "Region not configured"
    }

    if (!description.serverGroupName) {
      errors.rejectValue "serverGroupName", "destroyTitanServerGroupDescription.serverGroupName.empty"
    }
  }

}
