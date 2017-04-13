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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.loadbalancer.KubernetesLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.loadbalancer.KubernetesNamedServicePort;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.*;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails.Instance;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.*;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedService;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.utils.Strings;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public interface KubernetesDistributedService<T> extends DistributedService<T, KubernetesAccount> {
  String getDockerRegistry();
  ArtifactService getArtifactService();
  ServiceInterfaceFactory getServiceInterfaceFactory();
  ObjectMapper getObjectMapper();

  default JobExecutor getJobExecutor() {
    return DaemonTaskHandler.getTask().getJobExecutor();
  }

  default String getNamespace() {
    return "spinnaker";
  }

  default String buildAddress() {
    return Strings.join(".", getName(), getNamespace());
  }

  default String getRegion() {
    return getNamespace();
  }

  default String getArtifactId(String deploymentName) {
    String artifactName = getArtifact().getName();
    String version = getArtifactService().getArtifactVersion(deploymentName, getArtifact());

    KubernetesImageDescription image = new KubernetesImageDescription(artifactName, version, getDockerRegistry());
    return KubernetesUtil.getImageId(image);
  }

  default Provider.ProviderType getProviderType() {
    return Provider.ProviderType.KUBERNETES;
  }

  default List<String> getHealthProviders() {
    List<String> healthProviders = new ArrayList<>();
    healthProviders.add("KubernetesContainer");
    healthProviders.add("KubernetesPod");
    return healthProviders;
  }

  default Map<String, List<String>> getAvailabilityZones() {
    String namespace = getNamespace();
    List<String> zones = new ArrayList<>();
    zones.add(namespace);
    Map<String, List<String>> availabilityZones = new HashMap<>();
    availabilityZones.put(namespace, zones);
    return availabilityZones;
  }

  @Override
  default Map<String, Object> buildRollbackPipeline(AccountDeploymentDetails<KubernetesAccount> details, ServiceSettings settings) {
    Map<String, Object> pipeline = DistributedService.super.buildRollbackPipeline(details, settings);

    List<Map<String, Object>> stages = (List<Map<String, Object>>) pipeline.get("stages");
    assert(stages != null && !stages.isEmpty());

    for (Map<String, Object> stage : stages) {
      stage.put("namespaces", Collections.singletonList(getNamespace()));
      stage.put("interestingHealthProviderNames", Collections.singletonList("KubernetesService"));
      stage.remove("region");
    }

    return pipeline;
  }

  default Map<String, Object> getLoadBalancerDescription(AccountDeploymentDetails<KubernetesAccount> details, SpinnakerRuntimeSettings runtimeSettings) {
    ServiceSettings settings = runtimeSettings.getServiceSettings(getService());
    int port = settings.getPort();
    String accountName = details.getAccount().getName();

    KubernetesLoadBalancerDescription description = new KubernetesLoadBalancerDescription();

    String namespace = getNamespace();
    String name = getName();
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

    return getObjectMapper().convertValue(description, new TypeReference<Map<String, Object>>() { });
  }

  default List<ConfigSource> stageProfiles(
      AccountDeploymentDetails<KubernetesAccount> details,
      GenerateService.ResolvedConfiguration resolvedConfiguration) {
    KubernetesProviderUtils.createNamespace(details, getNamespace());
    Integer version = getLatestEnabledServiceVersion(details);
    if (version == null) {
      version = 0;
    } else {
      version++;
    }

    SpinnakerService thisService = getService();
    ServiceSettings thisServiceSettings = resolvedConfiguration.getServiceSettings(thisService);
    SpinnakerMonitoringDaemonService monitoringService = getMonitoringDaemonService();
    String name = getName();
    Map<String, String> env = new HashMap<>();
    List<ConfigSource> configSources = new ArrayList<>();
    ServiceSettings monitoringSettings = resolvedConfiguration.getServiceSettings(monitoringService);
    if (thisServiceSettings.isMonitored() && monitoringSettings.isEnabled()) {
      Map<String, Profile> monitoringProfiles = resolvedConfiguration.getProfilesForService(monitoringService.getType());

      String profileName = SpinnakerMonitoringDaemonService.serviceRegistryProfileName(thisService.getCanonicalName());
      Profile profile = monitoringProfiles.get(profileName);

      assert(profile != null);

      String secretName = KubernetesProviderUtils.componentRegistry(name, version);
      String mountPoint = Paths.get(profile.getOutputFile()).getParent().toString();
      env.clear();
      env.putAll(profile.getEnv());

      KubernetesProviderUtils.upsertSecret(details,
          Collections.singleton(profile.getStagedFile(getSpinnakerStagingPath())),
          secretName,
          getNamespace());

      configSources.add(new ConfigSource()
          .setId(secretName)
          .setMountPath(mountPoint)
          .setEnv(env)
      );

      profile = monitoringProfiles.get(SpinnakerMonitoringDaemonService.monitoringProfileName());
      assert(profile != null);

      secretName = KubernetesProviderUtils.componentMonitoring(name, version);
      mountPoint = Paths.get(profile.getOutputFile()).getParent().toString();
      env.clear();
      env.putAll(profile.getEnv());

      KubernetesProviderUtils.upsertSecret(details,
          Collections.singleton(profile.getStagedFile(getSpinnakerStagingPath())),
          secretName,
          getNamespace());

      configSources.add(new ConfigSource()
          .setId(secretName)
          .setMountPath(mountPoint)
          .setEnv(env)
      );
    }

    Map<String, Profile> serviceProfiles = resolvedConfiguration.getProfilesForService(thisService.getType());
    Map<String, Set<Profile>> collapseByDirectory = new HashMap<>();
    Set<String> requiredFiles = new HashSet<>();

    for (Map.Entry<String, Profile> entry : serviceProfiles.entrySet()) {
      Profile profile = entry.getValue();
      String mountPoint = Paths.get(profile.getOutputFile()).getParent().toString();
      Set<Profile> profiles = collapseByDirectory.getOrDefault(mountPoint, new HashSet<>());
      profiles.add(profile);
      requiredFiles.addAll(profile.getRequiredFiles());
      collapseByDirectory.put(mountPoint, profiles);
    }

    if (!requiredFiles.isEmpty()) {
      String secretName = KubernetesProviderUtils.componentDependencies(name, version);
      String mountPoint = null;
      for (String file : requiredFiles) {
        String nextMountPoint = Paths.get(file).getParent().toString();
        if (mountPoint == null) {
          mountPoint = nextMountPoint;
        }
        assert(mountPoint.equals(nextMountPoint));
      }

      KubernetesProviderUtils.upsertSecret(details, requiredFiles, secretName, getNamespace());
      configSources.add(new ConfigSource().setId(secretName).setMountPath(mountPoint));
    }

    int ind = 0;
    for (Map.Entry<String, Set<Profile>> entry : collapseByDirectory.entrySet()) {
      env.clear();
      String mountPoint = entry.getKey();
      Set<Profile> profiles = entry.getValue();
      env.putAll(profiles.stream().reduce(new HashMap<>(),
          (acc, profile) -> {
            acc.putAll(profile.getEnv());
            return acc;
          },
          (a, b) -> {
            a.putAll(b);
            return a;
          }
      ));

      Set<String> files = profiles
          .stream()
          .map(p -> p.getStagedFile(getSpinnakerStagingPath()))
          .collect(Collectors.toSet());

      String secretName = KubernetesProviderUtils.componentSecret(name + ind, version);
      ind += 1;

      KubernetesProviderUtils.upsertSecret(details, files, secretName, getNamespace());
      configSources.add(new ConfigSource()
          .setId(secretName)
          .setMountPath(mountPoint)
          .setEnv(env)
      );
    }

    return configSources;
  }

  default Map<String, Object> getServerGroupDescription(
      AccountDeploymentDetails<KubernetesAccount> details,
      SpinnakerRuntimeSettings runtimeSettings,
      List<ConfigSource> configSources) {
    DeployKubernetesAtomicOperationDescription description = new DeployKubernetesAtomicOperationDescription();
    SpinnakerMonitoringDaemonService monitoringService = getMonitoringDaemonService();
    DeploymentEnvironment.Size size = details
        .getDeploymentConfiguration()
        .getDeploymentEnvironment()
        .getSize();

    String accountName = details.getAccount().getName();
    String namespace = getNamespace();
    String name = getName();
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

    List<KubernetesContainerDescription> containers = new ArrayList<>();
    ServiceSettings serviceSettings = runtimeSettings.getServiceSettings(getService());
    KubernetesContainerDescription container = buildContainer(name, serviceSettings, configSources, size);
    containers.add(container);

    ServiceSettings monitoringSettings = runtimeSettings.getServiceSettings(monitoringService);
    if (monitoringSettings.isEnabled() && serviceSettings.isMonitored()) {
      serviceSettings = runtimeSettings.getServiceSettings(monitoringService);
      container = buildContainer(monitoringService.getName(), serviceSettings, configSources, size);
      containers.add(container);
    }

    description.setContainers(containers);

    return getObjectMapper().convertValue(description, new TypeReference<Map<String, Object>>() { });
  }

  default KubernetesContainerDescription buildContainer(String name, ServiceSettings settings, List<ConfigSource> configSources, DeploymentEnvironment.Size size) {
    KubernetesContainerDescription container = new KubernetesContainerDescription();
    KubernetesProbe readinessProbe = new KubernetesProbe();
    KubernetesHandler handler = new KubernetesHandler();
    int port = settings.getPort();

    String healthEndpoint = settings.getHealthEndpoint();
    if (healthEndpoint != null) {
      handler.setType(KubernetesHandlerType.HTTP);
      KubernetesHttpGetAction action = new KubernetesHttpGetAction();
      action.setPath(healthEndpoint);
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

    /* TODO(lwander) this needs work
    SizingTranslation.ServiceSize serviceSize = sizingTranslation.getServiceSize(size, service);
    KubernetesResourceDescription resources = new KubernetesResourceDescription();
    resources.setCpu(serviceSize.getCpu());
    resources.setMemory(serviceSize.getRam());
    container.setRequests(resources);
    container.setLimits(resources);
    */

    KubernetesImageDescription imageDescription = KubernetesUtil.buildImageDescription(settings.getArtifactId());
    container.setImageDescription(imageDescription);
    container.setName(name);

    List<KubernetesContainerPort> ports = new ArrayList<>();
    KubernetesContainerPort containerPort = new KubernetesContainerPort();
    containerPort.setContainerPort(port);
    ports.add(containerPort);
    container.setPorts(ports);

    List<KubernetesVolumeMount> volumeMounts = new ArrayList<>();
    for (ConfigSource configSource : configSources) {
      KubernetesVolumeMount volumeMount = new KubernetesVolumeMount();
      volumeMount.setName(configSource.getId());
      volumeMount.setMountPath(configSource.getMountPath());
      volumeMounts.add(volumeMount);
    }

    container.setVolumeMounts(volumeMounts);

    List<KubernetesEnvVar> envVars = new ArrayList<>();
    settings.getEnv().forEach((k, v) -> {
      KubernetesEnvVar envVar = new KubernetesEnvVar();
      envVar.setName(k);
      envVar.setValue(v);

      envVars.add(envVar);
    });

    configSources.forEach(c -> {
      c.getEnv().entrySet().forEach(envEntry -> {
        KubernetesEnvVar envVar = new KubernetesEnvVar();
        envVar.setName(envEntry.getKey());
        envVar.setValue(envEntry.getValue());
        envVars.add(envVar);
      });
    });


    container.setEnvVars(envVars);

    return container;
  }

  default void ensureRunning(
      AccountDeploymentDetails<KubernetesAccount> details,
      GenerateService.ResolvedConfiguration resolvedConfiguration,
      List<ConfigSource> configSources,
      boolean recreate) {
    ServiceSettings settings = resolvedConfiguration.getServiceSettings(getService());
    String namespace = getNamespace();
    String serviceName = getName();
    String replicaSetName = serviceName + "-v000";
    int port = settings.getPort();
    DeploymentEnvironment.Size size = details
        .getDeploymentConfiguration()
        .getDeploymentEnvironment()
        .getSize();

    KubernetesClient client = KubernetesProviderUtils.getClient(details);
    KubernetesProviderUtils.createNamespace(details, namespace);

    Map<String, String> serviceSelector = new HashMap<>();
    serviceSelector.put("load-balancer-" + serviceName, "true");

    Map<String, String> replicaSetSelector = new HashMap<>();
    replicaSetSelector.put("server-group", replicaSetName);

    Map<String, String> podLabels = new HashMap<>();
    podLabels.putAll(replicaSetSelector);
    podLabels.putAll(serviceSelector);

    ServiceBuilder serviceBuilder = new ServiceBuilder();
    serviceBuilder = serviceBuilder
        .withNewMetadata()
        .withName(serviceName)
        .withNamespace(namespace)
        .endMetadata()
        .withNewSpec()
        .withSelector(serviceSelector)
        .withPorts(new ServicePortBuilder().withPort(port).build())
        .endSpec();

    boolean create = true;
    if (client.services().inNamespace(namespace).withName(serviceName).get() != null) {
      if (recreate) {
        client.services().inNamespace(namespace).withName(serviceName).delete();
      } else {
        create = false;
      }
    }

    if (create) {
      client.services().inNamespace(namespace).create(serviceBuilder.build());
    }

    List<EnvVar> envVars = settings.getEnv().entrySet().stream().map(e -> {
      EnvVarBuilder envVarBuilder = new EnvVarBuilder();
      return envVarBuilder.withName(e.getKey()).withValue(e.getValue()).build();
    }).collect(Collectors.toList());

    configSources.forEach(c -> {
      c.getEnv().entrySet().forEach(envEntry -> {
        EnvVarBuilder envVarBuilder = new EnvVarBuilder();
        envVars.add(envVarBuilder.withName(envEntry.getKey())
            .withValue(envEntry.getValue())
            .build());
      });
    });

    ProbeBuilder probeBuilder = new ProbeBuilder();

    if (settings.getHealthEndpoint() != null) {
      probeBuilder = probeBuilder
          .withNewHttpGet()
          .withNewPort(port)
          .withPath(settings.getHealthEndpoint())
          .endHttpGet();
    } else {
      probeBuilder = probeBuilder
          .withNewTcpSocket()
          .withNewPort()
          .withIntVal(port)
          .endPort()
          .endTcpSocket();
    }

    /* TODO(lwander) this needs work
    DeploymentEnvironment.Size size = details.getDeploymentConfiguration().getDeploymentEnvironment().getSize();
    SizingTranslation.ServiceSize serviceSize = sizingTranslation.getServiceSize(size, service);
    Map<String, Quantity> resources = new HashMap<>();
    resources.put("cpu", new Quantity(serviceSize.getCpu()));
    resources.put("memory", new Quantity(serviceSize.getRam()));
    */

    List<VolumeMount> volumeMounts = configSources.stream().map(c -> {
      return new VolumeMountBuilder().withMountPath(c.getMountPath()).withName(c.getId()).build();
    }).collect(Collectors.toList());
    ContainerBuilder containerBuilder = new ContainerBuilder();

    containerBuilder = containerBuilder
        .withName(serviceName)
        .withImage(settings.getArtifactId())
        .withPorts(new ContainerPortBuilder().withContainerPort(port).build())
        .withVolumeMounts(volumeMounts)
        .withEnv(envVars)
        .withReadinessProbe(probeBuilder.build());
    //  .withNewResources()
    //  .withLimits(resources)
    //  .withRequests(resources)
    //  .endResources();

    List<Volume> volumes = configSources.stream().map(c -> {
      return new VolumeBuilder()
          .withName(c.getId())
          .withSecret(
              new SecretVolumeSourceBuilder()
                  .withSecretName(c.getId())
                  .build())
          .build();
    }).collect(Collectors.toList());
    ReplicaSetBuilder replicaSetBuilder = new ReplicaSetBuilder();

    replicaSetBuilder = replicaSetBuilder
        .withNewMetadata()
        .withName(replicaSetName)
        .withNamespace(namespace)
        .endMetadata()
        .withNewSpec()
        .withReplicas(1)
        .withNewSelector()
        .withMatchLabels(replicaSetSelector)
        .endSelector()
        .withNewTemplate()
        .withNewMetadata()
        .withLabels(podLabels)
        .endMetadata()
        .withNewSpec()
        .withContainers(containerBuilder.build())
        .withTerminationGracePeriodSeconds(5L)
        .withVolumes(volumes)
        .endSpec()
        .endTemplate()
        .endSpec();

    create = true;
    if (client.extensions().replicaSets().inNamespace(namespace).withName(replicaSetName).get() != null) {
      if (recreate) {
        client.extensions().replicaSets().inNamespace(namespace).withName(replicaSetName).delete();

        RunningServiceDetails runningServiceDetails = getRunningServiceDetails(details);
        while (runningServiceDetails.getHealthy() > 0) {
          try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(5));
          } catch (InterruptedException ignored) {
          }
          runningServiceDetails = getRunningServiceDetails(details);
        }
      } else {
        create = false;
      }
    }

    if (create) {
      client.extensions().replicaSets().inNamespace(namespace).create(replicaSetBuilder.build());
    }

    RunningServiceDetails runningServiceDetails = getRunningServiceDetails(details);
    while (runningServiceDetails.getHealthy() == 0) {
      try {
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
      } catch (InterruptedException ignored) {
      }
      runningServiceDetails = getRunningServiceDetails(details);
    }
  }

  default RunningServiceDetails getRunningServiceDetails(AccountDeploymentDetails<KubernetesAccount> details) {
    RunningServiceDetails res = new RunningServiceDetails();

    KubernetesClient client = KubernetesProviderUtils.getClient(details);
    String name = getName();
    String namespace = getNamespace();

    RunningServiceDetails.LoadBalancer lb = new RunningServiceDetails.LoadBalancer();
    lb.setExists(client.services().inNamespace(namespace).withName(name).get() != null);
    res.setLoadBalancer(lb);

    List<Pod> pods = client.pods().inNamespace(namespace).withLabel("load-balancer-" + name, "true").list().getItems();

    Map<Integer, List<Instance>> instances = res.getInstances();
    for (Pod pod : pods) {
      String podName = pod.getMetadata().getName();
      String serverGroupName = podName.substring(0, podName.lastIndexOf("-"));
      Names parsedName = Names.parseName(serverGroupName);
      Integer version = parsedName.getSequence();
      if (version == null) {
        throw new IllegalStateException("Server group for service " + getName() + " has unknown sequence (" + serverGroupName + ")");
      }

      String location = pod.getMetadata().getNamespace();
      String id = pod.getMetadata().getName();

      Instance instance = new Instance().setId(id).setLocation(location);
      List<Instance> knownInstances = instances.getOrDefault(version, new ArrayList<>());
      knownInstances.add(instance);
      instances.put(version, knownInstances);
    }

    int count = (int) pods
        .stream()
        .filter(p -> p
            .getStatus()
            .getContainerStatuses()
            .stream()
            .allMatch(c -> c.getReady() && c.getState().getRunning() != null && c.getState().getTerminated() == null))
        .count();

    res.setHealthy(count);

    return res;
  }

  default T connect(AccountDeploymentDetails<KubernetesAccount> details, SpinnakerRuntimeSettings runtimeSettings) {
    ServiceSettings settings = runtimeSettings.getServiceSettings(getService());

    KubernetesProviderUtils.Proxy proxy = KubernetesProviderUtils.openProxy(getJobExecutor(), details);
    String endpoint = KubernetesProviderUtils.proxyServiceEndpoint(proxy, getNamespace(), getName(), settings.getPort()).toString();

    return getServiceInterfaceFactory().createService(endpoint, getService());
  }

  default String connectCommand(AccountDeploymentDetails<KubernetesAccount> details, SpinnakerRuntimeSettings runtimeSettings) {
    ServiceSettings settings = runtimeSettings.getServiceSettings(getService());
    RunningServiceDetails runningServiceDetails = getRunningServiceDetails(details);
    Map<Integer, List<Instance>> instances = runningServiceDetails.getInstances();
    Integer latest = getLatestEnabledServiceVersion(details);

    List<Instance> latestInstances = instances.get(latest);
    if (latestInstances.isEmpty()) {
      throw new HalException(Problem.Severity.FATAL, "No instances running in latest server group for service " + getName() + " in namespace " + getNamespace());
    }

    return Strings.join(KubernetesProviderUtils.kubectlPortForwardCommand(details,
        getNamespace(),
        latestInstances.get(0).getId(),
        settings.getPort()), " ");
  }

  default void deleteVersion(AccountDeploymentDetails<KubernetesAccount> details, Integer version) {
    String name = String.format("%s-v%03d", getName(), version);
    String namespace = getNamespace();
    KubernetesProviderUtils.deleteReplicaSet(details, namespace, name);
  }
}
