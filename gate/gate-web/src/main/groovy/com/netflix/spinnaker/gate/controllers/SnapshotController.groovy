/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.SnapshotService
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.swagger.v3.oas.annotations.Operation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@Slf4j
@CompileStatic
@RestController
@RequestMapping("/applications")
class SnapshotController {

  @Autowired
  SnapshotService snapshotService

  @Operation(summary = "Get current snapshot")
  @RequestMapping(value = "/{application}/snapshots/{account}", method = RequestMethod.GET)
  Map getCurrentSnapshot(@PathVariable("application") String application,
                         @PathVariable("account") String account) {
    snapshotService.getCurrent(application, account)
  }

  @Operation(summary = "Get snapshot history")
  @RequestMapping(value = "/{application}/snapshots/{account}/history", method = RequestMethod.GET)
  List<Map> getSnapshotHistory(@PathVariable("application") String application,
                               @PathVariable("account") String account,
                               @RequestParam(value = "limit", defaultValue= "20") int limit) {
    snapshotService.getHistory(application, account, limit)
  }

}
