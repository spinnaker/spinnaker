/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.titus.TitusOperation
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DeleteTitusScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.titus.deploy.ops.DeleteTitusScalingPolicyAtomicOperation
import org.springframework.stereotype.Component

@Component('titusDeleteScalingPolicyDescription')
@TitusOperation(AtomicOperations.DELETE_SCALING_POLICY)
class DeleteTitusScalingPolicyAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  AtomicOperation convertOperation(Map input) {
    new DeleteTitusScalingPolicyAtomicOperation(convertDescription(input))
  }

  @Override
  DeleteTitusScalingPolicyDescription convertDescription(Map input) {
    DeleteTitusScalingPolicyDescription converted = getObjectMapper().convertValue(input, DeleteTitusScalingPolicyDescription);
    converted.credentials = getCredentialsObject(input.credentials as String)
    converted
  }
}
