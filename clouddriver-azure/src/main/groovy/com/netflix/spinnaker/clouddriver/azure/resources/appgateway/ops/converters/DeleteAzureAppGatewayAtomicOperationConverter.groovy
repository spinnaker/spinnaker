/*
 * Copyright 2016 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.appgateway.ops.converters

import com.netflix.spinnaker.clouddriver.azure.AzureOperation
import com.netflix.spinnaker.clouddriver.azure.common.AzureAtomicOperationConverterHelper
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.model.AzureAppGatewayDescription
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.ops.DeleteAzureAppGatewayAtomicOperation
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancer
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.DeleteAzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.ops.DeleteAzureLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@AzureOperation(AtomicOperations.DELETE_LOAD_BALANCER)
@Component("deleteAzureAppGatewayDescription")
class DeleteAzureAppGatewayAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  AtomicOperation convertOperation(Map input) {
    if(input.get("loadBalancerType") == AzureLoadBalancer.AzureLoadBalancerType.AZURE_LOAD_BALANCER.toString()) {
      new DeleteAzureLoadBalancerAtomicOperation(convertALBDescription(input))
    } else {
      new DeleteAzureAppGatewayAtomicOperation(convertDescription(input))
    }
  }

  AzureAppGatewayDescription convertDescription(Map input) {
    AzureAtomicOperationConverterHelper.convertDescription(input, this, AzureAppGatewayDescription) as AzureAppGatewayDescription
  }

  DeleteAzureLoadBalancerDescription convertALBDescription(Map input) {
    AzureAtomicOperationConverterHelper.convertDescription(input, this, DeleteAzureLoadBalancerDescription) as DeleteAzureLoadBalancerDescription
  }
}
