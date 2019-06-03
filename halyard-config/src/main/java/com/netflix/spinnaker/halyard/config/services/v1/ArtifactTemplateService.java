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
 */

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.ArtifactTemplate;
import com.netflix.spinnaker.halyard.config.model.v1.node.Artifacts;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArtifactTemplateService {
  private final LookupService lookupService;
  private final ValidateService validateService;
  private final DeploymentService deploymentService;

  private Artifacts getArtifacts(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setArtifacts();

    return lookupService.getSingularNodeOrDefault(
        filter, Artifacts.class, Artifacts::new, n -> setArtifacts(deploymentName, n));
  }

  private void setArtifacts(String deploymentName, Artifacts newArtifacts) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    deploymentConfiguration.setArtifacts(newArtifacts);
  }

  public List<ArtifactTemplate> getAllArtifactTemplates(String deploymentName) {
    return getArtifacts(deploymentName).getTemplates();
  }

  public ArtifactTemplate getArtifactTemplate(String deploymentName, String templateName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setArtifactTemplate(templateName);
    List<ArtifactTemplate> matchingArtifactTemplates =
        lookupService.getMatchingNodesOfType(filter, ArtifactTemplate.class);

    switch (matchingArtifactTemplates.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Problem.Severity.FATAL,
                    "No artifact template with name \"" + templateName + "\" was found")
                .setRemediation("Create a new artifact template with name\"" + templateName + "\"")
                .build());
      case 1:
        return matchingArtifactTemplates.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Problem.Severity.FATAL,
                    "More than one artifact template named \"" + templateName + "\" was found")
                .setRemediation(
                    "Manually delete/rename duplicate artifact templates with name \""
                        + templateName
                        + "\" in your halconfig file")
                .build());
    }
  }

  public void setArtifactTemplate(
      String deploymentName, String artifactTemplateName, ArtifactTemplate newArtifactTemplate) {
    List<ArtifactTemplate> artifactTemplates = getAllArtifactTemplates(deploymentName);
    for (int i = 0; i < artifactTemplates.size(); i++) {
      if (artifactTemplates.get(i).getNodeName().equals(artifactTemplateName)) {
        artifactTemplates.set(i, newArtifactTemplate);
        return;
      }
    }
    throw new HalException(
        new ConfigProblemBuilder(
                Problem.Severity.FATAL,
                "Artifact template \"" + artifactTemplateName + "\" wasn't found")
            .build());
  }

  public void deleteArtifactTemplate(String deploymentName, String artifactTemplateName) {
    List<ArtifactTemplate> artifactTemplates = getAllArtifactTemplates(deploymentName);
    boolean removed =
        artifactTemplates.removeIf(template -> template.getName().equals(artifactTemplateName));

    if (!removed) {
      throw new HalException(
          new ConfigProblemBuilder(
                  Problem.Severity.FATAL,
                  "Artifact template \"" + artifactTemplateName + "\" wasn't found")
              .build());
    }
  }

  public void addArtifactTemplate(String deploymentName, ArtifactTemplate newArtifactTemplate) {
    List<ArtifactTemplate> artifactTemplates = getAllArtifactTemplates(deploymentName);
    artifactTemplates.add(newArtifactTemplate);
  }

  public ProblemSet validateAllArtifactTemplates(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).withAnyArtifactTemplate();
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateArtifactTemplate(String deploymentName, String artifactTemplateName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setArtifactTemplate(artifactTemplateName);
    return validateService.validateMatchingFilter(filter);
  }
}
