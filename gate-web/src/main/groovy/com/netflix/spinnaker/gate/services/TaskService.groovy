/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.gate.services


import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@CompileStatic
@Service
@Slf4j
class TaskService {
  private static final String GROUP = "tasks"

  @Autowired
  OrcaServiceSelector orcaServiceSelector

  @Autowired
  ClouddriverServiceSelector clouddriverServiceSelector

  Map create(Map body) {
    if (body.containsKey("application")) {
      AuthenticatedRequest.setApplication(body.get("application").toString())
    }
    orcaServiceSelector.select().doOperation(body)
  }

  Map createAppTask(String app, Map body) {
    body.application = app
    AuthenticatedRequest.setApplication(app)
    orcaServiceSelector.select().doOperation(body)
  }

  Map createAppTask(Map body) {
    if (body.containsKey("application")) {
      AuthenticatedRequest.setApplication(body.get("application").toString())
    }
    orcaServiceSelector.select().doOperation(body)
  }

  Map getTask(String id) {
    HystrixFactory.newMapCommand(GROUP, "getTask") {
      orcaServiceSelector.select().getTask(id)
    } execute()
  }

  Map deleteTask(String id) {
    setApplicationForTask(id)
    HystrixFactory.newMapCommand(GROUP, "deleteTask") {
      orcaServiceSelector.select().deleteTask(id)
    } execute()
  }

  Map getTaskDetails(String taskDetailsId, String selectorKey) {
    HystrixFactory.newMapCommand(GROUP, "getTaskDetails") {
      clouddriverServiceSelector.select().getTaskDetails(taskDetailsId)
    } execute()
  }

  Map cancelTask(String id) {
    setApplicationForTask(id)
    HystrixFactory.newMapCommand(GROUP, "cancelTask") {
      orcaServiceSelector.select().cancelTask(id, "")
    } execute()
  }

  Map cancelTasks(List<String> taskIds) {
    setApplicationForTask(taskIds.get(0))
    HystrixFactory.newMapCommand(GROUP, "cancelTasks") {
      orcaServiceSelector.select().cancelTasks(taskIds)
    } execute()
  }

  Map createAndWaitForCompletion(Map body, int maxPolls = 32, int intervalMs = 1000) {
    log.info("Creating and waiting for completion: ${body}")

    if (body.containsKey("application")) {
      AuthenticatedRequest.setApplication(body.get("application").toString())
    }

    Map createResult = create(body)
    if (!createResult.get("ref")) {
      log.warn("No ref field found in create result, returning entire result: ${createResult}")
      return createResult
    }

    String taskId = ((String) createResult.get("ref")).split('/')[2]
    log.info("Create succeeded; polling task for completion: ${taskId}")

    Map task = [ id: taskId ]
    int i = 0
    while (i < maxPolls) {
      i++
      sleep(intervalMs)

      task = getTask(taskId)
      if (['SUCCEEDED', 'TERMINAL'].contains((String) task.get("status"))) {
        return task
      }
    }
    return task
  }

  /**
   * @deprecated  This pipeline operation does not belong here.
   */
  @Deprecated
  Map cancelPipeline(String id, String reason) {
    HystrixFactory.newMapCommand(GROUP, "cancelPipeline") {
      orcaServiceSelector.select().cancelPipeline(id, reason, false, "")
    } execute()
  }

  /**
   * Retrieve an orca task by id to populate RequestContext application
   *
   * @param id
   */
  void setApplicationForTask(String id) {
    try {
      Map task = getTask(id)
      if (task.containsKey("application")) {
        AuthenticatedRequest.setApplication(task.get("application").toString())
      }
    } catch (Exception e) {
      log.error("Error loading execution {} from orca", id, e)
    }
  }

}
