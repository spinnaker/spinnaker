/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.front50.controllers;

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.exceptions.InvalidRequestException;
import com.netflix.spinnaker.front50.model.ItemDAO;
import com.netflix.spinnaker.front50.model.pipeline.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("actions")
class ReorderPipelinesController {
  @Autowired FiatPermissionEvaluator fiatPermissionEvaluator;

  @Autowired PipelineDAO pipelineDAO;

  @Autowired PipelineStrategyDAO pipelineStrategyDAO;

  @RequestMapping(value = "/pipelines/reorder", method = RequestMethod.POST)
  void reorderPipelines(@RequestBody Map<String, Object> requestBody) {
    handlePipelineReorder(requestBody, pipelineDAO);
  }

  @RequestMapping(value = "/strategies/reorder", method = RequestMethod.POST)
  void reorderPipelineStrategies(@RequestBody Map<String, Object> requestBody) {
    handlePipelineReorder(requestBody, pipelineStrategyDAO);
  }

  private void handlePipelineReorder(
      Map<String, Object> requestBody, ItemDAO<Pipeline> pipelineItemDAO) {
    String application = (String) requestBody.get("application");
    Map<String, Integer> idsToIndices = (Map<String, Integer>) requestBody.get("idsToIndices");

    if (application == null) {
      throw new InvalidRequestException("`application` is required field on request body");
    }

    if (idsToIndices == null) {
      throw new InvalidRequestException("`idsToIndices` is required field on request body");
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (!fiatPermissionEvaluator.storeWholePermission()
        && !fiatPermissionEvaluator.hasPermission(auth, application, "APPLICATION", "WRITE")) {
      throw new InvalidRequestException(
          "Application write permission is required to reorder pipelines");
    }

    for (String id : idsToIndices.keySet()) {
      Pipeline pipeline = pipelineItemDAO.findById(id);

      if (pipeline == null) {
        throw new NotFoundException(String.format("No pipeline of id %s found", id));
      }

      if (!pipeline.getApplication().equals(application)) {
        throw new InvalidRequestException(
            String.format(
                "Pipeline with id %s does not belong to application %s", id, application));
      }

      pipeline.setIndex(idsToIndices.get(id));
      pipelineItemDAO.update(id, pipeline);
    }
  }
}
