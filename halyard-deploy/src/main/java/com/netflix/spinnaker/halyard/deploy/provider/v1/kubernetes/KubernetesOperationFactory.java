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
 *
 */

package com.netflix.spinnaker.halyard.deploy.provider.v1.kubernetes;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.loadbalancer.KubernetesLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.loadbalancer.KubernetesNamedServicePort;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.*;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.deploy.provider.v1.OperationFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.halyard.config.model.v1.node.Provider.ProviderType.KUBERNETES;

@Component
public class KubernetesOperationFactory extends OperationFactory {
  @Autowired
  ObjectMapper objectMapper;

  private KubernetesLoadBalancerDescription baseLoadBalancerDescription(String accountName, SpinnakerService service) {
    String address = service.getAddress();
    int port = service.getPort();
    KubernetesLoadBalancerDescription description = new KubernetesLoadBalancerDescription();

    String namespace = KubernetesProviderInterface.getNamespaceFromAddress(address);
    String name = KubernetesProviderInterface.getServiceFromAddress(address);
    Names parsedName = Names.parseName(name);
    description.setApp(parsedName.getApp());
    description.setStack(parsedName.getStack());
    description.setDetail(parsedName.getDetail());

    description.setName(name);
    description.setNamespace(namespace);
    description.setAccount(accountName);

    KubernetesNamedServicePort servicePort = new KubernetesNamedServicePort();
    servicePort.setPort(port);
    servicePort.setTargetPort(port);
    servicePort.setName("http");
    servicePort.setProtocol("TCP");

    List<KubernetesNamedServicePort> servicePorts = new ArrayList<>();
    servicePorts.add(servicePort);
    description.setPorts(servicePorts);

    return description;
  }

  private DeployKubernetesAtomicOperationDescription baseDeployDescription(String accountName, SpinnakerService service, String artifact, List<ConfigSource> configSources) {
    String address = service.getAddress();
    int port = service.getPort();
    DeployKubernetesAtomicOperationDescription description = new DeployKubernetesAtomicOperationDescription();

    String namespace = KubernetesProviderInterface.getNamespaceFromAddress(address);
    String name = KubernetesProviderInterface.getServiceFromAddress(address);
    String artifactName = service.getArtifact().getName();
    Names parsedName = Names.parseName(name);

    description.setNamespace(namespace);
    description.setAccount(accountName);

    description.setApplication(parsedName.getApp());
    description.setStack(parsedName.getStack());
    description.setFreeFormDetails(parsedName.getDetail());
    description.setTargetSize(1);

    List<KubernetesVolumeSource> volumeSources = new ArrayList<>();
    for (ConfigSource configSource : configSources) {
      KubernetesVolumeSource volumeSource = new KubernetesVolumeSource();
      volumeSource.setName(configSource.getId());
      volumeSource.setType(KubernetesVolumeSourceType.Secret);
      KubernetesSecretVolumeSource secretVolumeSource = new KubernetesSecretVolumeSource();
      secretVolumeSource.setSecretName(configSource.getId());
      volumeSource.setSecret(secretVolumeSource);
      volumeSources.add(volumeSource);
    }

    description.setVolumeSources(volumeSources);

    List<String> loadBalancers = new ArrayList<>();
    loadBalancers.add(name);
    description.setLoadBalancers(loadBalancers);

    KubernetesContainerDescription container = new KubernetesContainerDescription();
    KubernetesProbe readinessProbe = new KubernetesProbe();
    KubernetesHandler handler = new KubernetesHandler();

    String httpHealth = service.getHttpHealth();
    if (httpHealth != null) {
      handler.setType(KubernetesHandlerType.HTTP);
      KubernetesHttpGetAction action = new KubernetesHttpGetAction();
      action.setPath(httpHealth);
      action.setPort(port);
      handler.setHttpGetAction(action);
    } else {
      handler.setType(KubernetesHandlerType.TCP);
      KubernetesTcpSocketAction action = new KubernetesTcpSocketAction();
      action.setPort(port);
      handler.setTcpSocketAction(action);
    }

    readinessProbe.setHandler(handler);
    container.setReadinessProbe(readinessProbe);

    KubernetesImageDescription imageDescription = KubernetesUtil.buildImageDescription(artifact);
    container.setImageDescription(imageDescription);
    container.setName(artifactName);

    List<KubernetesContainerPort> ports = new ArrayList<>();
    KubernetesContainerPort containerPort = new KubernetesContainerPort();
    containerPort.setContainerPort(port);
    ports.add(containerPort);
    container.setPorts(ports);

    List<KubernetesVolumeMount> volumeMounts = new ArrayList<>();
    for (ConfigSource configSource : configSources) {
      KubernetesVolumeMount volumeMount = new KubernetesVolumeMount();
      volumeMount.setName(configSource.getId());
      volumeMount.setMountPath(configSource.getMountPoint());
      volumeMounts.add(volumeMount);
    }

    container.setVolumeMounts(volumeMounts);

    List<KubernetesEnvVar> envVars = new ArrayList<>();
    if (!service.getProfiles().isEmpty()) {
      KubernetesEnvVar envVar = new KubernetesEnvVar();
      envVar.setName(artifactName.toUpperCase() + "_OPTS");
      envVar.setValue("-Dspring.profiles.active=" + service.getProfiles().stream().reduce((a, b) -> a + "," + b).get());

      envVars.add(envVar);
    }

    service.getEnv().forEach((k, v) -> {
      KubernetesEnvVar envVar = new KubernetesEnvVar();
      envVar.setName((String) k);
      envVar.setValue((String) v);

      envVars.add(envVar);
    });

    container.setEnvVars(envVars);

    List<KubernetesContainerDescription> containers = new ArrayList<>();
    containers.add(container);
    description.setContainers(containers);

    return description;
  }

  private List<String> healthProviders() {
    List<String> healthProviders = new ArrayList<>();
    healthProviders.add("KubernetesContainer");
    healthProviders.add("KubernetesPod");
    return healthProviders;
  }

  private Map<String, List<String>> availabilityZones(String namespace) {
    List<String> zones = new ArrayList<>();
    zones.add(namespace);
    Map<String, List<String>> availabilityZones = new HashMap<>();
    availabilityZones.put(namespace, zones);
    return availabilityZones;
  }

  @Override
  public Map<String, Object> createUpsertPipeline(String accountName, SpinnakerService service) {
    String namespace = KubernetesProviderInterface.getNamespaceFromAddress(service.getAddress());
    List<Map<String, Object>> stages = new ArrayList<>();
    Map<String, Object> upsert = objectMapper.convertValue(baseLoadBalancerDescription(accountName, service), Map.class);
    stages.add(upsertTask(upsert, availabilityZones(namespace)));
    return buildTask("Upsert load balancer for " + service.getArtifact().getName(), stages);
  }

  @Override
  public Map<String, Object> createDeployPipeline(String accountName, SpinnakerService service, String artifact, List<ConfigSource> configSources, boolean update) {
    List<Map<String, Object>> stages = new ArrayList<>();
    Map<String, Object> deploy = objectMapper.convertValue(baseDeployDescription(accountName, service, artifact, configSources), Map.class);
    String namespace = KubernetesProviderInterface.getNamespaceFromAddress(service.getAddress());
    if (update) {
      deploy = redBlackStage(deploy, healthProviders(), namespace);
    } else {
      deploy = deployStage(deploy, healthProviders(), namespace);
    }
    stages.add(deploy);
    return buildPipeline("Deploy server group for " + service.getArtifact().getName(), stages);
  }


  @Override
  protected Provider.ProviderType getProviderType() {
    return KUBERNETES;
  }
}
