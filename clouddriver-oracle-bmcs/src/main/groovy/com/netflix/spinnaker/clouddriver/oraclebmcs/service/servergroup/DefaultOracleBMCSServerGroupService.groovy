/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.service.servergroup

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.oraclebmcs.OracleBMCSCloudProvider
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSInstance
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSServerGroup
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.oracle.bmc.core.model.LaunchInstanceDetails
import com.oracle.bmc.core.requests.LaunchInstanceRequest
import com.oracle.bmc.core.requests.TerminateInstanceRequest
import com.oracle.bmc.model.BmcException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DefaultOracleBMCSServerGroupService implements OracleBMCSServerGroupService {

  private static final String DESTROY = "DESTROY_SERVER_GROUP"
  private static final String RESIZE = "RESIZE_SERVER_GROUP"
  private static final String DISABLE = "DISABLE_SERVER_GROUP"
  private static final String ENABLE = "ENABLE_SERVER_GROUP"

  private final OracleBMCSServerGroupPersistence persistence;

  @Autowired
  public DefaultOracleBMCSServerGroupService(OracleBMCSServerGroupPersistence persistence) {
    this.persistence = persistence
  }

  @Override
  List<OracleBMCSServerGroup> listAllServerGroups(OracleBMCSNamedAccountCredentials creds) {
    def persistenceCtx = new OracleBMCSPersistenceContext(creds)
    List<String> sgNames = persistence.listServerGroupNames(persistenceCtx)
    return sgNames.findResults { name ->
      persistence.getServerGroupByName(persistenceCtx, name)
    }
  }

  @Override
  List<String> listServerGroupNamesByClusterName(OracleBMCSNamedAccountCredentials creds, String clusterName) {
    def persistenceCtx = new OracleBMCSPersistenceContext(creds)
    List<String> sgNames = persistence.listServerGroupNames(persistenceCtx)
    return sgNames.findAll { clusterName == Names.parseName(it)?.cluster }
  }

  @Override
  OracleBMCSServerGroup getServerGroup(OracleBMCSNamedAccountCredentials creds, String application, String name) {
    def persistenceCtx = new OracleBMCSPersistenceContext(creds)
    List<String> sgNames = persistence.listServerGroupNames(persistenceCtx)
    List<String> sgNamesInApp = sgNames.findAll { application == Names.parseName(it)?.app }
    String foundName = sgNamesInApp.find { name == it }
    if (foundName) {
      return persistence.getServerGroupByName(persistenceCtx, name)
    }
    return null;
  }

  @Override
  void createServerGroup(OracleBMCSServerGroup sg) {

    def instances = [] as Set
    for (int i = 0; i < sg.targetSize; i++) {
      instances << createInstance(sg, i)
    }
    sg.instances = instances
    persistence.upsertServerGroup(sg)
  }

  @Override
  boolean destroyServerGroup(Task task, OracleBMCSNamedAccountCredentials creds, String serverGroupName) {
    def persistenceCtx = new OracleBMCSPersistenceContext(creds)
    def serverGroup = persistence.getServerGroupByName(persistenceCtx, serverGroupName)
    if (serverGroup != null) {
      task.updateStatus DESTROY, "Found server group: $serverGroup.name"

      for (int i = 0; i < serverGroup.targetSize; i++) {
        def instance = serverGroup.instances[i]
        task.updateStatus DESTROY, "Terminating instance: $instance.name"
        terminateInstance(serverGroup, instance)
      }
      task.updateStatus DESTROY, "Removing persistent data for $serverGroup.name"
      persistence.deleteServerGroup(serverGroup)
      return true
    } else {
      task.updateStatus DESTROY, "Server group not found"
      return false
    }
  }

  @Override
  boolean resizeServerGroup(Task task, OracleBMCSNamedAccountCredentials creds, String serverGroupName, Integer targetSize) {
    def persistenceCtx = new OracleBMCSPersistenceContext(creds)
    def serverGroup = persistence.getServerGroupByName(persistenceCtx, serverGroupName)
    if (serverGroup != null) {
      task.updateStatus DESTROY, "Found server group: $serverGroup.name"

      if (targetSize > serverGroup.targetSize) {
        int numInstancesToCreate = targetSize - serverGroup.targetSize
        task.updateStatus RESIZE, "Creating $numInstancesToCreate instances"

        resize(serverGroup, targetSize, serverGroup.targetSize, targetSize,
          { int i ->
            task.updateStatus RESIZE, "Creating instance: $i"
            return createInstance(serverGroup, i)
          },
          { OracleBMCSInstance instance ->
            serverGroup.instances.add(instance)
          })

      } else if (serverGroup.targetSize > targetSize) {
        int numInstancesToTerminate = serverGroup.targetSize - targetSize
        task.updateStatus RESIZE, "Terminating $numInstancesToTerminate instances"

        resize(serverGroup, targetSize, targetSize, serverGroup.targetSize,
          { int i ->
            task.updateStatus RESIZE, "Terminating instance: " + serverGroup.instances[i].name
            return terminateInstance(serverGroup, serverGroup.instances[i])
          },
          { OracleBMCSInstance instance ->
            serverGroup.instances.remove(instance)
          })

      } else {
        task.updateStatus RESIZE, "Already running the desired number of instances"
      }
      task.updateStatus RESIZE, "Updating persistent data for $serverGroup.name"
      persistence.upsertServerGroup(serverGroup)
      return true
    } else {
      task.updateStatus RESIZE, "Server group not found"
      return false
    }
  }

  @Override
  void disableServerGroup(Task task, OracleBMCSNamedAccountCredentials creds, String serverGroupName) {
    def persistenceCtx = new OracleBMCSPersistenceContext(creds)
    def serverGroup = persistence.getServerGroupByName(persistenceCtx, serverGroupName)
    if (serverGroup != null) {
      task.updateStatus DISABLE, "Found server group: $serverGroup.name"
      serverGroup.disabled = true
      task.updateStatus DISABLE, "Updating persistent data for $serverGroup.name"
      persistence.upsertServerGroup(serverGroup)
    } else {
      task.updateStatus DISABLE, "Server group not found"
    }
  }

  @Override
  void enableServerGroup(Task task, OracleBMCSNamedAccountCredentials creds, String serverGroupName) {
    def persistenceCtx = new OracleBMCSPersistenceContext(creds)
    def serverGroup = persistence.getServerGroupByName(persistenceCtx, serverGroupName)
    if (serverGroup != null) {
      task.updateStatus ENABLE, "Found server group: $serverGroup.name"
      serverGroup.disabled = false
      task.updateStatus ENABLE, "Updating persistent data for $serverGroup.name"
      persistence.upsertServerGroup(serverGroup)
    } else {
      task.updateStatus ENABLE, "Server group not found"
    }
  }

  private OracleBMCSInstance createInstance(OracleBMCSServerGroup sg, int i) {
    LaunchInstanceRequest rq = LaunchInstanceRequest.builder().launchInstanceDetails(LaunchInstanceDetails.builder()
      .availabilityDomain(sg.launchConfig["availabilityDomain"] as String)
      .compartmentId(sg.launchConfig["compartmentId"] as String)
      .imageId(sg.launchConfig["imageId"] as String)
      .shape(sg.launchConfig["shape"] as String)
      .subnetId(sg.launchConfig["subnetId"] as String)
      .displayName(sg.name + "-$i")
      .build()).build()

    def rs = sg.credentials.computeClient.launchInstance(rq)
    return new OracleBMCSInstance(
      name: rs.instance.displayName,
      id: rs.instance.id,
      region: rs.instance.region,
      zone: rs.instance.availabilityDomain,
      healthState: HealthState.Starting,
      cloudProvider: OracleBMCSCloudProvider.ID,
      launchTime: rs.instance.timeCreated.time)
  }

  private OracleBMCSInstance terminateInstance(OracleBMCSServerGroup sg, OracleBMCSInstance instance) {
    TerminateInstanceRequest request = TerminateInstanceRequest.builder()
      .instanceId(instance.id).build()
    try {
      sg.credentials.computeClient.terminateInstance(request)
    } catch (BmcException e) {
      // Ignore missing instances (e.g., terminated manually outside of spinnaker)
      if (e.statusCode != 404) {
        throw e
      }
    }
    return instance
  }

  private void resize(OracleBMCSServerGroup sg, Integer targetSize, int from, int to, Closure operate, Closure update) {
    def instances = [] as Set
    for (int i = from; i < to; i++) {
      instances << operate(i)
    }
    for (OracleBMCSInstance instance : instances) {
      update(instance)
    }
    sg.targetSize = targetSize
  }
}
