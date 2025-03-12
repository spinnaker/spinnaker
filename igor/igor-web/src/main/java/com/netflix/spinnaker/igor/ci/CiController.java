/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.igor.ci;

import com.netflix.spinnaker.igor.build.model.GenericBuild;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty("services.ci.enabled")
@RequestMapping("/ci")
public class CiController {

  private Optional<CiBuildService> ciBuildService;

  @Autowired
  public CiController(Optional<CiBuildService> ciBuildService) {
    this.ciBuildService = ciBuildService;
  }

  @GetMapping("/builds")
  public List<GenericBuild> getBuilds(
      @RequestParam(value = "projectKey", required = false) String projectKey,
      @RequestParam(value = "repoSlug", required = false) String repoSlug,
      @RequestParam(value = "buildNumber", required = false) String buildNumber,
      @RequestParam(value = "commitId", required = false) String commitId,
      @RequestParam(value = "completionStatus", required = false) String completionStatus) {
    return getCiService().getBuilds(projectKey, repoSlug, buildNumber, commitId, completionStatus);
  }

  @GetMapping("/builds/{buildId}/output")
  public Map<String, Object> getBuildOutput(@PathVariable(value = "buildId") String buildId) {
    return getCiService().getBuildOutput(buildId);
  }

  private CiBuildService getCiService() {
    if (ciBuildService.isPresent()) {
      return ciBuildService.get();
    } else {
      throw new UnsupportedOperationException("No CI service exists");
    }
  }
}
