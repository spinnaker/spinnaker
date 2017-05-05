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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes;

import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ConfigSource;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import io.fabric8.kubernetes.api.model.*;

import java.util.List;
import java.util.stream.Collectors;

class ResourceBuilder {
  static Container buildContainer(String name, ServiceSettings settings, List<ConfigSource> configSources) {
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
        .withReadinessProbe(probeBuilder.build());

    return containerBuilder.build();
  }
}
