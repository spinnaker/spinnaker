/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.op;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller;
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.UpsertLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.oracle.model.Details;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.oracle.bmc.loadbalancer.model.BackendDetails;
import com.oracle.bmc.loadbalancer.model.BackendSet;
import com.oracle.bmc.loadbalancer.model.BackendSetDetails;
import com.oracle.bmc.loadbalancer.model.Certificate;
import com.oracle.bmc.loadbalancer.model.CertificateDetails;
import com.oracle.bmc.loadbalancer.model.CreateBackendSetDetails;
import com.oracle.bmc.loadbalancer.model.CreateCertificateDetails;
import com.oracle.bmc.loadbalancer.model.CreateListenerDetails;
import com.oracle.bmc.loadbalancer.model.CreateLoadBalancerDetails;
import com.oracle.bmc.loadbalancer.model.ListenerDetails;
import com.oracle.bmc.loadbalancer.model.LoadBalancer;
import com.oracle.bmc.loadbalancer.model.UpdateBackendSetDetails;
import com.oracle.bmc.loadbalancer.model.UpdateListenerDetails;
import com.oracle.bmc.loadbalancer.requests.CreateBackendSetRequest;
import com.oracle.bmc.loadbalancer.requests.CreateCertificateRequest;
import com.oracle.bmc.loadbalancer.requests.CreateListenerRequest;
import com.oracle.bmc.loadbalancer.requests.CreateLoadBalancerRequest;
import com.oracle.bmc.loadbalancer.requests.DeleteBackendSetRequest;
import com.oracle.bmc.loadbalancer.requests.DeleteCertificateRequest;
import com.oracle.bmc.loadbalancer.requests.DeleteListenerRequest;
import com.oracle.bmc.loadbalancer.requests.GetLoadBalancerRequest;
import com.oracle.bmc.loadbalancer.requests.UpdateBackendSetRequest;
import com.oracle.bmc.loadbalancer.requests.UpdateListenerRequest;
import com.oracle.bmc.loadbalancer.responses.CreateBackendSetResponse;
import com.oracle.bmc.loadbalancer.responses.CreateCertificateResponse;
import com.oracle.bmc.loadbalancer.responses.CreateListenerResponse;
import com.oracle.bmc.loadbalancer.responses.CreateLoadBalancerResponse;
import com.oracle.bmc.loadbalancer.responses.DeleteBackendSetResponse;
import com.oracle.bmc.loadbalancer.responses.DeleteCertificateResponse;
import com.oracle.bmc.loadbalancer.responses.DeleteListenerResponse;
import com.oracle.bmc.loadbalancer.responses.UpdateBackendSetResponse;
import com.oracle.bmc.loadbalancer.responses.UpdateListenerResponse;
import com.oracle.bmc.model.BmcException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UpsertOracleLoadBalancerAtomicOperation implements AtomicOperation<Map> {

  private final UpsertLoadBalancerDescription description;

  private static final String CREATE = "CreateLB";
  private static final String UPDATE = "UpdateLB";

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  UpsertOracleLoadBalancerAtomicOperation(UpsertLoadBalancerDescription description) {
    this.description = description;
  }

  UpdateBackendSetDetails toUpdate(BackendSetDetails details, BackendSet existing) {
    UpdateBackendSetDetails.Builder builder =
        UpdateBackendSetDetails.builder().policy(details.getPolicy());
    if (details.getHealthChecker() != null) {
      builder.healthChecker(details.getHealthChecker());
    }
    if (details.getSessionPersistenceConfiguration() != null) {
      builder.sessionPersistenceConfiguration(details.getSessionPersistenceConfiguration());
    }
    if (details.getSslConfiguration() != null) {
      builder.sslConfiguration(details.getSslConfiguration());
    }
    List<BackendDetails> backends =
        existing.getBackends().stream().map(b -> Details.of(b)).collect(Collectors.toList());
    builder.backends(backends);
    return builder.build();
  }

  CreateBackendSetDetails toCreate(BackendSetDetails details, String name) {
    CreateBackendSetDetails.Builder builder =
        CreateBackendSetDetails.builder().policy(details.getPolicy()).name(name);
    if (details.getHealthChecker() != null) {
      builder.healthChecker(details.getHealthChecker());
    }
    if (details.getSessionPersistenceConfiguration() != null) {
      builder.sessionPersistenceConfiguration(details.getSessionPersistenceConfiguration());
    }
    if (details.getSslConfiguration() != null) {
      builder.sslConfiguration(details.getSslConfiguration());
    }
    return builder.build();
  }

  CreateCertificateDetails toCreate(CertificateDetails details, String name) {
    CreateCertificateDetails.Builder builder =
        CreateCertificateDetails.builder().certificateName(name);
    if (details.getCaCertificate() != null) {
      builder.caCertificate(details.getCaCertificate());
    }
    if (details.getPublicCertificate() != null) {
      builder.publicCertificate(details.getPublicCertificate());
    }
    if (details.getPrivateKey() != null) {
      builder.privateKey(details.getPrivateKey());
    }
    if (details.getPassphrase() != null) {
      builder.passphrase(details.getPassphrase());
    }
    return builder.build();
  }

  CreateListenerDetails toCreate(ListenerDetails details, String name) {
    CreateListenerDetails.Builder builder =
        CreateListenerDetails.builder()
            .name(name)
            .protocol(details.getProtocol())
            .port(details.getPort());
    if (details.getConnectionConfiguration() != null) {
      builder.connectionConfiguration(details.getConnectionConfiguration());
    }
    if (details.getDefaultBackendSetName() != null) {
      builder.defaultBackendSetName(details.getDefaultBackendSetName());
    }
    if (details.getHostnameNames() != null) {
      builder.hostnameNames(details.getHostnameNames());
    }
    if (details.getPathRouteSetName() != null) {
      builder.pathRouteSetName(details.getPathRouteSetName());
    }
    if (details.getSslConfiguration() != null) {
      builder.sslConfiguration(details.getSslConfiguration());
    }
    return builder.build();
  }

  UpdateListenerDetails toUpdate(ListenerDetails details) {
    UpdateListenerDetails.Builder builder =
        UpdateListenerDetails.builder().protocol(details.getProtocol()).port(details.getPort());
    if (details.getConnectionConfiguration() != null) {
      builder.connectionConfiguration(details.getConnectionConfiguration());
    }
    if (details.getDefaultBackendSetName() != null) {
      builder.defaultBackendSetName(details.getDefaultBackendSetName());
    }
    if (details.getHostnameNames() != null) {
      builder.hostnameNames(details.getHostnameNames());
    }
    if (details.getPathRouteSetName() != null) {
      builder.pathRouteSetName(details.getPathRouteSetName());
    }
    if (details.getSslConfiguration() != null) {
      builder.sslConfiguration(details.getSslConfiguration());
    }
    return builder.build();
  }

  void updateBackendSets(LoadBalancer lb, Task task) {
    if (lb.getBackendSets() != null) {
      lb.getBackendSets()
          .forEach(
              (name, existingBackendSet) -> {
                BackendSetDetails backendSetUpdate =
                    (description.getBackendSets() != null)
                        ? description.getBackendSets().get(name)
                        : null;
                if (backendSetUpdate != null) {
                  // Update existing BackendSets
                  UpdateBackendSetResponse res =
                      description
                          .getCredentials()
                          .getLoadBalancerClient()
                          .updateBackendSet(
                              UpdateBackendSetRequest.builder()
                                  .loadBalancerId(lb.getId())
                                  .backendSetName(name)
                                  .updateBackendSetDetails(
                                      toUpdate(backendSetUpdate, existingBackendSet))
                                  .build());
                  task.updateStatus(
                      UPDATE,
                      "UpdateBackendSetRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}");
                  OracleWorkRequestPoller.poll(
                      res.getOpcWorkRequestId(),
                      UPDATE,
                      task,
                      description.getCredentials().getLoadBalancerClient());
                } else {
                  // Delete backendSet: must have no backend and no listener
                  DeleteBackendSetResponse res =
                      description
                          .getCredentials()
                          .getLoadBalancerClient()
                          .deleteBackendSet(
                              DeleteBackendSetRequest.builder()
                                  .loadBalancerId(lb.getId())
                                  .backendSetName(name)
                                  .build());
                  task.updateStatus(
                      UPDATE,
                      "DeleteBackendSetRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}");
                  OracleWorkRequestPoller.poll(
                      res.getOpcWorkRequestId(),
                      UPDATE,
                      task,
                      description.getCredentials().getLoadBalancerClient());
                }
              });
    }
    // Add new backendSets
    Map<String, BackendSetDetails> backendSets = description.getBackendSets();
    if (backendSets != null) {
      backendSets.forEach(
          (name, details) -> {
            if (!lb.getBackendSets().containsKey(name)) {
              CreateBackendSetResponse res =
                  description
                      .getCredentials()
                      .getLoadBalancerClient()
                      .createBackendSet(
                          CreateBackendSetRequest.builder()
                              .loadBalancerId(description.getLoadBalancerId())
                              .createBackendSetDetails(toCreate(details, name))
                              .build());
              task.updateStatus(
                  UPDATE,
                  "CreateBackendSetRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}");
              OracleWorkRequestPoller.poll(
                  res.getOpcWorkRequestId(),
                  UPDATE,
                  task,
                  description.getCredentials().getLoadBalancerClient());
            }
          });
    }
  }

  void updateCertificates(LoadBalancer lb, Task task) {
    if (lb.getCertificates() != null) {
      lb.getCertificates()
          .forEach(
              (name, existingCert) -> {
                CertificateDetails cert =
                    (description.getCertificates() != null)
                        ? description.getCertificates().get(name)
                        : null;
                if (cert == null) {
                  // Delete certificate: must have no listener using it
                  DeleteCertificateResponse res =
                      description
                          .getCredentials()
                          .getLoadBalancerClient()
                          .deleteCertificate(
                              DeleteCertificateRequest.builder()
                                  .loadBalancerId(lb.getId())
                                  .certificateName(name)
                                  .build());
                  task.updateStatus(
                      UPDATE,
                      "DeleteCertificateRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}");
                  OracleWorkRequestPoller.poll(
                      res.getOpcWorkRequestId(),
                      UPDATE,
                      task,
                      description.getCredentials().getLoadBalancerClient());
                }
              });
    }
    // Add new certificate
    Map<String, CertificateDetails> certificates = description.getCertificates();
    if (certificates != null) {
      certificates.forEach(
          (name, details) -> {
            Certificate cert = lb.getCertificates().get(name);
            if (cert == null) {
              CreateCertificateResponse res =
                  description
                      .getCredentials()
                      .getLoadBalancerClient()
                      .createCertificate(
                          CreateCertificateRequest.builder()
                              .loadBalancerId(description.getLoadBalancerId())
                              .createCertificateDetails(toCreate(details, name))
                              .build());
              task.updateStatus(
                  UPDATE,
                  "CreateCertificateRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}");
              OracleWorkRequestPoller.poll(
                  res.getOpcWorkRequestId(),
                  UPDATE,
                  task,
                  description.getCredentials().getLoadBalancerClient());
            }
          });
    }
  }

  void update(LoadBalancer lb, Task task) {
    task.updateStatus(UPDATE, "UpdateLoadBalancer: $lb.displayName");
    // Delete Listeners
    if (lb.getListeners() != null) {
      lb.getListeners()
          .forEach(
              (name, existingListener) -> {
                ListenerDetails listenerUpdate =
                    (description.getListeners() != null)
                        ? description.getListeners().get(name)
                        : null;
                if (listenerUpdate != null) {
                  // listener could be updated to use new backendSet so do this after updating
                  // backendSets
                } else {
                  DeleteListenerResponse res =
                      description
                          .getCredentials()
                          .getLoadBalancerClient()
                          .deleteListener(
                              DeleteListenerRequest.builder()
                                  .loadBalancerId(lb.getId())
                                  .listenerName(name)
                                  .build());
                  task.updateStatus(
                      UPDATE,
                      "DeleteListenerRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}");
                  OracleWorkRequestPoller.poll(
                      res.getOpcWorkRequestId(),
                      UPDATE,
                      task,
                      description.getCredentials().getLoadBalancerClient());
                }
              });
    }
    updateBackendSets(lb, task);
    updateCertificates(lb, task);
    // Update Listeners
    if (lb.getListeners() != null) {
      lb.getListeners()
          .forEach(
              (name, existingListener) -> {
                ListenerDetails listenerUpdate =
                    (description.getListeners() != null)
                        ? description.getListeners().get(name)
                        : null;
                if (listenerUpdate != null) {
                  UpdateListenerResponse res =
                      description
                          .getCredentials()
                          .getLoadBalancerClient()
                          .updateListener(
                              UpdateListenerRequest.builder()
                                  .loadBalancerId(lb.getId())
                                  .listenerName(name)
                                  .updateListenerDetails(toUpdate(listenerUpdate))
                                  .build());
                  task.updateStatus(
                      UPDATE,
                      "UpdateListenerRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}");
                  OracleWorkRequestPoller.poll(
                      res.getOpcWorkRequestId(),
                      UPDATE,
                      task,
                      description.getCredentials().getLoadBalancerClient());
                }
              });
    }
    // Add new Listeners
    Map<String, ListenerDetails> listeners = description.getListeners();
    if (listeners != null) {
      listeners.forEach(
          (name, listener) -> {
            if (!lb.getListeners().containsKey(name)) {
              CreateListenerResponse res =
                  description
                      .getCredentials()
                      .getLoadBalancerClient()
                      .createListener(
                          CreateListenerRequest.builder()
                              .loadBalancerId(description.getLoadBalancerId())
                              .createListenerDetails(toCreate(listener, name))
                              .build());
              task.updateStatus(
                  UPDATE,
                  "CreateListenerRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}");
              OracleWorkRequestPoller.poll(
                  res.getOpcWorkRequestId(),
                  UPDATE,
                  task,
                  description.getCredentials().getLoadBalancerClient());
            }
          });
    }
  }

  void create(Task task) {
    String clusterName = description.qualifiedName();
    task.updateStatus(CREATE, "Create LB: ${description.qualifiedName()}");
    CreateLoadBalancerDetails.Builder lbDetails =
        CreateLoadBalancerDetails.builder()
            .displayName(clusterName)
            .compartmentId(description.getCredentials().getCompartmentId())
            .shapeName(description.getShape())
            .subnetIds(description.getSubnetIds());
    if (description.getIsPrivate()) {
      lbDetails.isPrivate(description.getIsPrivate());
    }
    if (description.getCertificates() != null) {
      lbDetails.certificates(description.getCertificates());
    }
    if (description.getBackendSets() != null) {
      lbDetails.backendSets(description.getBackendSets());
    }
    if (description.getListeners() != null) {
      lbDetails.listeners(description.getListeners());
    }
    CreateLoadBalancerResponse res =
        description
            .getCredentials()
            .getLoadBalancerClient()
            .createLoadBalancer(
                CreateLoadBalancerRequest.builder()
                    .createLoadBalancerDetails(lbDetails.build())
                    .build());
    task.updateStatus(
        CREATE, "Create LB rq submitted - work request id: ${rs.getOpcWorkRequestId()}");
    OracleWorkRequestPoller.poll(
        res.getOpcWorkRequestId(),
        CREATE,
        task,
        description.getCredentials().getLoadBalancerClient());
  }

  @Override
  public Map operate(List priorOutputs) {
    Task task = getTask();
    if (description.getLoadBalancerId() != null) {
      try {
        LoadBalancer lb =
            description
                .getCredentials()
                .getLoadBalancerClient()
                .getLoadBalancer(
                    GetLoadBalancerRequest.builder()
                        .loadBalancerId(description.getLoadBalancerId())
                        .build())
                .getLoadBalancer();
        if (lb != null) {
          update(lb, task);
        } else {
          task.updateStatus(UPDATE, "LoadBalancer ${description.loadBalancerId} does not exist.");
        }
      } catch (BmcException e) {
        if (e.getStatusCode() == 404) {
          task.updateStatus(UPDATE, "LoadBalancer ${description.loadBalancerId} does not exist.");
        } else {
          throw e;
        }
      }
    } else {
      create(task);
    }
    return mapOf(
        "loadBalancers",
        mapOf(
            description.getCredentials().getRegion(), mapOf("name", description.qualifiedName())));
  }

  Map<String, Object> mapOf(String key, Object val) {
    Map<String, Object> map = new HashMap<>();
    map.put(key, val);
    return map;
  }
}
