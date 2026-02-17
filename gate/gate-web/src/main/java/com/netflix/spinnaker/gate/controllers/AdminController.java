package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/admin")
@RestController
@AllArgsConstructor
@Log4j2
public class AdminController {

  public final OrcaServiceSelector orcaService;
  private FiatPermissionEvaluator fiatPermissionEvaluator;

  /**
   * VERY simple endpoint that returns whether the user is an "admin" or not. This enables the UI to
   * show/hide certain buttons and such potentially. NOMINALLY this would be part of the "user" vs.
   * a separate endpoint, but for the moment, it's here to make a fast separate admin check for UI
   * calls.
   *
   * @return
   */
  @GetMapping
  public Map<String, ?> adminOrNot() {
    return Map.of(
        "username",
        SecurityContextHolder.getContext().getAuthentication().getName(),
        "isAdmin",
        fiatPermissionEvaluator.isAdmin());
  }

  /**
   * Note this targets the orca endpoint /admin/forceCancelExecution. See <a
   * href="https://spinnaker.io/docs/guides/runbooks/orca-zombie-executions/">Spinnaker docs</a> for
   * how this was done in the past - which this now exposes to "Admin" users. The one interesting
   * aspect internally is there's ANOTHER endpoint that's on the "QueueAdminController.kt" that does
   * the same thing, but it's not operating QUITE the same way and might be a better long term call
   * for this. At this point in time we'll use the documented API vs. the other API. TODO: Evaluate
   * whether we should change and cleanup the orca api's around execution handling.
   *
   * @param executionId Specific execution ID to be killed
   * @param executionType PIPELINE or ORCHESTRATION. Defaults to PIPELINE if not set.
   */
  @Operation(summary = "Admin endpoint to force cancel an execution")
  @PutMapping(value = "/executions/forceCancel")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public void killZombie(
      @Parameter(description = "The execution id of the specific pipeline")
          @RequestParam(value = "executionId")
          String executionId,
      @Parameter(description = "The type of execution, either PIPELINE or ORCHESTRATION.")
          @RequestParam(value = "executionType", required = false)
          String executionType) {
    String username = SecurityContextHolder.getContext().getAuthentication().getName();
    log.info("Force killing pipeline with execution id {} by user {}", executionId, username);
    try {
      orcaService
          .select()
          .forceCancelPipeline(
              executionId, Optional.ofNullable(executionType).orElse("PIPELINE"), username)
          .execute();

    } catch (Exception e) {
      throw new SpinnakerException(
          "Error invoking killing of the zombie pipeline!  Check logs - particularly on orca for more information",
          e);
    }
  }

  /**
   * Note this targets the orca endpoint /admin/queue/hydrate. See <a
   * href="https://spinnaker.io/docs/guides/runbooks/orca-zombie-executions/">Spinnaker docs</a> for
   * how this was done in the past - which this now exposes to "Admin" users. This does a "hydrate"
   * aka it tries to reput the execution in the queue to allow it to be run. See the code in
   * "QueueAdminController.kt" for more information.
   *
   * <p>NOTE dryRun defaults to "false" aka it WILL NOT do a dry run by default. it has to be an
   * explicit "true" to do a dry run.
   *
   * @return Map of the execution data. See docs for example responses
   * @param executionId Specific execution ID to be killed
   * @param dryRun true/false to do a dry run instead of actually hydrating the execution. Defaults
   *     to false.
   */
  @PostMapping("/executions/hydrate")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public Map rehydrate(
      @Parameter(description = "The execution id of the specific pipeline to rehydrate.")
          @RequestParam(value = "executionId")
          String executionId,
      @Parameter(
              description =
                  "Do a dry run instead of actually hydrating the execution into the queue")
          @RequestParam(required = false)
          String dryRun) {
    String userName = SecurityContextHolder.getContext().getAuthentication().getName();
    log.info("Rehydrating execution id {} by user {}", executionId, userName);
    try {
      return orcaService
          .select()
          .rehydrateExecution(executionId, Boolean.parseBoolean(dryRun))
          .execute()
          .body();
    } catch (IOException e) {
      throw new SpinnakerException("Error invoking hydration of queue!", e);
    }
  }
}
