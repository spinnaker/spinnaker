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

import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplate;
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplateDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("pipelineTemplates")
public class PipelineTemplateController {

  @Autowired
  PipelineTemplateDAO pipelineTemplateDAO;

  // TODO rz - Add fiat authz

  @RequestMapping(value = "", method = RequestMethod.GET)
  List<PipelineTemplate> list(@RequestParam(required = false, value = "scopes", defaultValue = "global") List<String> scopes) {
    return (List<PipelineTemplate>) pipelineTemplateDAO.getPipelineTemplatesByScope(scopes);
  }

  @RequestMapping(value = "", method = RequestMethod.POST)
  void save(@RequestBody PipelineTemplate pipelineTemplate) {
    checkForDuplicatePipelineTemplate(pipelineTemplate.getId());
    pipelineTemplateDAO.create(pipelineTemplate.getId(), pipelineTemplate);
  }

  @RequestMapping(value = "{id}", method = RequestMethod.GET)
  PipelineTemplate get(@PathVariable String id) {
    return pipelineTemplateDAO.findById(id);
  }

  @RequestMapping(value = "{id}", method = RequestMethod.PUT)
  PipelineTemplate update(@PathVariable String id, @RequestBody PipelineTemplate pipelineTemplate) {
    PipelineTemplate existingPipelineTemplate = pipelineTemplateDAO.findById(id);
    if (!pipelineTemplate.getId().equals(existingPipelineTemplate.getId())) {
      throw new InvalidPipelineTemplateRequestException("The provided id " + id + " doesn't match the pipeline template id " + pipelineTemplate.getId());
    }

    pipelineTemplate.setLastModified(System.currentTimeMillis());
    pipelineTemplateDAO.update(id, pipelineTemplate);

    return pipelineTemplate;
  }

  private void checkForDuplicatePipelineTemplate(String id) {
    try {
      pipelineTemplateDAO.findById(id);
    } catch (NotFoundException e) {
      return;
    }
    throw new DuplicatePipelineTemplateIdException("A pipeline template with the id " + id + " already exists");
  }

  @ExceptionHandler(InvalidPipelineTemplateRequestException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map handleInvalidPipelineTemplateRequestException(InvalidPipelineTemplateRequestException e) {
    Map<String, Object> m = new HashMap<>();
    m.put("error", e.getMessage());
    m.put("status", HttpStatus.BAD_REQUEST);
    return m;
  }

  @ExceptionHandler(DuplicatePipelineTemplateIdException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map handleDuplicatePipelineTemplateIdException(DuplicatePipelineTemplateIdException e) {
    Map<String, Object> m = new HashMap<>();
    m.put("error", e.getMessage());
    m.put("status", HttpStatus.BAD_REQUEST);
    return m;
  }

  static class InvalidPipelineTemplateRequestException extends RuntimeException {
    InvalidPipelineTemplateRequestException(String message) {
      super(message);
    }
  }

  static class DuplicatePipelineTemplateIdException extends RuntimeException {
    DuplicatePipelineTemplateIdException(String message) {
      super(message);
    }
  }

}
