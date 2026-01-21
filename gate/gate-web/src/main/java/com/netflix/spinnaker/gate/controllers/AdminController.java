/*
 * Copyright 2026 Wise, PLC.
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

package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

  private final OrcaServiceSelector orcaServiceSelector;

  public AdminController(OrcaServiceSelector orcaServiceSelector) {
    this.orcaServiceSelector = orcaServiceSelector;
  }

  @Operation(summary = "Admin endpoint to force cancel an execution")
  @RequestMapping(value = "/executions/forceCancel", method = RequestMethod.PUT)
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  void forceCancelExecution(
      @Parameter(description = "The execution ID to be cancelled.")
          @RequestParam(value = "executionId")
          String executionId,
      @Parameter(description = "The type of execution, either PIPELINE or ORCHESTRATION.")
          @RequestParam(value = "executionType")
          String executionType) {
    Optional<String> user = AuthenticatedRequest.getSpinnakerUser();

    // pre-authorize should prevent this from ever happening, but just in case
    if (user.isEmpty()) {
      throw new AccessDeniedException("User not authenticated");
    }

    Retrofit2SyncCall.execute(
        orcaServiceSelector.select().forceCancelExecution(executionId, executionType, user.get()));
  }
}
