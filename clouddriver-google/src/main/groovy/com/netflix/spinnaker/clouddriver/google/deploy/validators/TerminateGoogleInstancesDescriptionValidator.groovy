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
import com.netflix.spinnaker.clouddriver.google.GoogleOperation
import com.netflix.spinnaker.clouddriver.google.deploy.description.TerminateGoogleInstancesDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@GoogleOperation(AtomicOperations.TERMINATE_INSTANCES)
@Component
class TerminateGoogleInstancesDescriptionValidator extends DescriptionValidator<TerminateGoogleInstancesDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, TerminateGoogleInstancesDescription description, Errors errors) {
    StandardGceAttributeValidator helper = new StandardGceAttributeValidator("terminateGoogleInstancesDescription", errors)

    helper.validateCredentials(description.accountName, accountCredentialsProvider)

    if (description.serverGroupName) {
      helper.validateRegion(description.region)
    } else {
      helper.validateZone(description.zone)
    }

    helper.validateInstanceIds(description.instanceIds)
  }
}
