/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.TaskService;
import io.swagger.annotations.ApiOperation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/batch/tags")
public class BatchEntityTagsController {

  private TaskService taskService;

  @Autowired
  public BatchEntityTagsController(TaskService taskService) {
    this.taskService = taskService;
  }

  @ApiOperation(value = "Batch update a set of entity tags.", response = HashMap.class)
  @RequestMapping(method = RequestMethod.POST)
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  public Map batchUpdate(
      @RequestBody List<Map> entityTags,
      @RequestParam(defaultValue = "adhoc", name = "application") String application) {
    Map<String, Object> job = new HashMap<>();
    job.put("type", "bulkUpsertEntityTags");
    job.put("entityTags", entityTags);

    List<Map<String, Object>> jobs = new ArrayList<>();
    jobs.add(job);

    Map<String, Object> operation = new HashMap<>();
    operation.put("description", "Bulk upsert Tags");
    operation.put("job", jobs);
    return taskService.createAppTask(application, operation);
  }
}
