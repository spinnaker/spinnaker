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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.git;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.resource.v1.StringReplaceJarResource;
import com.netflix.spinnaker.halyard.core.resource.v1.TemplatedResource;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.LocalService;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface LocalGitService<T> extends LocalService<T> {
  ArtifactService getArtifactService();

  default String getDefaultHost() {
    return "localhost";
  }

  String getGitRoot();

  String getStartCommand();

  default String getScriptsDir() {
    return Paths.get(getGitRoot(), "scripts").toString();
  }

  default String getHomeDirectory() {
    return System.getProperty("user.home");
  }

  default String getArtifactId(String deploymentName) {
    return String.join("@", getArtifact().getName(), getArtifactCommit(deploymentName));
  }

  default String getArtifactCommit(String deploymentName) {
    SpinnakerArtifact artifact = getArtifact();
    return getArtifactService().getArtifactCommit(deploymentName, artifact);
  }

  default String installArtifactCommand(DeploymentDetails deploymentDetails) {
    Map<String, Object> bindings = new HashMap<>();
    bindings.put("scripts-dir", getScriptsDir());
    bindings.put("artifact", getArtifact().getName());
    TemplatedResource installResource = new StringReplaceJarResource("/git/install-component.sh");
    installResource.setBindings(bindings);
    return installResource.toString();
  }

  default void commitWrapperScripts() {
    Map<String, Object> bindings = new HashMap<>();
    bindings.put("git-root", getGitRoot());
    bindings.put("scripts-dir", getScriptsDir());
    bindings.put("artifact", getArtifact().getName());
    bindings.put("start-command", getStartCommand());
    TemplatedResource scriptResource = new StringReplaceJarResource("/git/start.sh");
    scriptResource.setBindings(bindings);
    String script = scriptResource.toString();

    new RemoteAction()
        .setScript(script)
        .commitScript(Paths.get(getScriptsDir(), getArtifact().getName() + "-start.sh"));

    scriptResource = new StringReplaceJarResource("/git/stop.sh");
    scriptResource.setBindings(bindings);
    script = scriptResource.toString();

    new RemoteAction()
        .setScript(script)
        .commitScript(Paths.get(getScriptsDir(), getArtifact().getName() + "-stop.sh"));
  }

  default String prepArtifactCommand(DeploymentDetails deploymentDetails) {
    Map<String, Object> bindings = new HashMap<>();
    String artifactName = getArtifact().getName();
    bindings.put("artifact", artifactName);
    bindings.put("repo", artifactName); // TODO(lwander): make configurable
    bindings.put("version", getArtifactCommit(deploymentDetails.getDeploymentName()));
    bindings.put("git-root", getGitRoot());

    DeploymentEnvironment env =
        deploymentDetails.getDeploymentConfiguration().getDeploymentEnvironment();
    DeploymentEnvironment.GitConfig gitConfig = env.getGitConfig();
    boolean update = env.getUpdateVersions();

    bindings.put("update", update ? "true" : "");
    bindings.put("origin", gitConfig.getOriginUser());
    bindings.put("upstream", gitConfig.getUpstreamUser());

    TemplatedResource prepResource = new StringReplaceJarResource("/git/prep-component.sh");

    prepResource.setBindings(bindings);

    return prepResource.toString();
  }

  default String stageProfilesCommand(
      DeploymentDetails details, GenerateService.ResolvedConfiguration resolvedConfiguration) {
    Map<String, Profile> profiles =
        resolvedConfiguration.getProfilesForService(getService().getType());

    List<String> allCommands = new ArrayList<>();
    for (Map.Entry<String, Profile> entry : profiles.entrySet()) {
      Profile profile = entry.getValue();
      String source = profile.getStagedFile(getSpinnakerStagingPath(details.getDeploymentName()));
      String dest = profile.getOutputFile();
      allCommands.add(String.format("mkdir -p $(dirname %s)", dest));
      allCommands.add(String.format("cp    -p %s %s", source, dest));

      if (profile.isExecutable()) {
        allCommands.add(String.format("chmod +x %s", dest));
      }
    }

    return String.join("\n", allCommands);
  }
}
