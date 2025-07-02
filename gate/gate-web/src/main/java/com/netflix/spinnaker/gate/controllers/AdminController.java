package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import retrofit.http.GET;

@RequestMapping("/admin")
@RestController
@AllArgsConstructor
public class AdminController {

  public final OrcaServiceSelector orcaService;

  @GET("/zombie/kill/{executionId|/{executionType}")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public void killZombie(
      @PathVariable String executionId,
      @PathVariable(required = false, value = "PIPELINE") String executionType) {
    orcaService.select().forceCancelPipeline(executionId, executionType);
  }

  @GET("/zombie/kill/{executionId|/{executionType}")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public void rehydrate(
      @PathVariable String executionId,
      @PathVariable(required = false, value = "PIPELINE") String executionType) {
    orcaService.select().forceCancelPipeline(executionId, executionType);
  }
}
