package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/admin")
@RestController
@AllArgsConstructor
@Log4j2
public class AdminController {

  public final OrcaServiceSelector orcaService;
  private FiatPermissionEvaluator fiatPermissionEvaluator;

  @GetMapping
  public Map<String, ?> adminOrNot() {
    return Map.of(
        "username",
        SecurityContextHolder.getContext().getAuthentication().getName(),
        "isAdmin",
        fiatPermissionEvaluator.isAdmin());
  }

  @PostMapping("/zombie/kill/{executionId}/{executionType}")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public Map killZombie(
      @PathVariable String executionId, @PathVariable(required = false) String executionType) {
    String username = SecurityContextHolder.getContext().getAuthentication().getName();
    log.info("Force killing pipeline with execution id " + executionId + " by user " + username);
    try {
      return orcaService
          .select()
          .forceCancelPipeline(
              executionId, Optional.ofNullable(executionType).orElse("PIPELINE"), username)
          .execute()
          .body();

    } catch (Exception e) {
      throw new SpinnakerException("Error invoking hydration of queue!", e);
    }
  }

  @PostMapping("/zombie/hydrate/{executionId}/{dryRun}")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public Map rehydrate(
      @PathVariable String executionId, @PathVariable(required = false) String dryRun) {
    String userName = SecurityContextHolder.getContext().getAuthentication().getName();
    log.info("Rehydrating execution with id " + executionId + " invoked by user " + userName);
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
