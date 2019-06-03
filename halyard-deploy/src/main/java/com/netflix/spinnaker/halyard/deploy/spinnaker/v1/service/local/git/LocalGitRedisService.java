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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.git;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.RedisService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public class LocalGitRedisService extends RedisService implements LocalGitService<Jedis> {
  String startCommand = "";

  @Autowired String gitRoot;

  @Override
  public ServiceSettings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    return new Settings()
        .setArtifactId("redis")
        .setHost(getDefaultHost())
        .setSkipLifeCycleManagement(true)
        .setEnabled(true);
  }

  @Autowired ArtifactService artifactService;

  @Override
  public String installArtifactCommand(DeploymentDetails deploymentDetails) {
    return "";
  }

  @Override
  public void collectLogs(DeploymentDetails details, SpinnakerRuntimeSettings runtimeSettings) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String prepArtifactCommand(DeploymentDetails deploymentDetails) {
    return "";
  }
}
