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

package com.netflix.spinnaker.clouddriver.google.deploy.converters

import com.netflix.spinnaker.clouddriver.google.GoogleOperation
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer.DeleteGoogleHttpLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer.DeleteGoogleLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerType
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@GoogleOperation(AtomicOperations.DELETE_LOAD_BALANCER)
@Component("deleteGoogleLoadBalancerDescription")
class DeleteGoogleLoadBalancerAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  AtomicOperation convertOperation(Map input) {
    DeleteGoogleLoadBalancerDescription description = convertDescription(input)
    switch (description.loadBalancerType) {
      case GoogleLoadBalancerType.NETWORK:
        return new DeleteGoogleLoadBalancerAtomicOperation(description)
        break
      case GoogleLoadBalancerType.HTTP:
        return new DeleteGoogleHttpLoadBalancerAtomicOperation(description)
        break
      default:
        // TODO(jacobkiefer): This is for backwards compatibility for L4 deletion.
        return new DeleteGoogleLoadBalancerAtomicOperation(description)
        break
    }
  }

  @Override
  DeleteGoogleLoadBalancerDescription convertDescription(Map input) {
    GoogleAtomicOperationConverterHelper.convertDescription(input, this, DeleteGoogleLoadBalancerDescription)
  }
}
