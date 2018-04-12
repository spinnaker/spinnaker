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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesImageDescription;
import com.netflix.spinnaker.halyard.config.model.v1.node.CustomSizing;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.core.resource.v1.JinjaJarResource;
import com.netflix.spinnaker.halyard.core.resource.v1.TemplatedResource;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ConfigSource;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.HasServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.KubernetesSharedServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2.KubernetesV2Utils.SecretMountPair;
import io.fabric8.utils.Strings;
import org.apache.commons.lang3.StringUtils;

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
import java.util.Set;
import java.util.stream.Collectors;

public interface KubernetesV2Service<T> extends HasServiceSettings<T> {
  String getServiceName();
  String getDockerRegistry(String deploymentName);
  String getSpinnakerStagingPath(String deploymentName);
  ArtifactService getArtifactService();
  ServiceSettings defaultServiceSettings();
  ObjectMapper getObjectMapper();

  default boolean isEnabled(DeploymentConfiguration deploymentConfiguration) {
    return true;
  }

  default List<String> getReadinessExecCommand(ServiceSettings settings) {
    return Arrays.asList("wget", "--spider", "-q", settings.getScheme() + "://localhost:" + settings.getPort() + settings.getHealthEndpoint());
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

    return service.toString();
  }

  default String getResourceYaml(AccountDeploymentDetails<KubernetesAccount> details,
      GenerateService.ResolvedConfiguration resolvedConfiguration) {
    ServiceSettings settings = resolvedConfiguration.getServiceSettings(getService());
    String namespace = getNamespace(settings);

    List<ConfigSource> configSources = stageConfig(details, resolvedConfiguration);
    Map<String, String> env = configSources.stream()
        .map(ConfigSource::getEnv)
        .map(Map::entrySet)
        .flatMap(Collection::stream)
        .collect(Collectors.toMap(
            Entry::getKey,
            Entry::getValue
        ));

    env.putAll(settings.getEnv());

    List<String> volumes = configSources.stream()
        .map(c -> {
          TemplatedResource volume = new JinjaJarResource("/kubernetes/manifests/volume.yml");
          volume.addBinding("name", c.getId());
          return volume.toString();
        }).collect(Collectors.toList());

    List<String> volumeMounts = configSources.stream()
        .map(c -> {
          TemplatedResource volume = new JinjaJarResource("/kubernetes/manifests/volumeMount.yml");
          volume.addBinding("name", c.getId());
          volume.addBinding("mountPath", c.getMountPath());
          return volume.toString();
        }).collect(Collectors.toList());

    TemplatedResource probe;
    if (StringUtils.isNotEmpty(settings.getHealthEndpoint())) {
      probe = new JinjaJarResource("/kubernetes/manifests/execReadinessProbe.yml");
      probe.addBinding("command", getReadinessExecCommand(settings));
    } else {
      probe = new JinjaJarResource("/kubernetes/manifests/tcpSocketReadinessProbe.yml");
      probe.addBinding("port", settings.getPort());
    }

    Integer targetSize = settings.getTargetSize();
    CustomSizing customSizing = details.getDeploymentConfiguration().getDeploymentEnvironment().getCustomSizing();
    TemplatedResource resources = new JinjaJarResource("/kubernetes/manifests/resources.yml");
    if (customSizing != null) {
      Map componentSizing = customSizing.getOrDefault(getService().getServiceName(), new HashMap());
      resources.addBinding("requests", componentSizing.getOrDefault("requests", new HashMap()));
      resources.addBinding("limits", componentSizing.getOrDefault("limits", new HashMap()));
      targetSize = (Integer) componentSizing.getOrDefault("replicas", targetSize);
    }

    TemplatedResource container = new JinjaJarResource("/kubernetes/manifests/container.yml");
    container.addBinding("name", getService().getCanonicalName());
    container.addBinding("imageId", settings.getArtifactId());
    container.addBinding("port", settings.getPort());
    container.addBinding("volumeMounts", volumeMounts);
    container.addBinding("probe", probe.toString());
    container.addBinding("env", env);
    container.addBinding("resources", resources.toString());

    List<String> containers = Collections.singletonList(container.toString());
    TemplatedResource podSpec = new JinjaJarResource("/kubernetes/manifests/podSpec.yml")
        .addBinding("containers", containers)
        .addBinding("volumes", volumes);

    return new JinjaJarResource("/kubernetes/manifests/deployment.yml")
        .addBinding("name", getService().getCanonicalName())
        .addBinding("namespace", namespace)
        .addBinding("replicas", targetSize)
        .addBinding("podAnnotations", settings.getKubernetes().getPodAnnotations())
        .addBinding("podSpec", podSpec.toString())
        .toString();
  }

  default String getNamespace(ServiceSettings settings) {
    return settings.getLocation();
  }

  default List<ConfigSource> stageConfig(AccountDeploymentDetails<KubernetesAccount> details,
      GenerateService.ResolvedConfiguration resolvedConfiguration) {
    Map<String, Profile> profiles = resolvedConfiguration.getProfilesForService(getService().getType());
    String stagingPath = getSpinnakerStagingPath(details.getDeploymentName());

    Map<String, Set<Profile>> profilesByDirectory = new HashMap<>();
    List<String> requiredFiles = new ArrayList<>();
    List<ConfigSource> configSources = new ArrayList<>();
    String secretNamePrefix = getServiceName() + "-files";
    String namespace = getNamespace(resolvedConfiguration.getServiceSettings(getService()));
    KubernetesAccount account = details.getAccount();

    for (Entry<String, Profile> entry : profiles.entrySet()) {
      Profile profile = entry.getValue();
      String outputFile = profile.getOutputFile();
      String mountPoint = Paths.get(outputFile).getParent().toString();

      Set<Profile> profilesInDirectory = profilesByDirectory.getOrDefault(mountPoint, new HashSet<>());
      profilesInDirectory.add(profile);

      requiredFiles.addAll(profile.getRequiredFiles());
      profilesByDirectory.put(mountPoint, profilesInDirectory);
    }

    for (Entry<String, Set<Profile>> entry: profilesByDirectory.entrySet()) {
      Set<Profile> profilesInDirectory = entry.getValue();
      String mountPath = entry.getKey();
      List<SecretMountPair> files = profilesInDirectory.stream()
          .map(p -> {
            File input = new File(p.getStagedFile(stagingPath));
            File output = new File(p.getOutputFile());
            return new SecretMountPair(input, output);
          })
          .collect(Collectors.toList());

      Map<String, String> env = profilesInDirectory.stream()
          .map(Profile::getEnv)
          .map(Map::entrySet)
          .flatMap(Collection::stream)
          .collect(Collectors.toMap(
              Entry::getKey,
              Entry::getValue
          ));

      String name = KubernetesV2Utils.createSecret(account, namespace, secretNamePrefix, files);
      configSources.add(new ConfigSource()
          .setId(name)
          .setMountPath(mountPath)
          .setEnv(env)
      );
    }

    if (!requiredFiles.isEmpty()) {
      List<SecretMountPair> files = requiredFiles.stream()
          .map(File::new)
          .map(SecretMountPair::new)
          .collect(Collectors.toList());

      String name = KubernetesV2Utils.createSecret(account, namespace, secretNamePrefix, files);
      configSources.add(new ConfigSource()
          .setId(name)
          .setMountPath(files.get(0).getContents().getParent())
      );
    }

    return configSources;
  }

  default String getArtifactId(String deploymentName) {
    String artifactName = getArtifact().getName();
    String version = getArtifactService().getArtifactVersion(deploymentName, getArtifact());

    KubernetesImageDescription image = new KubernetesImageDescription(artifactName, version, getDockerRegistry(deploymentName));
    return KubernetesUtil.getImageId(image);
  }

  default String buildAddress(String namespace) {
    return Strings.join(".", getServiceName(), namespace);
  }

  default ServiceSettings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    KubernetesSharedServiceSettings kubernetesSharedServiceSettings = new KubernetesSharedServiceSettings(deploymentConfiguration);
    ServiceSettings settings = defaultServiceSettings();
    String location = kubernetesSharedServiceSettings.getDeployLocation();
    settings.setAddress(buildAddress(location))
        .setArtifactId(getArtifactId(deploymentConfiguration.getName()))
        .setLocation(location)
        .setEnabled(isEnabled(deploymentConfiguration));
    return settings;
  }

  default String connectCommand(AccountDeploymentDetails<KubernetesAccount> details,
      SpinnakerRuntimeSettings runtimeSettings) {
    ServiceSettings settings = runtimeSettings.getServiceSettings(getService());
    KubernetesAccount account = details.getAccount();
    String namespace = settings.getLocation();
    String name = getServiceName();
    int port = settings.getPort();

    String podNameCommand = String.join(" ", KubernetesV2Utils.kubectlPodServiceCommand(account,
        namespace,
        name));

    return String.join(" ", KubernetesV2Utils.kubectlConnectPodCommand(account,
        namespace,
        "$(" + podNameCommand + ")",
        port));
  }
}
