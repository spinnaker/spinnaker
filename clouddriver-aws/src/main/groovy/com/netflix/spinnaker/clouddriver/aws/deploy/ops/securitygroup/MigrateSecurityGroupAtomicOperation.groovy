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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup

import com.netflix.spinnaker.clouddriver.aws.deploy.description.MigrateSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateSecurityGroupStrategy
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

import static com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup

class MigrateSecurityGroupAtomicOperation implements AtomicOperation<Void> {

  final MigrateSecurityGroupDescription description

  MigrateSecurityGroupAtomicOperation(MigrateSecurityGroupDescription description) {
    this.description = description
  }

  @Autowired
  SecurityGroupLookupFactory securityGroupLookupFactory

  @Autowired
  MigrateSecurityGroupStrategy migrationStrategy


  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Void operate(List priorOutputs) {
    SecurityGroupLookup sourceLookup = securityGroupLookupFactory.getInstance(description.source.region)
    SecurityGroupLookup targetLookup = description.source.region == description.target.region ?
      sourceLookup :
      securityGroupLookupFactory.getInstance(description.target.region)

    task.addResultObjects( [new SecurityGroupMigrator(sourceLookup, targetLookup, migrationStrategy, description.source, description.target)
      .migrate(description.dryRun)])
  }
}
