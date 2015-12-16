/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.kato.azure.deploy.converters

import com.netflix.spinnaker.kato.azure.deploy.description.DeleteAzureLoadBalancerDescription
import com.netflix.spinnaker.kato.azure.deploy.ops.loadbalancer.DeleteAzureLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.azure.AzureOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperations
import com.netflix.spinnaker.kato.security.AbstractAtomicOperationsCredentialsSupport
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@AzureOperation(AtomicOperations.DELETE_LOAD_BALANCER)
@Component("deleteAzureLoadBalancerDescription")
class DeleteAzureLoadBalancerAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  public DeleteAzureLoadBalancerAtomicOperationConverter() {
  }

  @Override
  AtomicOperation convertOperation(Map input) {
    new DeleteAzureLoadBalancerAtomicOperation(convertDescription(input))
  }

  @Override
  DeleteAzureLoadBalancerDescription convertDescription(Map input) {
    AzureAtomicOperationConverterHelper.convertDescription(input, this, DeleteAzureLoadBalancerDescription)
  }
}
