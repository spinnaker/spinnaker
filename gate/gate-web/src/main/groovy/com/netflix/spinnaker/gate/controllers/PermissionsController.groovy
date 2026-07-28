/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.swagger.v3.oas.annotations.Operation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@Slf4j
@CompileStatic
@RestController
@RequestMapping("/permissions")
class PermissionsController {

  @Autowired
  Front50Service front50Service

  /**
   * The grants every application receives from Spinnaker's configuration, which Front50 owns.
   *
   * <p>Existing applications carry these on their own responses, but the application creation form
   * has no application to read them from, and it needs them to show which roles are already granted
   * before the operator adds any. This is the same set of role names already embedded in every
   * application response, so it is no more sensitive than those.
   */
  @Operation(summary = "Get the permissions every application is granted by configuration")
  @RequestMapping(value = "/defaults", method = RequestMethod.GET)
  Map getDefaultApplicationPermissions() {
    return Retrofit2SyncCall.execute(front50Service.getDefaultApplicationPermissions())
  }
}
