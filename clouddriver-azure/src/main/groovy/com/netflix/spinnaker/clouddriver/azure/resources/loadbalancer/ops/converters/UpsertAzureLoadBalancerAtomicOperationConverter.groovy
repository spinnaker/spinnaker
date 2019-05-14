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

package com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.ops.converters

import com.netflix.spinnaker.clouddriver.azure.AzureOperation
import com.netflix.spinnaker.clouddriver.azure.common.AzureAtomicOperationConverterHelper
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.model.AzureAppGatewayDescription
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.ops.UpsertAzureAppGatewayAtomicOperation
import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancer
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.ops.UpsertAzureLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
//@AzureOperation("upsertLoadBalancerL4")
@AzureOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("upsertAzureLoadBalancerDescription")
class UpsertAzureLoadBalancerAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  UpsertAzureLoadBalancerAtomicOperationConverter() {
    log.info("Constructor....UpsertAzureLoadBalancerAtomicOperationConverter")
  }

  AtomicOperation convertOperation(Map input) {
    String loadBalancerType = input.get("loadBalancerType")
    if(loadBalancerType == null || loadBalancerType.equals(AzureLoadBalancer.AzureLoadBalancerType.AZURE_APPLICATION_GATEWAY.toString())) {
      return new UpsertAzureAppGatewayAtomicOperation((AzureAppGatewayDescription)convertDescription(input))
    }else {
      return new UpsertAzureLoadBalancerAtomicOperation((AzureLoadBalancerDescription)convertDescription(input))
    }
  }

  AzureResourceOpsDescription convertDescription(Map input) {
    String loadBalancerType = input.get("loadBalancerType")
    if(loadBalancerType == null || loadBalancerType.equals(AzureLoadBalancer.AzureLoadBalancerType.AZURE_APPLICATION_GATEWAY.toString())) {
      return AzureAtomicOperationConverterHelper.
        convertDescription(input, this, AzureAppGatewayDescription) as AzureAppGatewayDescription
    }else {
      return AzureAtomicOperationConverterHelper.
        convertDescription(input, this, AzureLoadBalancerDescription) as AzureLoadBalancerDescription
    }
  }
}

