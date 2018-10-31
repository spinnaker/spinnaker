/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.op

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.CreateLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.oracle.bmc.loadbalancer.model.BackendSetDetails
import com.oracle.bmc.loadbalancer.model.CreateBackendSetDetails
import com.oracle.bmc.loadbalancer.model.CreateLoadBalancerDetails
import com.oracle.bmc.loadbalancer.model.HealthCheckerDetails
import com.oracle.bmc.loadbalancer.model.ListenerDetails
import com.oracle.bmc.loadbalancer.model.UpdateBackendSetDetails
import com.oracle.bmc.loadbalancer.model.UpdateListenerDetails
import com.oracle.bmc.loadbalancer.requests.CreateLoadBalancerRequest
import com.oracle.bmc.loadbalancer.requests.CreateBackendSetRequest
import com.oracle.bmc.loadbalancer.requests.UpdateBackendSetRequest
import com.oracle.bmc.loadbalancer.requests.UpdateListenerRequest
import groovy.util.logging.Slf4j

@Slf4j
class CreateOracleLoadBalancerAtomicOperation implements AtomicOperation<Map> {

  private final CreateLoadBalancerDescription description

  private static final String BASE_PHASE = "CREATE_LOADBALANCER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  CreateOracleLoadBalancerAtomicOperation(CreateLoadBalancerDescription description) {
    this.description = description
  }

  UpdateBackendSetDetails toUpdate(BackendSetDetails details) {
    UpdateBackendSetDetails.Builder builder = UpdateBackendSetDetails.builder().policy(details.policy)
    if (details.healthChecker) {
      builder.healthChecker(details.healthChecker)
    }
    if (details.sessionPersistenceConfiguration) {
      builder.sessionPersistenceConfiguration(details.sessionPersistenceConfiguration)
    }
    if (details.sslConfiguration) {
      builder.sslConfiguration(details.sslConfiguration)
    }
    if (details.backends) {
      builder.backends(details.backends)
    }
    return builder.build()
  }

  CreateBackendSetDetails toCreate(BackendSetDetails details, String name) {
    CreateBackendSetDetails.Builder builder = CreateBackendSetDetails.builder().policy(details.policy).name(name)
    if (details.healthChecker) {
      builder.healthChecker(details.healthChecker)
    }
    if (details.sessionPersistenceConfiguration) {
      builder.sessionPersistenceConfiguration(details.sessionPersistenceConfiguration)
    }
    if (details.sslConfiguration) {
      builder.sslConfiguration(details.sslConfiguration)
    }
    return builder.build()
  }
  
  UpdateListenerDetails toUpdate(ListenerDetails details) {
    UpdateListenerDetails.Builder builder = UpdateListenerDetails.builder()
      .protocol(details.protocol).port(details.port)
      .defaultBackendSetName(details.defaultBackendSetName)
    if (details.connectionConfiguration) {
      builder.connectionConfiguration(details.connectionConfiguration)
    }
    if (details.hostnameNames) {
      builder.hostnameNames(details.hostnameNames)
    }
    if (details.sslConfiguration) {
      builder.sslConfiguration(details.sslConfiguration)
    }
    if (details.pathRouteSetName) {
      builder.pathRouteSetName(details.pathRouteSetName)
    }
    return builder.build()
  }
  
  void update(Task task) {
      task.updateStatus(BASE_PHASE, "Update LB: ${description.qualifiedName()}")
      if (description.backendSets) {
        //TODO update | create | delete
        description.backendSets.each { name, details -> 
          def rs = description.credentials.loadBalancerClient.createBackendSet(
            CreateBackendSetRequest.builder().loadBalancerId(description.loadBalancerId)
            .createBackendSetDetails(toCreate(details, name)).build())
          task.updateStatus(BASE_PHASE, "CreateBackendSetRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}")
        }
      }
      if (description.listeners) {
        //TODO update | create | delete
        description.listeners.each { name, listener -> 
          def rs = description.credentials.loadBalancerClient.updateListener(
            UpdateListenerRequest.builder().loadBalancerId(description.loadBalancerId).listenerName(name)
            .updateListenerDetails(toUpdate(listener)).build())
          task.updateStatus(BASE_PHASE, "UpdateListenerRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}")
        }
      }
  } 
  
  void create(Task task) {
    def clusterName = description.qualifiedName()
    task.updateStatus(BASE_PHASE, "Create LB: ${description.qualifiedName()}")
    def lbDetails = CreateLoadBalancerDetails.builder()
        .displayName(clusterName)
        .compartmentId(description.credentials.compartmentId)
        .shapeName(description.shape)
        .subnetIds(description.subnetIds)
    if (description.isPrivate) {
        lbDetails.isPrivate(description.isPrivate)
    }
    if (description.certificates) {
      lbDetails.certificates(description.certificates)
    }
    if (description.backendSets) {
      lbDetails.backendSets(description.backendSets)
    }
    if (description.listeners) {
      lbDetails.listeners(description.listeners)
    }
    def rs = description.credentials.loadBalancerClient.createLoadBalancer(
      CreateLoadBalancerRequest.builder().createLoadBalancerDetails(lbDetails.build()).build())
    task.updateStatus(BASE_PHASE, "Create LB rq submitted - work request id: ${rs.getOpcWorkRequestId()}")
    OracleWorkRequestPoller.poll(rs.getOpcWorkRequestId(), BASE_PHASE, task, description.credentials.loadBalancerClient)
  }
   
  @Override
  Map operate(List priorOutputs) {
    def task = getTask()
    if (description.loadBalancerId) {
      update(task)
    } else {
      create(task)
    }
    return [loadBalancers:
              [(description.credentials.region): [name: description.qualifiedName()]]]
  }
}
