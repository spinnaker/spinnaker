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

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.RedisService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedService.DeployPriority;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Delegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

@Data
@Component
@EqualsAndHashCode(callSuper = true)
public class KubernetesV2RedisService extends RedisService implements KubernetesV2Service<Jedis> {
  final DeployPriority deployPriority = new DeployPriority(5);

  @Delegate @Autowired KubernetesV2ServiceDelegate serviceDelegate;

  @Override
  public boolean runsOnJvm() {
    return false;
  }

  public String getArtifactId(DeploymentConfiguration deploymentConfiguration) {
    return "redis:6.2";
  }

  @Override
  public ServiceSettings defaultServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    return new Settings();
  }
}
