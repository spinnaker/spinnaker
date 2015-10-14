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
import com.netflix.spinnaker.gate.services.internal.KatoService
import com.netflix.spinnaker.gate.services.internal.OrcaService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@CompileStatic
@Service
class TaskService {
  private static final String GROUP = "tasks"

  @Autowired
  OrcaService orcaService

  @Autowired
  KatoService katoService

  Map create(Map body) {
    orcaService.doOperation(body)
  }

  Map createAppTask(String app, Map body) {
    body.application = app
    orcaService.doOperation(body)
  }

  Map createAppTask(Map body) {
    orcaService.doOperation(body)
  }

  Map getTask(String id) {
    HystrixFactory.newMapCommand(GROUP, "getTask") {
      orcaService.getTask(id)
    } execute()
  }

  Map deleteTask(String id) {
    HystrixFactory.newMapCommand(GROUP, "deleteTask") {
      orcaService.deleteTask(id)
    } execute()
  }

  Map getTaskDetails(String taskDetailsId) {
    HystrixFactory.newMapCommand(GROUP, "getTaskDetails") {
      katoService.getTaskDetails(taskDetailsId)
    } execute()
  }

  Map cancelTask(String id) {
    HystrixFactory.newMapCommand(GROUP, "cancelTask") {
      orcaService.cancelTask(id)
    } execute()
  }

  /**
   * @deprecated  This pipeline operation does not belong here.
   */
  @Deprecated
  Map cancelPipeline(String id) {
    HystrixFactory.newMapCommand(GROUP, "cancelPipeline") {
      orcaService.cancelPipeline(id)
    } execute()
  }
}
