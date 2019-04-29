/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.igor.scm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public abstract class AbstractCommitController {
  private static final Logger log = LoggerFactory.getLogger(AbstractCommitController.class);
  protected ExecutorService executor;
  protected ObjectMapper objectMapper;

  @ResponseStatus(
      value = HttpStatus.BAD_REQUEST,
      reason = "toCommit and fromCommit parameters are required in the query string")
  public static class MissingParametersException extends RuntimeException {}

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/{projectKey}/{repositorySlug}/compareCommits")
  public List<Map<String, Object>> compareCommits(
      @PathVariable String projectKey,
      @PathVariable String repositorySlug,
      @RequestParam Map<String, String> requestParams) {
    if (!requestParams.containsKey("to") || !requestParams.containsKey("from")) {
      throw new MissingParametersException();
    }

    return Collections.emptyList();
  }

  public List<Map<String, Object>> getNotFoundCommitsResponse(
      String projectKey, String repositorySlug, String to, String from, String url) {
    Map<String, Object> eMap = new HashMap<>();
    eMap.put("displayId", "NOT_FOUND");
    eMap.put("id", "NOT_FOUND");
    eMap.put("authorDisplayName", "UNKNOWN");
    eMap.put("timestamp", Instant.now());
    eMap.put(
        "message",
        String.format(
            "could not find any commits from %s to %s in %s %s/%s",
            from, to, url, projectKey, repositorySlug));
    eMap.put("commitUrl", url);
    return Collections.singletonList(eMap);
  }

  @Autowired
  public void setExecutor(ExecutorService executor) {
    this.executor = executor;
  }

  @Autowired
  public void setObjectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }
}
