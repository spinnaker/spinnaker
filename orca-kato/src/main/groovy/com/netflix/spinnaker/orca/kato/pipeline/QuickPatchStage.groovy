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

package com.netflix.spinnaker.orca.kato.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.kato.tasks.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForDownInstanceHealthTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForUpInstanceHealthTask
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.ParallelStage
import com.netflix.spinnaker.orca.pipeline.StepProvider
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import com.netflix.spinnaker.orca.kato.tasks.quip.TriggerQuipTask
import com.netflix.spinnaker.orca.kato.tasks.quip.MonitorQuipTask
import com.netflix.spinnaker.orca.kato.tasks.quip.VerifyQuipTask

/**
 * Wrapper stage over BuilkQuickPatchStage.  We do this so we can reuse the same steps whether or not we are doing
 * a rolling quick patch.  The difference is that the rolling version will only update one instance at a time while
 * the non-rolling version will act on all instances at once.  This is done by controlling the instances we
 * send to BuilkQuickPatchStage.
 */
@Slf4j
@Component
class QuickPatchStage extends LinearStage {

  @Autowired BulkQuickPatchStage bulkQuickPatchStage
  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  public static final String MAYO_CONFIG_TYPE = "quickPatch"

  QuickPatchStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    def instances = getInstancesForCluster(stage)
    List<Step> steps = []

    // rolling means instances in the asg will be updated sequentially
    if(stage.context.rollingPatch) {
      instances.each { key, value ->
        def instance = [:]
        instance.put(key, value)
        def nextStageContext = [:]
        nextStageContext.putAll(stage.context)
        nextStageContext << [instances : instance]
        nextStageContext.put("instanceIds", [key]) // for WaitForDown/UpInstancesTask
        injectAfter(stage, "bulkQuickPatchStage", bulkQuickPatchStage, nextStageContext)
      }
    } else { // quickpatch all instances in the asg at once
      def nextStageContext = [:]
      nextStageContext.putAll(stage.context)
      nextStageContext << [instances : instances]
      nextStageContext.put("instanceIds", instances.collect {key, value -> key}) // for WaitForDown/UpInstancesTask
      injectAfter(stage, "bulkQuickPatchStage", bulkQuickPatchStage, nextStageContext)
    }
    // mark as SUCCEEDED otherwise a stage w/o child tasks will remain in NOT_STARTED
    stage.status = ExecutionStatus.SUCCEEDED
    return steps
  }

  Map getInstancesForCluster(Stage stage) {
    def response = oortService.getCluster(stage.context.application, stage.context.account, stage.context.clusterName, stage.context.providerType ?: "aws")
    def oortCluster = objectMapper.readValue(response.body.in().text, Map)
    def instanceMap = [:]

    if (!oortCluster || !oortCluster.serverGroups) {
      throw new RuntimeException("unable to find any server groups")
    }

    def asgsForCluster = oortCluster.serverGroups.findAll {
      it.region == stage.context.region
    }

    //verify that there is only one ASG, maybe support it in the future
    if (asgsForCluster.size() != 1) {
      throw new RuntimeException("quick patch only runs if there is a single server group in the cluster")
    }

    asgsForCluster.get(0).instances.each { instance ->
      String hostName = instance.publicDnsName
      if(!hostName || hostName.isEmpty()) { // some instances dont have a public address, fall back to the private ip
        hostName = instance.privateIpAddress
      }

     int index = -1
     instance.health.eachWithIndex { health, idx ->
       if (health.healthCheckUrl != null && !health.healthCheckUrl.isEmpty()) {
         index = idx
       }
     }

      if(index == -1 || instance.health.get(index).status == "STARTING") {
        throw new RuntimeException("at least one instance is down or in the STARTING state, exiting")
      }

      String healthCheckUrl = instance.health.get(index).healthCheckUrl
      Map instanceInfo = [hostName : hostName, healthCheckUrl : healthCheckUrl]
      instanceMap.put(instance.instanceId, instanceInfo)
    }

    if(instanceMap.size() == 0 ) {
      throw new RuntimeException("could not find any instances")
    }

    stage.context.put("deploy.server.groups", [region : asgsForCluster.get(0).name]) // for ServerGroupCacheForceRefreshTask
    return instanceMap
  }
}
