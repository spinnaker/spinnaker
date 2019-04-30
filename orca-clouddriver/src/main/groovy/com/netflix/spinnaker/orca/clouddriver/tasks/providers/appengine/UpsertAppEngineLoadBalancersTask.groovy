/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.appengine

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class UpsertAppEngineLoadBalancersTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  static final String CLOUD_OPERATION_TYPE = "upsertLoadBalancer"
  static final String CLOUD_PROVIDER = "appengine"

  @Override
  long getBackoffPeriod() {
    return 2000
  }

  @Override
  long getTimeout() {
    return 300000
  }

  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  @Autowired
  TargetServerGroupResolver resolver

  @Override
  TaskResult execute(Stage stage) {
    def operations = new ArrayList()

    def context = new HashMap(stage.context)
    for (Map loadBalancer : context.loadBalancers) {
      def lbcontext = new HashMap(loadBalancer)
      lbcontext.availabilityZones = lbcontext.availabilityZones ?: [(lbcontext.region): lbcontext.regionZones]
      lbcontext.splitDescription?.allocationDescriptions?.each { Map description ->
        if (description.locatorType == "targetCoordinate") {
          description.serverGroupName = resolveTargetServerGroupName(lbcontext, description)
        }
      }
      def operation = new HashMap()
      operation.put(CLOUD_OPERATION_TYPE, lbcontext)
      operations.add(operation)
    }

    def taskId = kato.requestOperations(CLOUD_PROVIDER, operations)
      .toBlocking()
      .first()

    def outputs = [
      "notification.type"   : CLOUD_OPERATION_TYPE.toLowerCase(),
      "kato.result.expected": true,
      "kato.last.task.id"   : taskId,
      "targets"             : operations.collect {
        [
          credentials      : it[CLOUD_OPERATION_TYPE].account,
          availabilityZones: it[CLOUD_OPERATION_TYPE].availabilityZones,
          name             : it[CLOUD_OPERATION_TYPE].name,
        ]
      }
    ]
    TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build()
  }

  String resolveTargetServerGroupName(Map loadBalancer, Map allocationDescription) {
    ["cluster", "target", "credentials", "region"].each {
      if (!(loadBalancer[it] || allocationDescription[it])) {
        throw new IllegalArgumentException("Could not resolve target server group, $it not specified.")
      }
    }

    def params = new TargetServerGroup.Params(
      cloudProvider: CLOUD_PROVIDER,
      credentials: loadBalancer.credentials,
      cluster: allocationDescription.cluster,
      locations: [new Location(type: Location.Type.REGION, value: loadBalancer.region)],
      target: TargetServerGroup.Params.Target.valueOf(allocationDescription.target)
    )

    def serverGroups = resolver.resolveByParams(params)
    return serverGroups[0].getName()
  }
}
