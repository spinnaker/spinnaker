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
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.KubernetesContainerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

class KubernetesContainerValidator {
  static void validate(KubernetesContainerDescription description, StandardKubernetesAttributeValidator helper, String prefix) {
    helper.validateName(description.name, "${prefix}.name")
    helper.validateNotEmpty(description.image, "${prefix}.image")

    if (description.limits) {
      helper.validateCpu(description.limits.cpu, "${prefix}.limits.cpu")
      helper.validateMemory(description.limits.memory, "${prefix}.limits.memory")
    }

    if (description.requests) {
      helper.validateCpu(description.requests.cpu, "${prefix}.requests.cpu")
      helper.validateMemory(description.requests.memory, "${prefix}.requests.memory")
    }
  }
}
