/*
 * Copyright 2016 Google, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.validators.instance

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.instance.AbstractRegistrationKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.validators.StandardKubernetesAttributeValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@KubernetesOperation(AtomicOperations.DEREGISTER_INSTANCES_FROM_LOAD_BALANCER)
@Component
class DeregisterKubernetesAtomicOperationValidator extends DescriptionValidator<AbstractRegistrationKubernetesAtomicOperationDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, AbstractRegistrationKubernetesAtomicOperationDescription description, Errors errors) {
    def helper = new StandardKubernetesAttributeValidator("deregisterKubernetesAtomicOperationDescription", errors)

    AbstractRegistrationKubernetesAtomicOperationValidator.validate(description, helper, accountCredentialsProvider)
  }
}
