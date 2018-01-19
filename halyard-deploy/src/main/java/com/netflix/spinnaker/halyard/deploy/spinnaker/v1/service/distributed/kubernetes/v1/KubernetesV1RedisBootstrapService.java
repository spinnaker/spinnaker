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

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.HasServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.RedisBootstrapService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedLogCollector;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Delegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

@EqualsAndHashCode(callSuper = true)
@Component
@Data
public class KubernetesV1RedisBootstrapService extends RedisBootstrapService implements KubernetesV1DistributedService<Jedis> {
  @Delegate
  @Autowired
  KubernetesV1DistributedServiceDelegate distributedServiceDelegate;

  @Delegate(excludes = HasServiceSettings.class)
  public DistributedLogCollector getLogCollector() {
    return getLogCollectorFactory().build(this);
  }

  @Override
  public Settings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    KubernetesV1SharedServiceSettings kubernetesV1SharedServiceSettings = new KubernetesV1SharedServiceSettings(deploymentConfiguration);
    Settings settings = new Settings();
    String location = kubernetesV1SharedServiceSettings.getDeployLocation();
    settings.setAddress(buildAddress(location))
        .setArtifactId(getArtifactId(deploymentConfiguration.getName()))
        .setLocation(location)
        .setSafeToUpdate(true) // It's OK to flush this redis fully since we generally redeploy bootstrap clouddriver & orca
        .setEnabled(true);
    return settings;
  }

  public String getArtifactId(String deploymentName) {
    return "gcr.io/kubernetes-spinnaker/redis-cluster:v2";
  }

  final DeployPriority deployPriority = new DeployPriority(20);
  final boolean requiredToBootstrap = true;
}
