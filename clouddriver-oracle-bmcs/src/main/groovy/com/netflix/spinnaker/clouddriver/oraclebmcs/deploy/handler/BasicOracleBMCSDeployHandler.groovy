/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.handler

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.OracleBMCSServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.BasicOracleBMCSDeployDescription
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSServerGroup
import com.netflix.spinnaker.clouddriver.oraclebmcs.service.servergroup.OracleBMCSServerGroupService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class BasicOracleBMCSDeployHandler implements DeployHandler<BasicOracleBMCSDeployDescription> {

  private static final String BASE_PHASE = "DEPLOY"

  @Autowired
  OracleBMCSServerGroupService oracleBMCSServerGroupService

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  boolean handles(DeployDescription description) {
    description instanceof BasicOracleBMCSDeployDescription
  }

  @Override
  DeploymentResult handle(BasicOracleBMCSDeployDescription description, List priorOutputs) {
    def region = description.region
    def serverGroupNameResolver = new OracleBMCSServerGroupNameResolver(oracleBMCSServerGroupService, description.credentials, region)
    def clusterName = serverGroupNameResolver.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)

    task.updateStatus BASE_PHASE, "Initializing creation of server group for cluster $clusterName ..."

    task.updateStatus BASE_PHASE, "Looking up next sequence..."

    def serverGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)

    task.updateStatus BASE_PHASE, "Produced server group name: $serverGroupName"

    task.updateStatus BASE_PHASE, "Composing server group $serverGroupName..."

    Map<String, Object> launchConfig = [
      "availabilityDomain": description.availabilityDomain,
      "compartmentId"     : description.credentials.compartmentId,
      "imageId"           : description.imageId,
      "shape"             : description.shape,
      "vpcId"             : description.vpcId,
      "subnetId"          : description.subnetId,
      "createdTime"       : System.currentTimeMillis()
    ]

    def sg = new OracleBMCSServerGroup(
      name: serverGroupName,
      region: description.region,
      zone: description.availabilityDomain,
      launchConfig: launchConfig,
      targetSize: description.capacity.desired,
      credentials: description.credentials
    )

    oracleBMCSServerGroupService.createServerGroup(sg)

    task.updateStatus BASE_PHASE, "Done creating server group $serverGroupName."

    DeploymentResult deploymentResult = new DeploymentResult()
    deploymentResult.serverGroupNames = ["$region:$serverGroupName".toString()]
    deploymentResult.serverGroupNameByRegion[region] = serverGroupName
    return deploymentResult
  }
}
