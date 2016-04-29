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
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.ops.UpsertAzureAppGatewayAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@AzureOperation("upsertAppGateway")
// TODO: change operation type to AtomicOperations.UPSERT_LOAD_BALANCER after we retire AzureLoadBalancer
//@AzureOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("upsertAzureAppGatewayDescription")
class UpsertAzureAppGatewayAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  UpsertAzureAppGatewayAtomicOperationConverter() {
    log.info("Constructor....UpsertAzureAppGatewayAtomicOperationConverter")
  }

  AtomicOperation convertOperation(Map input) {
    new UpsertAzureAppGatewayAtomicOperation(convertDescription(input))
  }

  AzureAppGatewayDescription convertDescription(Map input) {
    AzureAtomicOperationConverterHelper.
      convertDescription(input, this, AzureAppGatewayDescription) as AzureAppGatewayDescription
  }
}
