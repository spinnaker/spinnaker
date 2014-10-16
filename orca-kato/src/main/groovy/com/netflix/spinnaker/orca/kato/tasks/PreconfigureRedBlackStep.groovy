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

package com.netflix.spinnaker.orca.kato.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.OortService
import org.springframework.beans.factory.annotation.Autowired

class PreconfigureRedBlackStep implements Task {
  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(TaskContext context) {
    def inputs  = convert(context)
    def account = inputs.account as String
    def cluster = inputs.cluster as String
    def names   = Names.parseName(cluster)
    def lastAsg = getLastAsgName(names.app, account, cluster)
    def availabilityZones = [:]
    availabilityZones[lastAsg.region] = lastAsg.asg?.availabilityZones
    if (!lastAsg) {
      new DefaultTaskResult(TaskResult.Status.TERMINAL)
    } else {
      def outputs = ["disableAsg.asgName": lastAsg.name,
                     "disableAsg.regions": [lastAsg.region],
                     "disableAsg.credentials": account,
                     "copyLastAsg.credentials": account,
                     "copyLastAsg.availabilityZones": availabilityZones,
                     "copyLastAsg.application": names.app]
      if (names.stack) {
        outputs."copyLastAsg.stack" = names.stack
      }
      new DefaultTaskResult(PipelineStatus.SUCCEEDED, outputs)
    }
  }

  private Map convert(TaskContext context) {
    mapper.copy().convertValue(context.getInputs("redBlack"), Map)
  }

  Map getLastAsgName(String app, String account, String cluster) {
    def response = oortService.getCluster(app, account, cluster)
    def json = response.body.in().text
    def map = mapper.readValue(json, Map)
    map?.serverGroups?.sort { a, b -> b.name <=> a.name }?.getAt(0)
  }
}
