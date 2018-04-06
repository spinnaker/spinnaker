/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.securitygroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.securitygroup.UpsertOpenstackSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import groovy.util.logging.Slf4j
import org.apache.commons.lang.StringUtils
import org.openstack4j.model.compute.IPProtocol
import org.openstack4j.model.compute.SecGroupExtension

/**
 * Creates or updates an Openstack security group.
 *
 * Note that this can only manage ingress rules for the security groups. It appears that this is a limitation of the
 * Openstack API itself. Egress rules can be created as part of default rules for security groups, but that needs to
 * be managed in Openstack itself, not through Spinnaker.
 */
@Slf4j
class UpsertOpenstackSecurityGroupAtomicOperation implements AtomicOperation<Void> {

  private final String BASE_PHASE = 'UPSERT_SECURITY_GROUP'
  static final String SELF_REFERENCIAL_RULE = 'SELF'
  UpsertOpenstackSecurityGroupDescription description

  UpsertOpenstackSecurityGroupAtomicOperation(UpsertOpenstackSecurityGroupDescription description) {
    this.description = description
  }

  protected static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  /*
  * Create:
  * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertSecurityGroup": { "region": "west", "name": "sg-test-1", "description": "test", "account": "test", "rules": [ { "ruleType": "TCP", "fromPort": 80, "toPort": 90, "cidr": "0.0.0.0/0"  } ] } } ]' localhost:7002/openstack/ops
  * Update:
  * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertSecurityGroup": { "region": "west", "id": "e56fa7eb-550d-42d4-8d3f-f658fbacd496", "name": "sg-test-1", "description": "test", "account": "test", "rules": [ { "ruleType": "TCP", "fromPort": 80, "toPort": 90, "cidr": "0.0.0.0/0"  } ] } } ]' localhost:7002/openstack/ops
  * Task status:
  * curl -X GET -H "Accept: application/json" localhost:7002/task/1
  */

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Upserting security group ${description.name} in region ${description.region}..."

    OpenstackClientProvider provider = description.credentials.provider

    try {

      // Try getting existing security group, update if needed
      SecGroupExtension securityGroup
      if (StringUtils.isNotEmpty(description.id)) {
        task.updateStatus BASE_PHASE, "Looking up existing security group with id ${description.id}"
        securityGroup = provider.getSecurityGroup(description.region, description.id)
        if (!securityGroup) {
          throw new OpenstackResourceNotFoundException("Could not find securityGroup: $description.id in region: $description.region")
        }
        task.updateStatus BASE_PHASE, "Updating security group with name ${description.name} and description '${description.description}'"
        securityGroup = provider.updateSecurityGroup(description.region, description.id, description.name, description.description)
      } else {
        task.updateStatus BASE_PHASE, "Creating new security group with name ${description.name}"
        securityGroup = provider.createSecurityGroup(description.region, description.name, description.description)
      }

      // TODO: Find the different between existing rules and only apply that instead of deleting and re-creating all the rules
      securityGroup.rules.each { rule ->
        task.updateStatus BASE_PHASE, "Deleting rule ${rule.id}"
        provider.deleteSecurityGroupRule(description.region, rule.id)
      }

      description.rules.each { rule ->
        task.updateStatus BASE_PHASE, "Creating rule for ${rule.cidr} from port ${rule.fromPort} to port ${rule.toPort}"
        String remoteSecurityGroupId = rule.remoteSecurityGroupId == SELF_REFERENCIAL_RULE ? securityGroup.id : rule.remoteSecurityGroupId
        provider.createSecurityGroupRule(description.region,
          securityGroup.id,
          IPProtocol.value(rule.ruleType),
          rule.cidr,
          remoteSecurityGroupId,
          rule.fromPort,
          rule.toPort,
          rule.icmpType,
          rule.icmpCode
        )
      }
      task.updateStatus BASE_PHASE, "Finished upserting security group ${description.name}."
    } catch (OpenstackProviderException e) {
      throw new OpenstackOperationException(AtomicOperations.UPSERT_SECURITY_GROUP, e)
    }
  }
}
