package com.netflix.spinnaker.orca.controllers;

import com.netflix.spinnaker.orca.igor.ConcourseService;
import com.netflix.spinnaker.orca.igor.model.ConcourseStageExecution;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
public class ConcourseController {
  private ConcourseService concourseService;

  public ConcourseController(Optional<ConcourseService> concourseService) {
    this.concourseService = concourseService.orElse(null);
  }

  /**
   * Since there isn't a way to trigger a Concourse job directly with parameterization,
   * Concourse instead monitors Spinnaker pipelines for a running Concourse stage.
   * Concourse then informs Spinnaker of the Concourse build that is running for that
   * stage so Spinnaker can monitor its completion.
   */
  @PostMapping("/concourse/stage/start")
  public void notifyConcourseExecution(@RequestParam("stageId") String stageId,
                                       @RequestParam("job") String job,
                                       @RequestParam("buildNumber") Integer buildNumber) {
    concourseService.pushExecution(new ConcourseStageExecution(stageId, job, buildNumber));
  }
}
