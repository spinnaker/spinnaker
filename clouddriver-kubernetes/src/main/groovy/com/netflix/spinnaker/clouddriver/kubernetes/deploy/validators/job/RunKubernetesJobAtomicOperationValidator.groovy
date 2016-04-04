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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy.validators.job

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.job.KubernetesJobDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.validators.KubernetesContainerValidator
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.validators.KubernetesVolumeSourceValidator
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.validators.StandardKubernetesAttributeValidator
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@KubernetesOperation(AtomicOperations.RUN_JOB)
@Component
class RunKubernetesJobAtomicOperationValidator extends DescriptionValidator<KubernetesJobDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, KubernetesJobDescription description, Errors errors) {
    def helper = new StandardKubernetesAttributeValidator("runKubernetesJobAtomicOperationDescription", errors)

    if (!helper.validateCredentials(description.account, accountCredentialsProvider)) {
      return
    }

    KubernetesCredentials credentials = (KubernetesCredentials) accountCredentialsProvider.getCredentials(description.account).credentials

    helper.validateApplication(description.application, "application")
    helper.validateStack(description.stack, "stack")
    helper.validateDetails(description.freeFormDetails, "details")
    helper.validatePositive(description.completions, "completions")
    helper.validatePositive(description.parallelism, "parallelism")
    helper.validateNamespace(credentials, description.namespace, "namespace")

    description.volumeSources.eachWithIndex { source, idx ->
      KubernetesVolumeSourceValidator.validate(source, helper, "volumeSources[${idx}]")
    }

    description.loadBalancers.eachWithIndex { name, idx ->
      helper.validateName(name, "loadBalancers[${idx}]")
    }

    helper.validateNotEmpty(description.containers, "containers")
    description.containers.eachWithIndex { container, idx ->
      KubernetesContainerValidator.validate(container, helper, "container[${idx}]")
    }
  }
}

