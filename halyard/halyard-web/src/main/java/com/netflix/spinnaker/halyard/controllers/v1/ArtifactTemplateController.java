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

package com.netflix.spinnaker.halyard.controllers.v1;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.ArtifactTemplate;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.services.v1.ArtifactTemplateService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericDeleteRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/artifactTemplates")
@RequiredArgsConstructor
public class ArtifactTemplateController {
  private final ArtifactTemplateService artifactTemplateService;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final HalconfigParser halconfigParser;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<ArtifactTemplate>> getArtifactTemplates(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<List<ArtifactTemplate>>builder()
        .getter(() -> artifactTemplateService.getAllArtifactTemplates(deploymentName))
        .validator(() -> artifactTemplateService.validateAllArtifactTemplates(deploymentName))
        .description("Get artifact templates")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{templateName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, ArtifactTemplate> getArtifactTemplate(
      @PathVariable String deploymentName,
      @PathVariable String templateName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<ArtifactTemplate>builder()
        .getter(() -> artifactTemplateService.getArtifactTemplate(deploymentName, templateName))
        .validator(
            () -> artifactTemplateService.validateArtifactTemplate(deploymentName, templateName))
        .description("Get the " + templateName + " artifact template")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{templateName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setArtifactTemplate(
      @PathVariable String deploymentName,
      @PathVariable String templateName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody ArtifactTemplate artifactTemplate) {
    return GenericUpdateRequest.<ArtifactTemplate>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(t -> artifactTemplateService.setArtifactTemplate(deploymentName, templateName, t))
        .validator(
            () -> artifactTemplateService.validateArtifactTemplate(deploymentName, templateName))
        .description("Edit the " + templateName + " artifact template")
        .build()
        .execute(validationSettings, artifactTemplate);
  }

  @RequestMapping(value = "/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addArtifactTemplate(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody ArtifactTemplate artifactTemplate) {
    return GenericUpdateRequest.<ArtifactTemplate>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(t -> artifactTemplateService.addArtifactTemplate(deploymentName, t))
        .validator(
            () ->
                artifactTemplateService.validateArtifactTemplate(
                    deploymentName, artifactTemplate.getName()))
        .description("Add the " + artifactTemplate.getName() + " artifact template")
        .build()
        .execute(validationSettings, artifactTemplate);
  }

  @RequestMapping(value = "/{templateName:.+}", method = RequestMethod.DELETE)
  DaemonTask<Halconfig, Void> deleteArtifactTemplate(
      @PathVariable String deploymentName,
      @PathVariable String templateName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericDeleteRequest.builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .deleter(() -> artifactTemplateService.deleteArtifactTemplate(deploymentName, templateName))
        .validator(() -> artifactTemplateService.validateAllArtifactTemplates(deploymentName))
        .description("Delete the " + templateName + " artifact template")
        .build()
        .execute(validationSettings);
  }
}
