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
import com.netflix.spinnaker.orca.oort.InstanceService
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.OperatingSystem
import com.netflix.spinnaker.orca.pipeline.util.PackageInfo
import groovy.util.logging.Slf4j
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RestAdapter
import retrofit.RetrofitError

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

  private static INSTANCE_VERSION_SLEEP = 10000

  QuickPatchStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    List<Step> steps = []

    OperatingSystem operatingSystem = OperatingSystem.valueOf(stage.context.baseOs)
    PackageInfo packageInfo = new PackageInfo(stage, operatingSystem.packageType.packageType,
      operatingSystem.packageType.versionDelimiter, true, true, objectMapper)
    String packageName = stage.context?.package
    String version = stage.context?.patchVersion ?:  packageInfo.findTargetPackage()?.packageVersion

    stage.context.put("version", version) // so the ui can display the discovered package version and we can verify for skipUpToDate
    def instances = getInstancesForCluster(stage)

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
    // infer the app from the cluster prefix since we want to be able to quick patch different apps from the same pipeline
    def app
    if(stage.context.clusterName.indexOf("-") > 0) {
      app = stage.context.clusterName.substring(0, stage.context.clusterName.indexOf("-"))
    } else {
      app = stage.context.clusterName
    }

    def response = oortService.getCluster(app, stage.context.account, stage.context.clusterName, stage.context.providerType ?: "aws")
    def oortCluster = objectMapper.readValue(response.body.in().text, Map)
    def instanceMap = [:]
    def skippedMap = [:]

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

      // optionally check the installed package version and skip if == target version
      if(stage.context.skipUpToDate && getAppVersion(hostName, stage.context.package) == stage.context.version) {
        skippedMap.put(instance.instanceId, instanceInfo)
      } else {
        instanceMap.put(instance.instanceId, instanceInfo)
      }
    }

    if(instanceMap.size() == 0 ) {
      throw new RuntimeException("could not find any instances")
    }
    stage.context.put("skippedInstances", skippedMap)
    stage.context.put("deploy.server.groups", [region : asgsForCluster.get(0).name]) // for ServerGroupCacheForceRefreshTask
    return instanceMap
  }

  String getAppVersion(String hostName, String packageName) {
    InstanceService instanceService = createInstanceService("http://${hostName}:5050")
    int retries = 5;
    def instanceResponse
    String version

    while(retries) {
      try {
        instanceResponse  = instanceService.getCurrentVersion(packageName)
        version = objectMapper.readValue(instanceResponse.body.in().text, Map)?.version
      } catch (RetrofitError e) {
        //retry
      }

      if(!version || version.isEmpty()) {
        sleep(INSTANCE_VERSION_SLEEP)
      } else {
        break
      }
      --retries
    }

    // instead of failing the stage if we can't detect the version, try to install new version anyway
    return version
  }

  InstanceService createInstanceService(String address) {
    RestAdapter restAdapter = new RestAdapter.Builder()
      .setEndpoint(address)
      .build()
    return restAdapter.create(InstanceService.class)
  }
}
