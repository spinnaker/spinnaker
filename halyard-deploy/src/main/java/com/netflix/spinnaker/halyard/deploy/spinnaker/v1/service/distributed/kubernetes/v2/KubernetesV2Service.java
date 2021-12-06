/*
 * Copyright 2018 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.model.v1.node.AffinityConfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.CustomSizing;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.config.model.v1.node.SidecarConfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Toleration;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.resource.v1.JinjaJarResource;
import com.netflix.spinnaker.halyard.core.resource.v1.TemplatedResource;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ConfigSource;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.HasServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.KubernetesSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerMonitoringDaemonService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedService.DeployPriority;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.SidecarService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.KubernetesService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.KubernetesSharedServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2.KubernetesV2Utils.SecretMountPair;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

public interface KubernetesV2Service<T> extends HasServiceSettings<T>, KubernetesService {
  String getServiceName();

  String getSpinnakerStagingPath(String deploymentName);

  String getSpinnakerStagingDependenciesPath(String deploymentName);

  ServiceSettings defaultServiceSettings(DeploymentConfiguration deploymentConfiguration);

  ObjectMapper getObjectMapper();

  SpinnakerMonitoringDaemonService getMonitoringDaemonService();

  DeployPriority getDeployPriority();

  default boolean runsOnJvm() {
    return true;
  }

  default int terminationGracePeriodSeconds() {
    return 60;
  }

  default String shutdownScriptFile() {
    return "/opt/spinnaker/scripts/shutdown.sh";
  }

  default boolean isEnabled(DeploymentConfiguration deploymentConfiguration) {
    return true;
  }

  default List<String> getReadinessExecCommand(ServiceSettings settings) {
    List<String> execCommandList = settings.getKubernetes().getCustomHealthCheckExecCommands();
    if (execCommandList == null || execCommandList.isEmpty()) {
      execCommandList =
          Arrays.asList(
              "wget",
              "--no-check-certificate",
              "--spider",
              "-q",
              settings.getScheme()
                  + "://localhost:"
                  + settings.getPort()
                  + settings.getHealthEndpoint());
    }
    return execCommandList;
  }

  default boolean hasPreStopCommand() {
    return false;
  }

  default List<String> getPreStopCommand(ServiceSettings settings) {
    return hasPreStopCommand()
        ? Arrays.asList("bash", shutdownScriptFile())
        : Collections.EMPTY_LIST;
  }

  default String getRootHomeDirectory() {
    return "/root";
  }

  default String getHomeDirectory() {
    return "/home/spinnaker";
  }

  default String getNamespaceYaml(GenerateService.ResolvedConfiguration resolvedConfiguration) {
    ServiceSettings settings = resolvedConfiguration.getServiceSettings(getService());
    String name = getNamespace(settings);
    TemplatedResource namespace = new JinjaJarResource("/kubernetes/manifests/namespace.yml");
    namespace.addBinding("name", name);
    return namespace.toString();
  }

  default String getServiceYaml(GenerateService.ResolvedConfiguration resolvedConfiguration) {
    ServiceSettings settings = resolvedConfiguration.getServiceSettings(getService());
    String namespace = getNamespace(settings);
    TemplatedResource service = new JinjaJarResource("/kubernetes/manifests/service.yml");
    service.addBinding("name", getService().getCanonicalName());
    service.addBinding("namespace", namespace);
    service.addBinding("port", settings.getPort());
    service.addBinding("type", settings.getKubernetes().getServiceType());
    service.addBinding("nodePort", settings.getKubernetes().getNodePort());
    service.addBinding("serviceLabels", settings.getKubernetes().getServiceLabels());
    service.addBinding("serviceAnnotations", settings.getKubernetes().getServiceAnnotations());

    return service.toString();
  }

  default String getVolumeYaml(ConfigSource configSource) {
    TemplatedResource volume;
    switch (configSource.getType()) {
      case secret:
        volume = new JinjaJarResource("/kubernetes/manifests/secretVolume.yml");
        break;
      case emptyDir:
        volume = new JinjaJarResource("/kubernetes/manifests/emptyDirVolume.yml");
        break;
      case configMap:
        volume = new JinjaJarResource("/kubernetes/manifests/configMapVolume.yml");
        break;
      case persistentVolumeClaim:
        volume = new JinjaJarResource("/kubernetes/manifests/persistentVolumeClaimVolume.yml");
        break;
      default:
        throw new IllegalStateException("Unknown volume type: " + configSource.getType());
    }

    volume.addBinding("name", configSource.getId());
    return volume.toString();
  }

  default String getResourceYaml(
      KubernetesV2Executor executor,
      AccountDeploymentDetails<KubernetesAccount> details,
      GenerateService.ResolvedConfiguration resolvedConfiguration) {
    ServiceSettings settings = resolvedConfiguration.getServiceSettings(getService());

    Integer targetSize = settings.getTargetSize();
    CustomSizing customSizing =
        details.getDeploymentConfiguration().getDeploymentEnvironment().getCustomSizing();
    if (customSizing != null) {
      Map componentSizing = customSizing.getOrDefault(getService().getServiceName(), new HashMap());
      targetSize = (Integer) componentSizing.getOrDefault("replicas", targetSize);
    }

    String version = makeValidLabel(details.getDeploymentConfiguration().getVersion());
    if (version.isEmpty()) {
      version = "unknown";
    }

    return new JinjaJarResource("/kubernetes/manifests/deployment.yml")
        .addBinding("name", getService().getCanonicalName())
        .addBinding("namespace", getNamespace(settings))
        .addBinding("replicas", targetSize)
        .addBinding("version", version)
        .addBinding("podAnnotations", settings.getKubernetes().getPodAnnotations())
        .addBinding("podSpec", getPodSpecYaml(executor, details, resolvedConfiguration))
        .addBinding("podLabels", settings.getKubernetes().getPodLabels())
        .addBinding("deploymentStrategy", settings.getKubernetes().getDeploymentStrategy())
        .toString();
  }

  default String getPodSpecYaml(
      KubernetesV2Executor executor,
      AccountDeploymentDetails<KubernetesAccount> details,
      GenerateService.ResolvedConfiguration resolvedConfiguration) {
    SpinnakerRuntimeSettings runtimeSettings = resolvedConfiguration.getRuntimeSettings();
    ServiceSettings settings = resolvedConfiguration.getServiceSettings(getService());

    List<ConfigSource> configSources = stageConfig(executor, details, resolvedConfiguration);
    List<SidecarConfig> sidecarConfigs = getSidecarConfigs(details);

    configSources.addAll(
        sidecarConfigs.stream()
            .filter(c -> StringUtils.isNotEmpty(c.getMountPath()))
            .map(
                c ->
                    new ConfigSource()
                        .setMountPath(c.getMountPath())
                        .setId(c.getName())
                        .setType(ConfigSource.Type.emptyDir))
            .collect(Collectors.toList()));

    Map<String, String> env =
        configSources.stream()
            .map(ConfigSource::getEnv)
            .map(Map::entrySet)
            .flatMap(Collection::stream)
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

    env.putAll(settings.getEnv());

    String primaryContainer =
        buildContainer(getService().getCanonicalName(), details, settings, configSources, env);
    List<String> sidecarContainers =
        getSidecars(runtimeSettings).stream()
            .map(SidecarService::getService)
            .map(
                s ->
                    buildContainer(
                        s.getCanonicalName(),
                        details,
                        runtimeSettings.getServiceSettings(s),
                        configSources,
                        env))
            .collect(Collectors.toList());

    sidecarContainers.addAll(
        sidecarConfigs.stream().map(this::buildCustomSidecar).collect(Collectors.toList()));

    List<String> containers = new ArrayList<>();
    containers.add(primaryContainer);
    containers.addAll(sidecarContainers);

    return new JinjaJarResource("/kubernetes/manifests/podSpec.yml")
        .addBinding("containers", containers)
        .addBinding("initContainers", getInitContainers(details))
        .addBinding("hostAliases", getHostAliases(details))
        .addBinding("imagePullSecrets", settings.getKubernetes().getImagePullSecrets())
        .addBinding("serviceAccountName", settings.getKubernetes().getServiceAccountName())
        .addBinding("terminationGracePeriodSeconds", terminationGracePeriodSeconds())
        .addBinding("nodeSelector", settings.getKubernetes().getNodeSelector())
        .addBinding("affinity", getAffinity(details))
        .addBinding("tolerations", getTolerations(details))
        .addBinding(
            "volumes", combineVolumes(configSources, settings.getKubernetes(), sidecarConfigs))
        .addBinding("securityContext", settings.getKubernetes().getSecurityContext())
        .toString();
  }

  default boolean characterAlphanumeric(String value, int index) {
    return Character.isAlphabetic(value.charAt(index)) || Character.isDigit(value.charAt(index));
  }

  default String makeValidLabel(String value) {
    value = value.replaceAll("[^A-Za-z0-9-_.]", "");
    while (!value.isEmpty() && !characterAlphanumeric(value, 0)) {
      value = value.substring(1);
    }

    while (!value.isEmpty() && !characterAlphanumeric(value, value.length() - 1)) {
      value = value.substring(0, value.length() - 1);
    }

    return value;
  }

  default String buildCustomSidecar(SidecarConfig config) {
    List<String> volumeMounts = new ArrayList<>();
    if (StringUtils.isNotEmpty(config.getMountPath())) {
      TemplatedResource volume = new JinjaJarResource("/kubernetes/manifests/volumeMount.yml");
      volume.addBinding("name", config.getName());
      volume.addBinding("mountPath", config.getMountPath());
      volumeMounts.add(volume.toString());
    }

    volumeMounts.addAll(
        config.getConfigMapVolumeMounts().stream()
            .map(
                c -> {
                  TemplatedResource volume =
                      new JinjaJarResource("/kubernetes/manifests/volumeMount.yml");
                  volume.addBinding("name", c.getConfigMapName());
                  volume.addBinding("mountPath", c.getMountPath());
                  return volume.toString();
                })
            .collect(Collectors.toList()));

    volumeMounts.addAll(
        config.getSecretVolumeMounts().stream()
            .map(
                c -> {
                  TemplatedResource volume =
                      new JinjaJarResource("/kubernetes/manifests/volumeMount.yml");
                  volume.addBinding("name", c.getSecretName());
                  volume.addBinding("mountPath", c.getMountPath());
                  return volume.toString();
                })
            .collect(Collectors.toList()));

    TemplatedResource container = new JinjaJarResource("/kubernetes/manifests/container.yml");
    if (config.getSecurityContext() != null) {
      TemplatedResource securityContext =
          new JinjaJarResource("/kubernetes/manifests/securityContext.yml");
      securityContext.addBinding("privileged", config.getSecurityContext().isPrivileged());
      container.addBinding("securityContext", securityContext.toString());
    }

    if (config.getPort() != null) {
      TemplatedResource containerPort = new JinjaJarResource("/kubernetes/manifests/port.yml");
      containerPort.addBinding("port", config.getPort());
      container.addBinding("port", containerPort.toString());
    } else {
      container.addBinding("port", null);
    }

    container.addBinding("name", config.getName());
    container.addBinding("imageId", config.getDockerImage());
    container.addBinding("command", config.getCommand());
    container.addBinding("args", config.getArgs());
    container.addBinding("volumeMounts", volumeMounts);
    container.addBinding("readinessProbe", null);
    container.addBinding("livenessProbe", null);
    container.addBinding("lifecycle", null);
    container.addBinding("env", config.getEnv());
    container.addBinding("resources", null);

    return container.toString();
  }

  default String buildContainer(
      String name,
      AccountDeploymentDetails<KubernetesAccount> details,
      ServiceSettings settings,
      List<ConfigSource> configSources,
      Map<String, String> env) {
    List<String> volumeMounts =
        configSources.stream()
            .map(
                c -> {
                  TemplatedResource volume =
                      new JinjaJarResource("/kubernetes/manifests/volumeMount.yml");
                  volume.addBinding("name", c.getId());
                  volume.addBinding("mountPath", c.getMountPath());
                  return volume.toString();
                })
            .collect(Collectors.toList());

    volumeMounts.addAll(
        settings.getKubernetes().getVolumes().stream()
            .map(
                c -> {
                  TemplatedResource volume =
                      new JinjaJarResource("/kubernetes/manifests/volumeMount.yml");
                  volume.addBinding("name", c.getId());
                  volume.addBinding("mountPath", c.getMountPath());
                  return volume.toString();
                })
            .collect(Collectors.toList()));

    String lifecycle = "{}";
    List<String> preStopCommand = getPreStopCommand(settings);
    if (!preStopCommand.isEmpty()) {
      TemplatedResource lifecycleResource =
          new JinjaJarResource("/kubernetes/manifests/lifecycle.yml");
      lifecycleResource.addBinding("command", getPreStopCommand(settings));
      lifecycle = lifecycleResource.toString();
    }

    DeploymentEnvironment deploymentEnvironment =
        details.getDeploymentConfiguration().getDeploymentEnvironment();
    CustomSizing customSizing = deploymentEnvironment.getCustomSizing();
    TemplatedResource resources = new JinjaJarResource("/kubernetes/manifests/resources.yml");
    if (customSizing != null) {
      // Look for container specific sizing otherwise fall back to service sizing
      Map componentSizing =
          customSizing.getOrDefault(
              name, customSizing.getOrDefault(getService().getServiceName(), new HashMap()));
      resources.addBinding("requests", componentSizing.getOrDefault("requests", new HashMap()));
      resources.addBinding("limits", componentSizing.getOrDefault("limits", new HashMap()));
    }

    TemplatedResource container = new JinjaJarResource("/kubernetes/manifests/container.yml");
    container.addBinding("name", name);
    container.addBinding("imageId", settings.getArtifactId());
    TemplatedResource port = new JinjaJarResource("/kubernetes/manifests/port.yml");
    port.addBinding("port", settings.getPort());
    container.addBinding("port", port.toString());
    container.addBinding("volumeMounts", volumeMounts);

    TemplatedResource readinessProbe = getProbe(settings, null);
    container.addBinding("readinessProbe", readinessProbe.toString());

    DeploymentEnvironment.LivenessProbeConfig livenessProbeConfig =
        deploymentEnvironment.getLivenessProbeConfig();
    if (livenessProbeConfig != null
        && livenessProbeConfig.isEnabled()
        && livenessProbeConfig.getInitialDelaySeconds() != null) {
      TemplatedResource livenessProbe =
          getProbe(settings, livenessProbeConfig.getInitialDelaySeconds());
      container.addBinding("livenessProbe", livenessProbe.toString());
    }

    container.addBinding("lifecycle", lifecycle);
    container.addBinding("env", env);
    container.addBinding("resources", resources.toString());

    return container.toString();
  }

  default TemplatedResource getProbe(ServiceSettings settings, Integer initialDelaySeconds) {
    TemplatedResource probe;
    if (StringUtils.isEmpty(settings.getHealthEndpoint())
        || settings.getKubernetes().getUseTcpProbe()) {
      probe = new JinjaJarResource("/kubernetes/manifests/tcpSocketProbe.yml");
      probe.addBinding("port", settings.getPort());
    } else if (settings.getKubernetes().getUseExecHealthCheck()) {
      probe = new JinjaJarResource("/kubernetes/manifests/execProbe.yml");
      probe.addBinding("command", getReadinessExecCommand(settings));
    } else {
      probe = new JinjaJarResource("/kubernetes/manifests/httpProbe.yml");
      probe.addBinding("port", settings.getPort());
      probe.addBinding("path", settings.getHealthEndpoint());
      probe.addBinding("scheme", settings.getScheme().toUpperCase());
    }
    probe.addBinding("initialDelaySeconds", initialDelaySeconds);
    return probe;
  }

  default String getNamespace(ServiceSettings settings) {
    return settings.getLocation();
  }

  default List<String> combineVolumes(
      List<ConfigSource> configSources,
      KubernetesSettings settings,
      List<SidecarConfig> sidecarConfigs) {

    Stream<ConfigSource> configStream =
        configSources.stream()
            .collect(Collectors.toMap(ConfigSource::getId, (i) -> i, (a, b) -> a))
            .values()
            .stream();

    Stream<ConfigSource> settingStream =
        settings.getVolumes().stream()
            .collect(Collectors.toMap(ConfigSource::getId, (i) -> i, (a, b) -> a))
            .values()
            .stream();

    Stream<ConfigSource> sidecarStream =
        sidecarConfigs.stream()
            .map(SidecarConfig::getConfigMapVolumeMounts)
            .flatMap(Collection::stream)
            .map(
                c ->
                    new ConfigSource()
                        .setMountPath(c.getMountPath())
                        .setId(c.getConfigMapName())
                        .setType(ConfigSource.Type.configMap));

    Stream<ConfigSource> secretStream =
        sidecarConfigs.stream()
            .map(SidecarConfig::getSecretVolumeMounts)
            .flatMap(Collection::stream)
            .map(
                c ->
                    new ConfigSource()
                        .setMountPath(c.getMountPath())
                        .setId(c.getSecretName())
                        .setType(ConfigSource.Type.secret));

    Stream<ConfigSource> volumeConfigStream =
        Stream.of(configStream, settingStream, sidecarStream, secretStream).flatMap(s -> s);

    return volumeConfigStream.map(this::getVolumeYaml).collect(Collectors.toList());
  }

  default List<ConfigSource> stageConfig(
      KubernetesV2Executor executor,
      AccountDeploymentDetails<KubernetesAccount> details,
      GenerateService.ResolvedConfiguration resolvedConfiguration) {
    Map<String, Profile> profiles =
        resolvedConfiguration.getProfilesForService(getService().getType());
    String stagingPath = getSpinnakerStagingPath(details.getDeploymentName());
    SpinnakerRuntimeSettings runtimeSettings = resolvedConfiguration.getRuntimeSettings();

    Map<String, Set<Profile>> profilesByDirectory = new HashMap<>();
    List<String> requiredFiles = new ArrayList<>();
    Map<String, byte[]> requiredEncryptedFiles = new HashMap<>();
    List<ConfigSource> configSources = new ArrayList<>();
    String secretNamePrefix = getServiceName() + "-files";
    String namespace = getNamespace(resolvedConfiguration.getServiceSettings(getService()));
    KubernetesAccount account = details.getAccount();

    for (SidecarService sidecarService : getSidecars(runtimeSettings)) {
      for (Profile profile :
          sidecarService.getSidecarProfiles(resolvedConfiguration, getService())) {
        if (profile == null) {
          throw new HalException(
              Problem.Severity.FATAL,
              "Service "
                  + sidecarService.getService().getCanonicalName()
                  + " is required but was not supplied for deployment.");
        }

        profiles.put(profile.getName(), profile);
        requiredFiles.addAll(profile.getRequiredFiles());
        requiredEncryptedFiles.putAll(profile.getDecryptedFiles());
      }
    }

    for (Entry<String, Profile> entry : profiles.entrySet()) {
      Profile profile = entry.getValue();
      String outputFile = profile.getOutputFile();
      String mountPoint = Paths.get(outputFile).getParent().toString();

      Set<Profile> profilesInDirectory =
          profilesByDirectory.getOrDefault(mountPoint, new HashSet<>());
      profilesInDirectory.add(profile);

      requiredFiles.addAll(profile.getRequiredFiles());
      requiredEncryptedFiles.putAll(profile.getDecryptedFiles());
      profilesByDirectory.put(mountPoint, profilesInDirectory);
    }

    for (Entry<String, Set<Profile>> entry : profilesByDirectory.entrySet()) {
      Set<Profile> profilesInDirectory = entry.getValue();
      String mountPath = entry.getKey();
      List<SecretMountPair> files =
          profilesInDirectory.stream()
              .map(
                  p -> {
                    File input = new File(p.getStagedFile(stagingPath));
                    File output = new File(p.getOutputFile());
                    return new SecretMountPair(input, output);
                  })
              .collect(Collectors.toList());

      Map<String, String> env =
          profilesInDirectory.stream()
              .map(Profile::getEnv)
              .map(Map::entrySet)
              .flatMap(Collection::stream)
              .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

      KubernetesV2Utils.SecretSpec spec =
          executor
              .getKubernetesV2Utils()
              .createSecretSpec(
                  namespace, getService().getCanonicalName(), secretNamePrefix, files);
      executor.replace(spec.resource.toString());
      configSources.add(new ConfigSource().setId(spec.name).setMountPath(mountPath).setEnv(env));
    }

    if (!requiredFiles.isEmpty() || !requiredEncryptedFiles.isEmpty()) {
      List<SecretMountPair> files =
          requiredFiles.stream()
              .map(File::new)
              .map(SecretMountPair::new)
              .collect(Collectors.toList());

      // Add in memory decrypted files
      requiredEncryptedFiles.keySet().stream()
          .map(k -> new SecretMountPair(k, requiredEncryptedFiles.get(k)))
          .forEach(s -> files.add(s));

      KubernetesV2Utils.SecretSpec spec =
          executor
              .getKubernetesV2Utils()
              .createSecretSpec(
                  namespace, getService().getCanonicalName(), secretNamePrefix, files);
      executor.replace(spec.resource.toString());
      configSources.add(
          new ConfigSource()
              .setId(spec.name)
              .setMountPath(getSpinnakerStagingDependenciesPath(details.getDeploymentName())));
    }

    return configSources;
  }

  default List<SidecarConfig> getSidecarConfigs(
      AccountDeploymentDetails<KubernetesAccount> details) {
    // attempt to get the service name specific sidecars first
    List<SidecarConfig> sidecarConfigs =
        details
            .getDeploymentConfiguration()
            .getDeploymentEnvironment()
            .getSidecars()
            .getOrDefault(getService().getServiceName(), new ArrayList<>());

    if (sidecarConfigs.isEmpty()) {
      sidecarConfigs =
          details
              .getDeploymentConfiguration()
              .getDeploymentEnvironment()
              .getSidecars()
              .getOrDefault(getService().getBaseCanonicalName(), new ArrayList<>());
    }

    return sidecarConfigs;
  }

  default List<String> getHostAliases(AccountDeploymentDetails<KubernetesAccount> details) {
    // attempt to get the service name specific sidecars first
    List<Map> hostAliasesConfig =
        details
            .getDeploymentConfiguration()
            .getDeploymentEnvironment()
            .getHostAliases()
            .getOrDefault(getService().getServiceName(), new ArrayList<>());

    if (hostAliasesConfig.isEmpty()) {
      hostAliasesConfig =
          details
              .getDeploymentConfiguration()
              .getDeploymentEnvironment()
              .getHostAliases()
              .getOrDefault(getService().getBaseCanonicalName(), new ArrayList<>());
    }

    List<String> hostAliases =
        hostAliasesConfig.stream()
            .map(
                o -> {
                  try {
                    return getObjectMapper().writeValueAsString(o);
                  } catch (JsonProcessingException e) {
                    throw new HalException(
                        Problem.Severity.FATAL, "Invalid host alias format: " + e.getMessage(), e);
                  }
                })
            .collect(Collectors.toList());

    return hostAliases;
  }

  default String getAffinity(AccountDeploymentDetails<KubernetesAccount> details) {
    AffinityConfig affinityConfig =
        details
            .getDeploymentConfiguration()
            .getDeploymentEnvironment()
            .getAffinity()
            .getOrDefault(getService().getServiceName(), new AffinityConfig());

    if (affinityConfig.equals(new AffinityConfig())) {
      affinityConfig =
          details
              .getDeploymentConfiguration()
              .getDeploymentEnvironment()
              .getAffinity()
              .getOrDefault(getService().getBaseCanonicalName(), new AffinityConfig());
    }

    try {
      return getObjectMapper().writeValueAsString(affinityConfig);
    } catch (JsonProcessingException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Invalid affinity format: " + e.getMessage(), e);
    }
  }

  default String getTolerations(AccountDeploymentDetails<KubernetesAccount> details) {
    List<Toleration> toleration =
        details
            .getDeploymentConfiguration()
            .getDeploymentEnvironment()
            .getTolerations()
            .getOrDefault(getService().getServiceName(), new ArrayList<>());

    if (toleration.isEmpty()) {
      toleration =
          details
              .getDeploymentConfiguration()
              .getDeploymentEnvironment()
              .getTolerations()
              .getOrDefault(getService().getBaseCanonicalName(), new ArrayList<>());
    }

    try {
      return getObjectMapper().writeValueAsString(toleration);
    } catch (JsonProcessingException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Invalid tolerations format: " + e.getMessage(), e);
    }
  }

  default List<String> getInitContainers(AccountDeploymentDetails<KubernetesAccount> details) {
    List<Map> initContainersConfig =
        details
            .getDeploymentConfiguration()
            .getDeploymentEnvironment()
            .getInitContainers()
            .getOrDefault(getService().getServiceName(), new ArrayList<>());

    if (initContainersConfig.isEmpty()) {
      initContainersConfig =
          details
              .getDeploymentConfiguration()
              .getDeploymentEnvironment()
              .getInitContainers()
              .getOrDefault(getService().getBaseCanonicalName(), new ArrayList<>());
    }

    List<String> initContainers =
        initContainersConfig.stream()
            .map(
                o -> {
                  try {
                    return getObjectMapper().writeValueAsString(o);
                  } catch (JsonProcessingException e) {
                    throw new HalException(
                        Problem.Severity.FATAL,
                        "Invalid init container format: " + e.getMessage(),
                        e);
                  }
                })
            .collect(Collectors.toList());

    return initContainers;
  }

  default List<SidecarService> getSidecars(SpinnakerRuntimeSettings runtimeSettings) {
    SpinnakerMonitoringDaemonService monitoringService = getMonitoringDaemonService();
    List<SidecarService> result = new ArrayList<>();
    if (monitoringService == null) {
      return result;
    }

    ServiceSettings monitoringSettings = runtimeSettings.getServiceSettings(monitoringService);
    ServiceSettings thisSettings = runtimeSettings.getServiceSettings(getService());

    if (monitoringSettings.getEnabled() && thisSettings.getMonitored()) {
      result.add(monitoringService);
    }

    return result;
  }

  default Optional<String> buildAddress(String namespace) {
    return Optional.of(String.join(".", getServiceName(), namespace));
  }

  default ServiceSettings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    KubernetesSharedServiceSettings kubernetesSharedServiceSettings =
        new KubernetesSharedServiceSettings(deploymentConfiguration);
    ServiceSettings settings = defaultServiceSettings(deploymentConfiguration);
    String location = kubernetesSharedServiceSettings.getDeployLocation();
    settings
        .setArtifactId(getArtifactId(deploymentConfiguration))
        .setLocation(location)
        .setEnabled(isEnabled(deploymentConfiguration));
    buildAddress(location).ifPresent(settings::setAddress);
    if (runsOnJvm()) {
      // Use half the available memory allocated to the container for the JVM heap
      settings.getEnv().put("JAVA_OPTS", "-XX:MaxRAMPercentage=50.0");
    }
    return settings;
  }

  default String connectCommand(
      AccountDeploymentDetails<KubernetesAccount> details,
      SpinnakerRuntimeSettings runtimeSettings,
      KubernetesV2Utils kubernetesV2Utils) {
    ServiceSettings settings = runtimeSettings.getServiceSettings(getService());
    KubernetesAccount account = details.getAccount();
    String namespace = settings.getLocation();
    String name = getServiceName();
    int port = settings.getPort();

    String podNameCommand =
        String.join(" ", kubernetesV2Utils.kubectlPodServiceCommand(account, namespace, name));

    return String.join(
        " ",
        kubernetesV2Utils.kubectlConnectPodCommand(
            account, namespace, "$(" + podNameCommand + ")", port));
  }
}
