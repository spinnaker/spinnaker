/*
 * Copyright 2016 Netflix, Inc.
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

import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.titus.TitusOperation
import com.netflix.spinnaker.clouddriver.titus.deploy.description.EnableDisableInstanceDiscoveryDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@TitusOperation(AtomicOperations.DISABLE_INSTANCES_IN_DISCOVERY)
class DisableTitusInstancesInDiscoveryDescriptionValidator
  extends AbstractTitusDescriptionValidatorSupport<EnableDisableInstanceDiscoveryDescription> {

  @Autowired
  DisableTitusInstancesInDiscoveryDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "disableInstacesInDiscoveryDescription")
  }

  @Override
  void validate(List priorDescriptions, EnableDisableInstanceDiscoveryDescription description, ValidationErrors errors) {
    def key = description.class.simpleName
    validateAsgNameAndRegionAndInstanceIds(description, errors)

    if (!description.credentials.discoveryEnabled) {
      errors.rejectValue("discovery", "${key}.credentials.discovery.not.configured")
    }
  }
}
