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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsProvider;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Component
public class AwsCredentialsProfileFactoryBuilder {
  @Autowired
  protected ArtifactService artifactService;

  public AwsCredentialsProfileFactory build(SpinnakerArtifact artifact) {
    return new AwsCredentialsProfileFactory(artifact);
  }

  public String getOutputFile(String spinnakerHome) {
    return Paths.get(spinnakerHome, ".aws/credentials").toString();
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  public class AwsCredentialsProfileFactory extends TemplateBackedProfileFactory {
    public AwsCredentialsProfileFactory(SpinnakerArtifact artifact) {
      super();
      this.artifact = artifact;
    }

    @Override
    protected ArtifactService getArtifactService() {
      return artifactService;
    }

    private String template = String.join("\n",
        "[default]",
        "aws_access_key_id = {%accessKeyId%}",
        "aws_secret_access_key = {%secretAccessKey%}"
    );

    @Override
    protected Map<String, String> getBindings(DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
      AwsProvider awsProvider = deploymentConfiguration.getProviders().getAws();
      Map<String, String> result = new HashMap<>();
      result.put("accessKeyId", awsProvider.getAccessKeyId());
      result.put("secretAccessKey", awsProvider.getSecretAccessKey());
      return result;
    }

    SpinnakerArtifact artifact;

    @Override
    protected String commentPrefix() {
      return null;
    }

    @Override
    protected boolean showEditWarning() {
      return false;
    }
  }
}
