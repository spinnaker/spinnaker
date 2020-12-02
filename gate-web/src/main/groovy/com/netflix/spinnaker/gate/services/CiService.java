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

package com.netflix.spinnaker.gate.services;

import com.netflix.spinnaker.gate.services.internal.IgorService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CiService {

  private Optional<IgorService> igorService;

  @Autowired
  private CiService(Optional<IgorService> igorService) {
    this.igorService = igorService;
  }

  public List<Map<String, Object>> getBuilds(
      String projectKey,
      String repoSlug,
      String completionStatus,
      String buildNumber,
      String commitId) {
    if (!igorService.isPresent()) {
      throw new UnsupportedOperationException(
          "Operation not supported because igor service is not configured");
    }
    return igorService
        .get()
        .getBuilds(projectKey, repoSlug, completionStatus, buildNumber, commitId);
  }

  public Map<String, Object> getBuildOutput(String buildId) {
    if (!igorService.isPresent()) {
      throw new UnsupportedOperationException(
          "Operation not supported because igor service is not configured");
    }
    return igorService.get().getBuildOutput(buildId);
  }
}
