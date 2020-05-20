/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.gate.services;

import com.netflix.spinnaker.gate.services.commands.HystrixFactory;
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskService {

  private final Logger log = LoggerFactory.getLogger(getClass());
  private static final String GROUP = "tasks";

  private OrcaServiceSelector orcaServiceSelector;
  private ClouddriverServiceSelector clouddriverServiceSelector;

  public TaskService(
      OrcaServiceSelector orcaServiceSelector,
      ClouddriverServiceSelector clouddriverServiceSelector) {
    this.orcaServiceSelector = orcaServiceSelector;
    this.clouddriverServiceSelector = clouddriverServiceSelector;
  }

  public Map create(Map body) {
    if (body.containsKey("application")) {
      AuthenticatedRequest.setApplication(body.get("application").toString());
    }

    return orcaServiceSelector.select().doOperation(body);
  }

  public Map createAppTask(String app, Map body) {
    body.put("application", app);
    AuthenticatedRequest.setApplication(app);
    return orcaServiceSelector.select().doOperation(body);
  }

  public Map createAppTask(Map body) {
    if (body.containsKey("application")) {
      AuthenticatedRequest.setApplication(body.get("application").toString());
    }

    return orcaServiceSelector.select().doOperation(body);
  }

  public Map getTask(final String id) {
    return (Map)
        HystrixFactory.newMapCommand(
                GROUP, "getTask", () -> getOrcaServiceSelector().select().getTask(id))
            .execute();
  }

  public Map deleteTask(final String id) {
    setApplicationForTask(id);
    return (Map)
        HystrixFactory.newMapCommand(
                GROUP, "deleteTask", () -> getOrcaServiceSelector().select().deleteTask(id))
            .execute();
  }

  public Map getTaskDetails(final String taskDetailsId, String selectorKey) {
    return (Map)
        HystrixFactory.newMapCommand(
                GROUP,
                "getTaskDetails",
                () -> getClouddriverServiceSelector().select().getTaskDetails(taskDetailsId))
            .execute();
  }

  public Map cancelTask(final String id) {
    setApplicationForTask(id);
    return (Map)
        HystrixFactory.newMapCommand(
                GROUP, "cancelTask", () -> getOrcaServiceSelector().select().cancelTask(id, ""))
            .execute();
  }

  public Map cancelTasks(final List<String> taskIds) {
    setApplicationForTask(taskIds.get(0));
    return (Map)
        HystrixFactory.newMapCommand(
                GROUP, "cancelTasks", () -> getOrcaServiceSelector().select().cancelTasks(taskIds))
            .execute();
  }

  public Map createAndWaitForCompletion(Map body, int maxPolls, int intervalMs) {
    log.info("Creating and waiting for completion: " + String.valueOf(body));

    if (body.containsKey("application")) {
      AuthenticatedRequest.setApplication(body.get("application").toString());
    }

    Map createResult = create(body);
    if (createResult.get("ref") == null) {
      log.warn(
          "No ref field found in create result, returning entire result: "
              + String.valueOf(createResult));
      return createResult;
    }

    String taskId = ((String) createResult.get("ref")).split("/")[2];
    log.info("Create succeeded; polling task for completion: " + taskId);

    LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(1);
    map.put("id", taskId);
    Map task = map;
    int i = 0;
    while (i < maxPolls) {
      i = i++;
      try {
        Thread.sleep(intervalMs);
      } catch (InterruptedException ignored) {
      }

      task = getTask(taskId);
      if (new ArrayList<String>(Arrays.asList("SUCCEEDED", "TERMINAL"))
          .contains((String) task.get("status"))) {
        return task;
      }
    }

    return task;
  }

  public Map createAndWaitForCompletion(Map body, int maxPolls) {
    return createAndWaitForCompletion(body, maxPolls, 1000);
  }

  public Map createAndWaitForCompletion(Map body) {
    return createAndWaitForCompletion(body, 32, 1000);
  }

  /** @deprecated This pipeline operation does not belong here. */
  @Deprecated
  public Map cancelPipeline(final String id, final String reason) {
    return (Map)
        HystrixFactory.newMapCommand(
                GROUP,
                "cancelPipeline",
                () -> getOrcaServiceSelector().select().cancelPipeline(id, reason, false, ""))
            .execute();
  }

  /**
   * Retrieve an orca task by id to populate RequestContext application
   *
   * @param id
   */
  public void setApplicationForTask(String id) {
    try {
      Map task = getTask(id);
      if (task.containsKey("application")) {
        AuthenticatedRequest.setApplication(task.get("application").toString());
      }

    } catch (Exception e) {
      log.error("Error loading execution {} from orca", id, e);
    }
  }

  public OrcaServiceSelector getOrcaServiceSelector() {
    return orcaServiceSelector;
  }

  public void setOrcaServiceSelector(OrcaServiceSelector orcaServiceSelector) {
    this.orcaServiceSelector = orcaServiceSelector;
  }

  public ClouddriverServiceSelector getClouddriverServiceSelector() {
    return clouddriverServiceSelector;
  }

  public void setClouddriverServiceSelector(ClouddriverServiceSelector clouddriverServiceSelector) {
    this.clouddriverServiceSelector = clouddriverServiceSelector;
  }
}
