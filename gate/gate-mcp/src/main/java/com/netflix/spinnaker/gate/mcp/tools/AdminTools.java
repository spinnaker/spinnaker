/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.gate.mcp.tools;

import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Fiat-admin-only MCP tools mirroring gate-web's {@code AdminController}.
 *
 * <p>Unlike every other tool in this module, these don't map onto a downstream service that
 * self-enforces Fiat - orca's {@code /admin/forceCancelExecution} endpoint has no authorization of
 * its own (see {@code orca-web}'s {@code AdminController.groovy}); it's protected only by
 * gate-web's {@code AdminController.killZombie}'s {@code @PreAuthorize("@fiatPermissionEvaluator
 * .isAdmin()")}. Since gate-mcp can't depend on gate-web's controllers, {@code
 * cancel_zombie_pipeline} carries that exact same annotation directly, replicating gate-web's
 * enforcement rather than inheriting it.
 */
public class AdminTools {

  private final OrcaServiceSelector orcaServiceSelector;
  private final McpAccessGuard accessGuard;

  public AdminTools(OrcaServiceSelector orcaServiceSelector, McpAccessGuard accessGuard) {
    this.orcaServiceSelector = orcaServiceSelector;
    this.accessGuard = accessGuard;
  }

  @McpTool(
      name = "cancel_zombie_pipeline",
      description =
          "Admin-only: force-cancel an execution that's stuck in a running state and won't respond to a normal "
              + "cancel_pipeline call (a 'zombie' execution - see the Spinnaker runbook on orca zombie executions). "
              + "Requires Fiat admin privileges, regardless of application-level permissions.")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public void cancelZombiePipeline(
      @McpToolParam(description = "The execution id to force-cancel", required = true)
          String executionId,
      @McpToolParam(
              description =
                  "Execution type: 'PIPELINE' or 'ORCHESTRATION'. Defaults to 'PIPELINE'.",
              required = false)
          String executionType) {
    accessGuard.requireWriteAccess("cancel_zombie_pipeline", executionId);

    String canceledBy = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous");
    Retrofit2SyncCall.execute(
        orcaServiceSelector
            .select()
            .forceCancelPipeline(
                executionId, executionType != null ? executionType : "PIPELINE", canceledBy));
  }
}
