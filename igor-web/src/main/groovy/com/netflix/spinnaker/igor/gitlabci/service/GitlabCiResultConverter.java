/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.igor.gitlabci.service;

import com.netflix.spinnaker.igor.build.model.Result;
import com.netflix.spinnaker.igor.gitlabci.client.model.PipelineStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitlabCiResultConverter {
  private static Logger log = LoggerFactory.getLogger(GitlabCiPipelineUtils.class);

  public static Result getResultFromGitlabCiState(PipelineStatus status) {
    switch (status) {
      case pending:
        return Result.NOT_BUILT;
      case running:
        return Result.BUILDING;
      case success:
        return Result.SUCCESS;
      case canceled:
        return Result.ABORTED;
      case failed:
        return Result.FAILURE;
      case skipped:
        return Result.NOT_BUILT;
      default:
        log.info("could not convert " + String.valueOf(status));
        throw new IllegalArgumentException("status: " + String.valueOf(status) + " is not known");
    }
  }

  public static boolean running(PipelineStatus status) {
    switch (status) {
      case pending:
      case running:
        return true;
      default:
        return false;
    }
  }
}
