/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.converters

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation
import com.netflix.spinnaker.clouddriver.aws.deploy.description.MigrateClusterConfigurationsDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup.MigrateClusterConfigurationsAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@AmazonOperation(AtomicOperations.MIGRATE_CLUSTER_CONFIGURATIONS)
@Component("migrateClusterConfigurationsDescription")
class MigrateClusterConfigurationsAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  AtomicOperation convertOperation(Map input) {
    new MigrateClusterConfigurationsAtomicOperation(convertDescription(input))
  }

  @Override
  MigrateClusterConfigurationsDescription convertDescription(Map input) {
    if (input.regionMapping) {
      ((Map<String, Object>) input.regionMapping).keySet().each { i ->
        if (input.regionMapping[i] instanceof String) {
          input.regionMapping[i] = [ (input.regionMapping[i]) : []]
        }
      };
    }
    def converted = objectMapper.convertValue(input, MigrateClusterConfigurationsDescription)
    converted.sources.each {
      it.credentials = getCredentialsObject(it.cluster.account as String)
      converted.credentials.add(it.credentials)
    }
    converted.accountMapping.values().each { converted.credentials.add(getCredentialsObject(it)) }
    converted
  }
}
