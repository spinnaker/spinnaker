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

import com.netflix.spinnaker.clouddriver.azure.common.AzureAtomicOperationConverterHelper
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.UpsertAzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.ops.UpsertAzureLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.azure.AzureOperation
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@AzureOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("upsertAzureLoadBalancerDescription")
class UpsertAzureLoadBalancerAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  public UpsertAzureLoadBalancerAtomicOperationConverter() {
    log.info("Constructor....UpsertAzureLoadBalancerAtomicOperationConverter")
  }

  AtomicOperation convertOperation(Map input) {
    new UpsertAzureLoadBalancerAtomicOperation(convertDescription(input))
  }

  UpsertAzureLoadBalancerDescription convertDescription(Map input) {
    AzureAtomicOperationConverterHelper.convertDescription(input, this, UpsertAzureLoadBalancerDescription)
  }
}

