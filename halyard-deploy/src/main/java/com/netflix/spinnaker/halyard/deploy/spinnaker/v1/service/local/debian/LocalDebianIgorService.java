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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.debian;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.HasServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.IgorService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.LogCollector;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.LocalLogCollectorFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Delegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public class LocalDebianIgorService extends IgorService
    implements LocalDebianService<IgorService.Igor> {
  final String upstartServiceName = "igor";

  @Autowired ArtifactService artifactService;

  @Autowired LocalLogCollectorFactory localLogCollectorFactory;

  @Delegate(excludes = HasServiceSettings.class)
  LogCollector getLocalLogCollector() {
    return localLogCollectorFactory.build(this);
  }

  @Override
  public ServiceSettings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    return new Settings()
        .setArtifactId(getArtifactId(deploymentConfiguration.getName()))
        .setHost(getDefaultHost())
        .setEnabled(true);
  }

  public String getArtifactId(String deploymentName) {
    return LocalDebianService.super.getArtifactId(deploymentName);
  }
}
