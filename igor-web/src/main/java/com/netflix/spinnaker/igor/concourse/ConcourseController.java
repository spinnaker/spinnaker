/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.concourse;

import static java.util.Collections.emptyList;

import com.netflix.spinnaker.igor.concourse.client.model.Job;
import com.netflix.spinnaker.igor.concourse.client.model.Pipeline;
import com.netflix.spinnaker.igor.concourse.client.model.Team;
import com.netflix.spinnaker.igor.concourse.service.ConcourseService;
import com.netflix.spinnaker.igor.service.BuildServices;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty("concourse.enabled")
@RequestMapping("/concourse")
public class ConcourseController {
  private final BuildServices buildServices;

  public ConcourseController(BuildServices buildServices) {
    this.buildServices = buildServices;
  }

  @GetMapping("/{buildMaster}/teams")
  public List<String> getTeams(@PathVariable("buildMaster") String buildMaster) {
    return getService(buildMaster)
        .map(
            service ->
                service.teams().stream().map(Team::getName).sorted().collect(Collectors.toList()))
        .orElse(emptyList());
  }

  @GetMapping("/{buildMaster}/teams/{team}/pipelines")
  public List<String> getPipelines(
      @PathVariable("buildMaster") String buildMaster, @PathVariable("team") String team) {
    return getService(buildMaster)
        .map(
            service ->
                service.pipelines().stream()
                    .filter(p -> team.equals(p.getTeamName()))
                    .map(Pipeline::getName)
                    .sorted()
                    .collect(Collectors.toList()))
        .orElse(emptyList());
  }

  @GetMapping("/{buildMaster}/teams/{team}/pipelines/{pipeline}/jobs")
  public List<String> getJobs(
      @PathVariable("buildMaster") String buildMaster,
      @PathVariable("team") String team,
      @PathVariable("pipeline") String pipeline) {
    // don't sort so that we leave the jobs in the order in which they appear in the pipeline
    return getService(buildMaster)
        .map(
            service ->
                service.getJobs().stream()
                    .filter(
                        j -> team.equals(j.getTeamName()) && pipeline.equals(j.getPipelineName()))
                    .map(Job::getName)
                    .collect(Collectors.toList()))
        .orElse(emptyList());
  }

  @GetMapping("/{buildMaster}/teams/{team}/pipelines/{pipeline}/resources")
  public List<String> getResourceNames(
      @PathVariable("buildMaster") String buildMaster,
      @PathVariable("team") String team,
      @PathVariable("pipeline") String pipeline) {
    return getService(buildMaster)
        .map(
            service ->
                service.getResourceNames(team, pipeline).stream()
                    .sorted()
                    .collect(Collectors.toList()))
        .orElse(emptyList());
  }

  private Optional<ConcourseService> getService(String buildMaster) {
    return Optional.ofNullable((ConcourseService) buildServices.getService(buildMaster));
  }
}
