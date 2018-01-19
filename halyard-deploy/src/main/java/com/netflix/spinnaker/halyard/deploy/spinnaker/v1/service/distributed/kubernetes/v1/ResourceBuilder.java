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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.CustomSizing;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ConfigSource;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.QuantityBuilder;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class ResourceBuilder {
  static Container buildContainer(String name, ServiceSettings settings, List<ConfigSource> configSources, DeploymentEnvironment deploymentEnvironment) {
    int port = settings.getPort();
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

    List<VolumeMount> volumeMounts = configSources.stream().map(c -> {
      return new VolumeMountBuilder().withMountPath(c.getMountPath()).withName(c.getId()).build();
    }).collect(Collectors.toList());

    ContainerBuilder containerBuilder = new ContainerBuilder();
    containerBuilder = containerBuilder
        .withName(name)
        .withImage(settings.getArtifactId())
        .withPorts(new ContainerPortBuilder().withContainerPort(port).build())
        .withVolumeMounts(volumeMounts)
        .withEnv(envVars)
        .withReadinessProbe(probeBuilder.build())
        .withResources(buildResourceRequirements(name, deploymentEnvironment));

    return containerBuilder.build();
  }

  static ResourceRequirements buildResourceRequirements(String serviceName, DeploymentEnvironment deploymentEnvironment) {
    Map<String, Map> customSizing = deploymentEnvironment.getCustomSizing().get(serviceName);

    if (customSizing == null) {
      return null;
    }

    ResourceRequirementsBuilder resourceRequirementsBuilder = new ResourceRequirementsBuilder();
    if (customSizing.get("requests") != null) {
      resourceRequirementsBuilder.addToRequests("memory", new QuantityBuilder().withAmount(CustomSizing.stringOrNull(customSizing.get("requests").get("memory"))).build());
      resourceRequirementsBuilder.addToRequests("cpu", new QuantityBuilder().withAmount(CustomSizing.stringOrNull(customSizing.get("requests").get("cpu"))).build());
    }
    if (customSizing.get("limits") != null) {
      resourceRequirementsBuilder.addToLimits("memory", new QuantityBuilder().withAmount(CustomSizing.stringOrNull(customSizing.get("limits").get("memory"))).build());
      resourceRequirementsBuilder.addToLimits("cpu", new QuantityBuilder().withAmount(CustomSizing.stringOrNull(customSizing.get("limits").get("cpu"))).build());
    }

    return resourceRequirementsBuilder.build();
  }
}
