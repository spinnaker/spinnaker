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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.validators.servergroup

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.CloneKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.validators.KubernetesContainerValidator
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.validators.StandardKubernetesAttributeValidator
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@KubernetesOperation(AtomicOperations.CLONE_SERVER_GROUP)
@Component
class CloneKubernetesAtomicOperationValidator extends DescriptionValidator<CloneKubernetesAtomicOperationDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, CloneKubernetesAtomicOperationDescription description, Errors errors) {
    def helper = new StandardKubernetesAttributeValidator("cloneKubernetesAtomicOperationDescription", errors)
    if (!helper.validateCredentials(description.account, accountCredentialsProvider)) {
      return
    }

    KubernetesV1Credentials credentials = (KubernetesV1Credentials) accountCredentialsProvider.getCredentials(description.account).credentials

    helper.validateServerGroupCloneSource(description.source, "source")
    if (description.application) {
      helper.validateApplication(description.application, "application")
    }

    if (description.stack) {
      helper.validateStack(description.stack, "stack")
    }

    if (description.freeFormDetails) {
      helper.validateDetails(description.freeFormDetails, "details")
    }

    if (description.targetSize != null) {
      helper.validateNonNegative(description.targetSize, "targetSize")
    }

    if (description.namespace) {
      helper.validateNamespace(credentials, description.namespace, "namespace")
    }

    if (description.restartPolicy) {
      helper.validateRestartPolicy(description.restartPolicy, "restartPolicy")
    }

    if (description.loadBalancers) {
      description.loadBalancers.eachWithIndex { name, idx ->
        helper.validateName(name, "loadBalancers[${idx}]")
      }
    }

    if (description.securityGroups) {
      description.securityGroups.eachWithIndex { name, idx ->
        helper.validateName(name, "securityGroups[${idx}]")
      }
    }

    if (description.containers) {
      description.containers.eachWithIndex { container, idx ->
        KubernetesContainerValidator.validate(container, helper, "container[${idx}]")
      }
    }
  }
}
