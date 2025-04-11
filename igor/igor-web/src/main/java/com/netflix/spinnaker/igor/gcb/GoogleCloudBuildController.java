/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.igor.gcb;

import com.google.api.services.cloudbuild.v1.model.Build;
import com.google.api.services.cloudbuild.v1.model.BuildTrigger;
import com.google.api.services.cloudbuild.v1.model.RepoSource;
import com.google.common.collect.Lists;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import retrofit2.http.Query;

@ConditionalOnProperty("gcb.enabled")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/gcb")
public class GoogleCloudBuildController {
  private final GoogleCloudBuildAccountRepository googleCloudBuildAccountRepository;
  private final GoogleCloudBuildParser googleCloudBuildParser;

  @RequestMapping(value = "/accounts", method = RequestMethod.GET)
  @PostFilter("hasPermission(filterObject, 'BUILD_SERVICE', 'READ')")
  public List<String> getAccounts() {
    return Lists.newArrayList(googleCloudBuildAccountRepository.getAccounts());
  }

  @RequestMapping(
      value = "/builds/create/{account}",
      method = RequestMethod.POST,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasPermission(#account, 'BUILD_SERVICE', 'WRITE')")
  public Build createBuild(@PathVariable String account, @RequestBody String buildString) {
    Build build = googleCloudBuildParser.parse(buildString, Build.class);
    return googleCloudBuildAccountRepository.getGoogleCloudBuild(account).createBuild(build);
  }

  @RequestMapping(
      value = "/builds/{account}/{buildId}",
      method = RequestMethod.PUT,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public void updateBuild(
      @PathVariable String account,
      @PathVariable String buildId,
      @Query("status") String status,
      @RequestBody String serializedBuild) {
    googleCloudBuildAccountRepository
        .getGoogleCloudBuild(account)
        .updateBuild(buildId, status, serializedBuild);
  }

  @RequestMapping(value = "/builds/{account}/{buildId}", method = RequestMethod.GET)
  @PreAuthorize("hasPermission(#account, 'BUILD_SERVICE', 'READ')")
  public Build getBuild(@PathVariable String account, @PathVariable String buildId) {
    return googleCloudBuildAccountRepository.getGoogleCloudBuild(account).getBuild(buildId);
  }

  @RequestMapping(value = "/builds/{account}/{buildId}/artifacts", method = RequestMethod.GET)
  @PreAuthorize("hasPermission(#account, 'BUILD_SERVICE', 'READ')")
  public List<Artifact> getArtifacts(@PathVariable String account, @PathVariable String buildId) {
    return Lists.newArrayList(
        googleCloudBuildAccountRepository.getGoogleCloudBuild(account).getArtifacts(buildId));
  }

  @RequestMapping(
      value = "/artifacts/extract/{account}",
      method = RequestMethod.PUT,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public List<Artifact> extractArtifacts(
      @PathVariable String account, @RequestBody String serializedBuild) {
    Build build = googleCloudBuildParser.parse(serializedBuild, Build.class);
    return Lists.newArrayList(
        googleCloudBuildAccountRepository.getGoogleCloudBuild(account).extractArtifacts(build));
  }

  @RequestMapping(value = "/triggers/{account}", method = RequestMethod.GET)
  @PreAuthorize("hasPermission(#account, 'BUILD_SERVICE', 'READ')")
  public List<BuildTrigger> listTriggers(@PathVariable String account) {
    return Lists.newArrayList(
        googleCloudBuildAccountRepository.getGoogleCloudBuild(account).listTriggers());
  }

  @RequestMapping(
      value = "/triggers/{account}/{triggerId}/run",
      method = RequestMethod.POST,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasPermission(#account, 'BUILD_SERVICE', 'WRITE')")
  public Build runTrigger(
      @PathVariable String account,
      @PathVariable String triggerId,
      @RequestBody String repoSourceString) {
    RepoSource repoSource = googleCloudBuildParser.parse(repoSourceString, RepoSource.class);
    return googleCloudBuildAccountRepository
        .getGoogleCloudBuild(account)
        .runTrigger(triggerId, repoSource);
  }

  @RequestMapping(value = "/builds/stop/{account}/{buildId}", method = RequestMethod.POST)
  Build stopBuild(@PathVariable String account, @PathVariable String buildId) {
    return googleCloudBuildAccountRepository.getGoogleCloudBuild(account).stopBuild(buildId);
  }
}
