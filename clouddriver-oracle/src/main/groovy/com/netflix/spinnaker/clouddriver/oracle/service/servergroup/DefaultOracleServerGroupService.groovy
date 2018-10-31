/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.service.servergroup

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.oracle.OracleCloudProvider
import com.netflix.spinnaker.clouddriver.oracle.model.OracleInstance
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.oracle.bmc.core.model.LaunchInstanceDetails
import com.oracle.bmc.core.requests.LaunchInstanceRequest
import com.oracle.bmc.core.requests.TerminateInstanceRequest
import com.oracle.bmc.model.BmcException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DefaultOracleServerGroupService implements OracleServerGroupService {

  private static final String DEPLOY = "DEPLOY_SERVER_GROUP"
  private static final String DESTROY = "DESTROY_SERVER_GROUP"
  private static final String RESIZE = "RESIZE_SERVER_GROUP"
  private static final String DISABLE = "DISABLE_SERVER_GROUP"
  private static final String ENABLE = "ENABLE_SERVER_GROUP"

  private final OracleServerGroupPersistence persistence;

  @Autowired
  public DefaultOracleServerGroupService(OracleServerGroupPersistence persistence) {
    this.persistence = persistence
  }

  @Override
  List<OracleServerGroup> listAllServerGroups(OracleNamedAccountCredentials creds) {
    def persistenceCtx = new OraclePersistenceContext(creds)
    List<String> sgNames = persistence.listServerGroupNames(persistenceCtx)
    return sgNames.findResults { name ->
      persistence.getServerGroupByName(persistenceCtx, name)
    }
  }

  @Override
  List<String> listServerGroupNamesByClusterName(OracleNamedAccountCredentials creds, String clusterName) {
    def persistenceCtx = new OraclePersistenceContext(creds)
    List<String> sgNames = persistence.listServerGroupNames(persistenceCtx)
    return sgNames.findAll { clusterName == Names.parseName(it)?.cluster }
  }

  @Override
  OracleServerGroup getServerGroup(OracleNamedAccountCredentials creds, String application, String name) {
    def persistenceCtx = new OraclePersistenceContext(creds)
    List<String> sgNames = persistence.listServerGroupNames(persistenceCtx)
    List<String> sgNamesInApp = sgNames.findAll { application == Names.parseName(it)?.app }
    String foundName = sgNamesInApp.find { name == it }
    if (foundName) {
      return persistence.getServerGroupByName(persistenceCtx, name)
    }
    return null;
  }

  @Override
  void createServerGroup(Task task, OracleServerGroup sg) {  
    def instances = [] as Set
    //if overList createInstance throws com.oracle.bmc.model.BmcException: (400, LimitExceeded, false) 
    def errors = []
    try {
      for (int i = 0; i < sg.targetSize; i++) {
        instances << createInstance(sg, i)
      }
    } catch (BmcException e) {
      task.updateStatus DEPLOY, "Creating instance failed: $e"
      errors << e
    }
    if (errors) {    
      if (instances.size() > 0) {
        task.updateStatus DEPLOY, "ServerGroup created with errors: $errors"
      } else {
        task.updateStatus DEPLOY, "ServerGroup creation failed: $errors"
      }
    }
    if (instances.size() > 0) {
      sg.instances = instances
      persistence.upsertServerGroup(sg)
    }
  }

  @Override
  void updateServerGroup(OracleServerGroup sg) {  
    persistence.upsertServerGroup(sg)
  }

  @Override
  boolean destroyServerGroup(Task task, OracleNamedAccountCredentials creds, String serverGroupName) {
    def persistenceCtx = new OraclePersistenceContext(creds)
    def serverGroup = persistence.getServerGroupByName(persistenceCtx, serverGroupName)
    if (serverGroup != null) {
      task.updateStatus DESTROY, "Found server group: $serverGroup.name"
      if (serverGroup.instances && serverGroup.instances.size() > 0) {   
        for (int i = 0; i < serverGroup.targetSize; i++) {
          def instance = serverGroup.instances[i]
          if (instance) {
            task.updateStatus DESTROY, "Terminating instance: $instance.name"
            terminateInstance(serverGroup, instance)
          }
        }
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
  boolean resizeServerGroup(Task task, OracleNamedAccountCredentials creds, String serverGroupName, Integer targetSize) {
    def persistenceCtx = new OraclePersistenceContext(creds)
    def serverGroup = persistence.getServerGroupByName(persistenceCtx, serverGroupName)
    if (serverGroup != null) {
      task.updateStatus RESIZE, "Found server group: $serverGroup.name resizing to $targetSize"
      if (targetSize > serverGroup.targetSize) {
        int numInstancesToCreate = targetSize - serverGroup.targetSize
        task.updateStatus RESIZE, "Creating $numInstancesToCreate instances"
        increase(task, serverGroup, targetSize)
      } else if (serverGroup.targetSize > targetSize) {
        int numInstancesToTerminate = serverGroup.targetSize - targetSize
        task.updateStatus RESIZE, "Terminating $numInstancesToTerminate instances"
        decrease(task, serverGroup, targetSize)
      } else {
        task.updateStatus RESIZE, "Already running the desired number of instances"
        return true
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
  void disableServerGroup(Task task, OracleNamedAccountCredentials creds, String serverGroupName) {
    def persistenceCtx = new OraclePersistenceContext(creds)
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
  void enableServerGroup(Task task, OracleNamedAccountCredentials creds, String serverGroupName) {
    def persistenceCtx = new OraclePersistenceContext(creds)
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

  private OracleInstance createInstance(OracleServerGroup sg, int i) {
    Map<String, String> metadata = new HashMap<>()
    if (sg.launchConfig["sshAuthorizedKeys"]?.trim()) {
      // "ssh_authorized_keys"* - Provide one or more public SSH keys to be included in the ~/.ssh/authorized_keys file
      // for the default user on the instance.
      // Use a newline character to separate multiple keys.
      metadata.put("ssh_authorized_keys", sg.launchConfig["sshAuthorizedKeys"] as String)
    }

    LaunchInstanceRequest rq = LaunchInstanceRequest.builder().launchInstanceDetails(LaunchInstanceDetails.builder()
      .availabilityDomain(sg.launchConfig["availabilityDomain"] as String)
      .compartmentId(sg.launchConfig["compartmentId"] as String)
      .imageId(sg.launchConfig["imageId"] as String)
      .shape(sg.launchConfig["shape"] as String)
      .subnetId(sg.launchConfig["subnetId"] as String)
      .metadata(metadata)
      .displayName(sg.name + "-$i")
      .build()).build()

    def rs = sg.credentials.computeClient.launchInstance(rq)
    return new OracleInstance(
      name: rs.instance.displayName,
      id: rs.instance.id,
      region: rs.instance.region,
      zone: rs.instance.availabilityDomain,
      healthState: HealthState.Starting,
      cloudProvider: OracleCloudProvider.ID,
      launchTime: rs.instance.timeCreated.time)
  }

  private OracleInstance terminateInstance(OracleServerGroup sg, OracleInstance instance) {
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

  private void increase(Task task, OracleServerGroup serverGroup, int targetSize) {    
    int currentSize = serverGroup.targetSize; 
    def instances = [] as Set
    def errors = []
    for (int i = currentSize; i < targetSize; i++) {
      task.updateStatus RESIZE, "Creating instance: $i"
      try {
        instances << createInstance(serverGroup, i)
      } catch (BmcException e) {
        task.updateStatus RESIZE, "Creating instance failed: $e"
        errors << e
      }
    }
    if (errors) {    
      if (instances.size() > 0) {
        task.updateStatus RESIZE, "ServerGroup resize with errors: $errors"
      } else {
        task.updateStatus RESIZE, "ServerGroup resize failed: $errors"
      }
    }
    for (OracleInstance instance : instances) {
      serverGroup.instances.add(instance)
    }
    serverGroup.targetSize = currentSize + instances.size()
  }

  private void decrease(Task task, OracleServerGroup serverGroup, int targetSize) {   
    def instances = [] as Set
    int currentSize = serverGroup.targetSize;
    for (int i = targetSize; i < currentSize; i++) {
      task.updateStatus RESIZE, "Terminating instance: " + serverGroup.instances[i].name
      instances << terminateInstance(serverGroup, serverGroup.instances[i])
    }
    for (OracleInstance instance : instances) {
        serverGroup.instances.remove(instance)
    }
    serverGroup.targetSize = targetSize
  }
}
