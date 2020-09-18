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
    return getOrcaServiceSelector().select().getTask(id);
  }

  public Map deleteTask(final String id) {
    setApplicationForTask(id);
    return getOrcaServiceSelector().select().deleteTask(id);
  }

  public Map getTaskDetails(final String taskDetailsId, String selectorKey) {
    return getClouddriverServiceSelector().select().getTaskDetails(taskDetailsId);
  }

  public Map cancelTask(final String id) {
    setApplicationForTask(id);
    return getOrcaServiceSelector().select().cancelTask(id, "");
  }

  public Map cancelTasks(final List<String> taskIds) {
    setApplicationForTask(taskIds.get(0));
    return getOrcaServiceSelector().select().cancelTasks(taskIds);
  }

  public Map createAndWaitForCompletion(Map body, int maxPolls, int intervalMs) {
    log.info("Creating and waiting for completion: " + body);

    if (body.containsKey("application")) {
      AuthenticatedRequest.setApplication(body.get("application").toString());
    }

    Map createResult = create(body);
    if (createResult.get("ref") == null) {
      log.warn("No ref field found in create result, returning entire result: " + createResult);
      return createResult;
    }

    String taskId = ((String) createResult.get("ref")).split("/")[2];
    log.info("Create succeeded; polling task for completion: " + taskId);

    LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(1);
    map.put("id", taskId);
    Map task = map;
    for (int i = 0; i < maxPolls; i++) {
      try {
        Thread.sleep(intervalMs);
      } catch (InterruptedException ignored) {
      }

      task = getTask(taskId);
      if (new ArrayList<>(Arrays.asList("SUCCEEDED", "TERMINAL"))
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
    return getOrcaServiceSelector().select().cancelPipeline(id, reason, false, "");
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
