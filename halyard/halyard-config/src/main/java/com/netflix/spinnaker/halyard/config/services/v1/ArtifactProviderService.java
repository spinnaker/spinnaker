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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.bitbucket.BitbucketArtifactProvider;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.gcs.GcsArtifactProvider;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.github.GitHubArtifactProvider;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.gitlab.GitlabArtifactProvider;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.gitrepo.GitRepoArtifactProvider;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.helm.HelmArtifactProvider;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.http.HttpArtifactProvider;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.oracle.OracleArtifactProvider;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.s3.S3ArtifactProvider;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactProvider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Artifacts;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the
 * current halconfigs providers.
 */
@Component
public class ArtifactProviderService {
  @Autowired private LookupService lookupService;

  @Autowired private ValidateService validateService;

  @Autowired private DeploymentService deploymentService;

  public ArtifactProvider getArtifactProvider(String deploymentName, String providerName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setArtifactProvider(providerName);

    List<ArtifactProvider> matching =
        lookupService.getMatchingNodesOfType(filter, ArtifactProvider.class);

    switch (matching.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Severity.FATAL, "No provider with name \"" + providerName + "\" could be found")
                .setRemediation("Create a new provider with name \"" + providerName + "\"")
                .build());
      case 1:
        return matching.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Severity.FATAL,
                    "More than one provider with name \"" + providerName + "\" found")
                .setRemediation(
                    "Manually delete or rename duplicate providers with name \""
                        + providerName
                        + "\" in your halconfig file")
                .build());
    }
  }

  public List<ArtifactProvider> getAllArtifactProviders(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).withAnyArtifactProvider();

    List<ArtifactProvider> matching =
        lookupService.getMatchingNodesOfType(filter, ArtifactProvider.class);

    if (matching.size() == 0) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(Severity.FATAL, "No providers could be found").build());
    } else {
      return matching;
    }
  }

  public void setArtifactProvider(String deploymentName, ArtifactProvider provider) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    Artifacts artifacts = deploymentConfiguration.getArtifacts();
    switch (provider.providerType()) {
      case BITBUCKET:
        artifacts.setBitbucket((BitbucketArtifactProvider) provider);
        break;
      case GCS:
        artifacts.setGcs((GcsArtifactProvider) provider);
        break;
      case ORACLE:
        artifacts.setOracle((OracleArtifactProvider) provider);
        break;
      case GITHUB:
        artifacts.setGithub((GitHubArtifactProvider) provider);
        break;
      case GITLAB:
        artifacts.setGitlab((GitlabArtifactProvider) provider);
        break;
      case GITREPO:
        artifacts.setGitrepo((GitRepoArtifactProvider) provider);
        break;
      case HTTP:
        artifacts.setHttp((HttpArtifactProvider) provider);
        break;
      case HELM:
        artifacts.setHelm((HelmArtifactProvider) provider);
        break;
      case S3:
        artifacts.setS3((S3ArtifactProvider) provider);
        break;
      default:
        throw new IllegalArgumentException("Unknown provider type " + provider.providerType());
    }
  }

  public void setEnabled(String deploymentName, String providerName, boolean enabled) {
    ArtifactProvider provider = getArtifactProvider(deploymentName, providerName);
    provider.setEnabled(enabled);
  }

  public ProblemSet validateArtifactProvider(String deploymentName, String providerName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setArtifactProvider(providerName)
            .withAnyAccount()
            .setBakeryDefaults()
            .withAnyBaseImage();

    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllArtifactProviders(String deploymentName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).withAnyArtifactProvider().withAnyAccount();

    return validateService.validateMatchingFilter(filter);
  }
}
