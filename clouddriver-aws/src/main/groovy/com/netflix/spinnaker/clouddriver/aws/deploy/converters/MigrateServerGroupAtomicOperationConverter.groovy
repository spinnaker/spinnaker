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
import com.netflix.spinnaker.clouddriver.aws.deploy.description.MigrateServerGroupDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup.MigrateServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@AmazonOperation(AtomicOperations.MIGRATE_SERVER_GROUP)
@Component("migrateServerGroupDescription")
class MigrateServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  AtomicOperation convertOperation(Map input) {
    new MigrateServerGroupAtomicOperation(convertDescription(input))
  }

  @Override
  MigrateServerGroupDescription convertDescription(Map input) {
    def converted = objectMapper.convertValue(input, MigrateServerGroupDescription)
    converted.source.credentials = getCredentialsObject(input.source.credentials as String)
    converted.target.credentials = getCredentialsObject(input.target.credentials as String)
    converted
  }
}
