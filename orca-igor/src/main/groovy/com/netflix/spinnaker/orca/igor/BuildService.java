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
package com.netflix.spinnaker.orca.igor;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BuildService {
  private final IgorService igorService;

  private String encode(String uri) {
    try {
      return UriUtils.encodeFragment(uri, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  public String build(String master, String jobName, Map<String, String> queryParams) {
    return igorService.build(master, encode(jobName), queryParams, "");
  }

  public String stop(String master, String jobName, String queuedBuild, Integer buildNumber) {
    return igorService.stop(master, jobName, queuedBuild, buildNumber, "");
  }

  public Map queuedBuild(String master, String item) {
    return igorService.queuedBuild(master, item);
  }

  public Map<String, Object> getBuild(Integer buildNumber, String master, String job) {
    return igorService.getBuild(buildNumber, master, encode(job));
  }

  public Map<String, Object> getPropertyFile(Integer buildNumber, String fileName, String master, String job) {
    return igorService.getPropertyFile(buildNumber, fileName, master, encode(job));
  }

  public List<Artifact> getArtifacts(Integer buildNumber, String fileName, String master, String job) {
    return igorService.getArtifacts(buildNumber, fileName, master, encode(job));
  }

  public List compareCommits(String repoType, String projectKey, String repositorySlug, Map<String, String> requestParams) {
    return igorService.compareCommits(repoType, projectKey, repositorySlug, requestParams);
  }
}
