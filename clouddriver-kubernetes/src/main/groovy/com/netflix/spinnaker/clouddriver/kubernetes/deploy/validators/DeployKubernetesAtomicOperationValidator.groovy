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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@KubernetesOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("deployKubernetesAtomicOperationValidator")
class DeployKubernetesAtomicOperationValidator extends DescriptionValidator<DeployKubernetesAtomicOperationDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, DeployKubernetesAtomicOperationDescription description, Errors errors) {
    def helper = new StandardKubernetesAttributeValidator("deployKubernetesAtomicOperationDescription", errors)

    if (!helper.validateCredentials(description.credentials, accountCredentialsProvider)) {
      return
    }

    KubernetesCredentials credentials = (KubernetesCredentials) accountCredentialsProvider.getCredentials(description.credentials).credentials

    helper.validateApplication(description.application, "application")
    helper.validateStack(description.stack, "stack")
    helper.validateDetails(description.freeFormDetails, "details")
    helper.validateNonNegative(description.targetSize, "targetSize")
    helper.validateNamespace(credentials, description.namespace, "namespace")

    description.loadBalancers.eachWithIndex { name, idx ->
      helper.validateName(name, "loadBalancers[${idx}]")
    }

    description.securityGroups.eachWithIndex { name, idx ->
      helper.validateName(name, "securityGroups[${idx}]")
    }

    helper.validateNotEmpty(description.containers, "containers")
    description.containers.eachWithIndex { container, idx ->
      KubernetesContainerValidator.validate(container, helper, "container[${idx}]")
    }
  }
}
