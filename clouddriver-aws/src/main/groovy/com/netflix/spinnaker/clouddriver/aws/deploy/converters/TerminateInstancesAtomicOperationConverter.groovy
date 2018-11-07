/*
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.aws.deploy.converters

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonInstanceProvider
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.aws.deploy.description.TerminateInstancesDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.TerminateInstancesAtomicOperation
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component("terminateInstancesDescription")
@AmazonOperation(AtomicOperations.TERMINATE_INSTANCES)
class TerminateInstancesAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Autowired
  AmazonInstanceProvider amazonInstanceProvider

  @Override
  AtomicOperation convertOperation(Map input) {
    new TerminateInstancesAtomicOperation(convertDescription(input))
  }

  @Override
  TerminateInstancesDescription convertDescription(Map input) {
    def converted = objectMapper.convertValue(input, TerminateInstancesDescription)
    converted.credentials = getCredentialsObject(input.credentials as String)

    try {
      def serverGroups = converted.instanceIds.findResults {
        def instance = amazonInstanceProvider.getInstance(converted.credentials.name, converted.region, it)
        return instance?.any()?.get("serverGroup")
      } as Set<String>
      converted.serverGroups = serverGroups
    } catch (Exception e) {
      converted.serverGroups = []
      log.error(
        "Unable to determine server groups for instances (instanceIds: {}, account: {}, region: {})",
        converted.instanceIds,
        converted.credentials.name,
        converted.region,
        e
      )
    }

    converted
  }
}
