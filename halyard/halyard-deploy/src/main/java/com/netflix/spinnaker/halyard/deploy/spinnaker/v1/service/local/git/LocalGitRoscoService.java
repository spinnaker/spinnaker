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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.RoscoService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public class LocalGitRoscoService extends RoscoService
    implements LocalGitService<RoscoService.Rosco> {
  String startCommand = "./gradlew";

  @Autowired String gitRoot;

  @Autowired ArtifactService artifactService;

  @Autowired Yaml yamlParser;

  @Override
  protected String getConfigOutputPath() {
    return "~/.spinnaker";
  }

  @Override
  protected String getRoscoConfigPath() {
    return Paths.get(gitRoot, "/rosco/rosco-web/config").toString();
  }

  @Override
  public ServiceSettings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    return new Settings()
        .setArtifactId(getArtifactId(deploymentConfiguration.getName()))
        .setHost(getDefaultHost())
        .setEnabled(true);
  }

  public String getArtifactId(String deploymentName) {
    return LocalGitService.super.getArtifactId(deploymentName);
  }

  @Override
  public void collectLogs(DeploymentDetails details, SpinnakerRuntimeSettings runtimeSettings) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void appendCustomConfigDir(Profile profile) {
    Map parsedContents = (Map) yamlParser.load(profile.getContents());

    if (!(parsedContents.get("rosco") instanceof Map)) {
      parsedContents.put("rosco", new LinkedHashMap<String, Object>());
    }

    String packerDirectory = Paths.get(getRoscoConfigPath(), "packer").toString();
    ((Map) parsedContents.get("rosco")).put("configDir", packerDirectory);

    profile.setContents(yamlParser.dump(parsedContents));
  }
}
