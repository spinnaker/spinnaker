/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.front50.controllers;

import static com.netflix.spinnaker.front50.model.pipeline.Pipeline.TYPE_TEMPLATED;
import static com.netflix.spinnaker.front50.model.pipeline.TemplateConfiguration.TemplateSource.SPINNAKER_PREFIX;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.front50.exception.BadRequestException;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.exceptions.DuplicateEntityException;
import com.netflix.spinnaker.front50.exceptions.InvalidRequestException;
import com.netflix.spinnaker.front50.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplate;
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplateDAO;
import com.netflix.spinnaker.front50.model.pipeline.TemplateConfiguration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("pipelineTemplates")
public class PipelineTemplateController {

  @Autowired(required = false)
  PipelineTemplateDAO pipelineTemplateDAO = null;

  @Autowired PipelineDAO pipelineDAO;

  @Autowired ObjectMapper objectMapper;

  // TODO rz - Add fiat authz

  @RequestMapping(value = "", method = RequestMethod.GET)
  List<PipelineTemplate> list(
      @RequestParam(required = false, value = "scopes") List<String> scopes) {
    return (List<PipelineTemplate>) getPipelineTemplateDAO().getPipelineTemplatesByScope(scopes);
  }

  @RequestMapping(value = "", method = RequestMethod.POST)
  void save(@RequestBody PipelineTemplate pipelineTemplate) {
    checkForDuplicatePipelineTemplate(pipelineTemplate.getId());
    getPipelineTemplateDAO().create(pipelineTemplate.getId(), pipelineTemplate);
  }

  @RequestMapping(value = "{id}", method = RequestMethod.GET)
  PipelineTemplate get(@PathVariable String id) {
    return getPipelineTemplateDAO().findById(id);
  }

  @RequestMapping(value = "{id}", method = RequestMethod.PUT)
  PipelineTemplate update(@PathVariable String id, @RequestBody PipelineTemplate pipelineTemplate) {
    PipelineTemplate existingPipelineTemplate = getPipelineTemplateDAO().findById(id);
    if (!pipelineTemplate.getId().equals(existingPipelineTemplate.getId())) {
      throw new InvalidRequestException(
          "The provided id "
              + id
              + " doesn't match the pipeline template id "
              + pipelineTemplate.getId());
    }

    pipelineTemplate.setLastModified(System.currentTimeMillis());
    getPipelineTemplateDAO().update(id, pipelineTemplate);

    return pipelineTemplate;
  }

  @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
  void delete(@PathVariable String id) {
    checkForDependentConfigs(id, true);
    checkForDependentTemplates(id);
    getPipelineTemplateDAO().delete(id);
  }

  @RequestMapping(value = "{id}/dependentPipelines", method = RequestMethod.GET)
  List<Pipeline> listDependentPipelines(
      @PathVariable String id,
      @RequestParam(required = false, value = "recursive", defaultValue = "false")
          boolean recursive) {
    List<String> dependentConfigsIds = getDependentConfigs(id, recursive);

    return pipelineDAO.all().stream()
        .filter(pipeline -> dependentConfigsIds.contains(pipeline.getId()))
        .collect(Collectors.toList());
  }

  @VisibleForTesting
  List<String> getDependentConfigs(String templateId, boolean recursive) {
    List<String> dependentConfigIds = new ArrayList<>();

    List<String> templateIds = convertAllTemplateIdsToSources(templateId, recursive);

    pipelineDAO.all().stream()
        .filter(pipeline -> pipeline.getType() != null && pipeline.getType().equals(TYPE_TEMPLATED))
        .forEach(
            templatedPipeline -> {
              String source;
              try {
                TemplateConfiguration config =
                    objectMapper.convertValue(
                        templatedPipeline.getConfig(), TemplateConfiguration.class);

                source = config.getPipeline().getTemplate().getSource();
              } catch (Exception e) {
                return;
              }

              if (source != null && containsAnyIgnoreCase(source, templateIds)) {
                dependentConfigIds.add(templatedPipeline.getId());
              }
            });
    return dependentConfigIds;
  }

  private List<String> convertAllTemplateIdsToSources(String rootTemplateId, boolean recursive) {
    List<String> templateIds = new ArrayList<>();
    templateIds.add(SPINNAKER_PREFIX + rootTemplateId);
    if (recursive) {
      for (String id : getDependentTemplates(rootTemplateId, Optional.empty())) {
        templateIds.add(SPINNAKER_PREFIX + id);
      }
    }
    return templateIds;
  }

  private static boolean containsAnyIgnoreCase(String source, List<String> templateIds) {
    for (String templateId : templateIds) {
      if (source.equalsIgnoreCase(templateId)) {
        return true;
      }
    }
    return false;
  }

  @VisibleForTesting
  void checkForDependentConfigs(String templateId, boolean recursive) {
    List<String> dependentConfigIds = getDependentConfigs(templateId, recursive);
    if (dependentConfigIds.size() != 0) {
      throw new InvalidRequestException(
          "The following pipeline configs"
              + " depend on this template: "
              + String.join(", ", dependentConfigIds));
    }
  }

  @VisibleForTesting
  List<String> getDependentTemplates(
      String templateId, Optional<Collection<PipelineTemplate>> templates) {
    List<String> dependentTemplateIds = new ArrayList<>();

    final Collection<PipelineTemplate> pipelineTemplates =
        templates.orElse(getPipelineTemplateDAO().all());
    pipelineTemplates.forEach(
        template -> {
          if (template.getSource() != null
              && template.getSource().equalsIgnoreCase(SPINNAKER_PREFIX + templateId)) {
            dependentTemplateIds.add(template.getId());
            dependentTemplateIds.addAll(
                getDependentTemplates(template.getId(), Optional.of(pipelineTemplates)));
          }
        });
    return dependentTemplateIds;
  }

  @VisibleForTesting
  void checkForDependentTemplates(String templateId) {
    List<String> dependentTemplateIds = getDependentTemplates(templateId, Optional.empty());
    if (dependentTemplateIds.size() != 0) {
      throw new InvalidRequestException(
          "The following pipeline templates"
              + " depend on this template: "
              + String.join(", ", dependentTemplateIds));
    }
  }

  private void checkForDuplicatePipelineTemplate(String id) {
    try {
      getPipelineTemplateDAO().findById(id);
    } catch (NotFoundException e) {
      return;
    }
    throw new DuplicateEntityException("A pipeline template with the id " + id + " already exists");
  }

  private PipelineTemplateDAO getPipelineTemplateDAO() {
    if (pipelineTemplateDAO == null) {
      throw new BadRequestException(
          "Pipeline Templates are not supported with your current storage backend");
    }
    return pipelineTemplateDAO;
  }
}
