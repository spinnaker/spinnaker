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
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.StringBackedProfileFactory;
import java.nio.file.Paths;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;

@Data
@Component
@EqualsAndHashCode(callSuper = true)
public class KubernetesV2ClouddriverRoService extends KubernetesV2ClouddriverService{
  @Override
  public Type getType() {
    return Type.CLOUDDRIVER_RO;
  }

  @Override
  public boolean isEnabled(DeploymentConfiguration deploymentConfiguration) {
    return deploymentConfiguration.getDeploymentEnvironment().getHaServices().getClouddriver().isEnabled();
  }
  @Override
  public List<Profile> getProfiles(DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> profiles = super.getProfiles(deploymentConfiguration, endpoints);

    String filename = "clouddriver-ro.yml";
    String path = Paths.get(getConfigOutputPath(), filename).toString();

    // TODO(joonlim): Issue 2934 - Delete once ./halconfig/clouddriver-ro.yml is added to the clouddriver repo
    ArtifactService artifactService = getArtifactService();
    profiles.add(new StringBackedProfileFactory() {
      @Override
      protected String getRawBaseProfile() {
        return "";
      }

      @Override
      protected void setProfile(Profile profile, DeploymentConfiguration deploymentConfiguration,
          SpinnakerRuntimeSettings endpoints) {
        String contents = String.join("\n",
            "server:",
            "  port: ${services.clouddriver-ro.port:7002}",
            "  address: ${services.clouddriver-ro.host:localhost}",
            "",
            "redis:",
            "  connection: ${services.redis.baseUrl:redis://localhost:6379}",
            "",
            "caching:",
            "  redis:",
            "    hashingEnabled: false",
            "  writeEnabled: false",
            ""
        );
        profile.appendContents(contents);
      }

      @Override
      public SpinnakerArtifact getArtifact() {
        return SpinnakerArtifact.CLOUDDRIVER;
      }

      @Override
      protected String commentPrefix() {
        return "## ";
      }

      @Override
      public ArtifactService getArtifactService() {
        return artifactService;
      }
    }.getProfile(filename, path, deploymentConfiguration, endpoints));

    // TODO(joonlim): Issue 2934 - Uncomment once ./halconfig/clouddriver-ro.yml is added to the clouddriver repo
    /*
    // Remove clouddriver.yml in favor of clouddriver-ro.yml
    profiles = profiles.stream().filter(p -> !p.getName().equals("clouddriver.yml")).collect(Collectors.toList());

    profiles.add(getClouddriverProfileFactory().getProfile(filename, path, deploymentConfiguration, endpoints));
    */

    return profiles;
  }

  // TODO(joonlim): Issue 2934 - Override overrideServiceEndpoints for external Redis endpoint.
}
