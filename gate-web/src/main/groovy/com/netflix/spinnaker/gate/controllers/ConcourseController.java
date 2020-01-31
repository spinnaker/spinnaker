/*
 * Copyright 2019 Pivotal Inc.
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

package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.internal.IgorService;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/concourse")
@RequiredArgsConstructor
public class ConcourseController {

  private final Optional<IgorService> igorService;
  private final OrcaServiceSelector orcaService;

  @ApiOperation(
      value = "Retrieve the list of team names available to triggers",
      response = List.class)
  @GetMapping(value = "/{buildMaster}/teams")
  List<String> teams(@PathVariable("buildMaster") String buildMaster) {
    return igorService.get().getConcourseTeams(buildMaster);
  }

  @ApiOperation(
      value = "Retrieve the list of pipeline names for a given team available to triggers",
      response = List.class)
  @GetMapping(value = "/{buildMaster}/teams/{team}/pipelines")
  List<String> pipelines(
      @PathVariable("buildMaster") String buildMaster, @PathVariable("team") String team) {
    return igorService.get().getConcoursePipelines(buildMaster, team);
  }

  @ApiOperation(
      value = "Retrieve the list of job names for a given pipeline available to triggers",
      response = List.class)
  @GetMapping(value = "/{buildMaster}/teams/{team}/pipelines/{pipeline}/jobs")
  List<String> jobs(
      @PathVariable("buildMaster") String buildMaster,
      @PathVariable("team") String team,
      @PathVariable("pipeline") String pipeline) {
    return igorService.get().getConcourseJobs(buildMaster, team, pipeline);
  }

  @ApiOperation(
      value =
          "Retrieve the list of resource names for a given pipeline available to the Concourse stage",
      response = List.class)
  @GetMapping(value = "/{buildMaster}/teams/{team}/pipelines/{pipeline}/resources")
  List<String> resources(
      @PathVariable("buildMaster") String buildMaster,
      @PathVariable("team") String team,
      @PathVariable("pipeline") String pipeline) {
    return igorService.get().getConcourseResources(buildMaster, team, pipeline);
  }

  @ApiOperation(
      value =
          "Inform Spinnaker of the Concourse build running connected to a particular Concourse stage execution")
  @PostMapping("/stage/start")
  void stageExecution(
      @RequestParam("stageId") String stageId,
      @RequestParam("job") String job,
      @RequestParam("buildNumber") Integer buildNumber) {
    orcaService.select().concourseStageExecution(stageId, job, buildNumber, "");
  }
}
