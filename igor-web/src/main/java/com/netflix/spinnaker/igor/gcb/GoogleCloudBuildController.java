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
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import retrofit.http.Query;

@ConditionalOnProperty("gcb.enabled")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/gcb")
public class GoogleCloudBuildController {
  private final GoogleCloudBuildAccountRepository googleCloudBuildAccountRepository;
  private final GoogleCloudBuildParser googleCloudBuildParser;

  @RequestMapping(value = "/accounts", method = RequestMethod.GET)
  List<String> getAccounts() {
    return googleCloudBuildAccountRepository.getAccounts();
  }

  @RequestMapping(
      value = "/builds/create/{account}",
      method = RequestMethod.POST,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  Build createBuild(@PathVariable String account, @RequestBody String buildString) {
    Build build = googleCloudBuildParser.parse(buildString, Build.class);
    return googleCloudBuildAccountRepository.getGoogleCloudBuild(account).createBuild(build);
  }

  @RequestMapping(
      value = "/builds/{account}/{buildId}",
      method = RequestMethod.PUT,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  void updateBuild(
      @PathVariable String account,
      @PathVariable String buildId,
      @Query("status") String status,
      @RequestBody String serializedBuild) {
    googleCloudBuildAccountRepository
        .getGoogleCloudBuild(account)
        .updateBuild(buildId, status, serializedBuild);
  }

  @RequestMapping(value = "/builds/{account}/{buildId}", method = RequestMethod.GET)
  Build getBuild(@PathVariable String account, @PathVariable String buildId) {
    return googleCloudBuildAccountRepository.getGoogleCloudBuild(account).getBuild(buildId);
  }

  @RequestMapping(value = "/builds/{account}/{buildId}/artifacts", method = RequestMethod.GET)
  List<Artifact> getArtifacts(@PathVariable String account, @PathVariable String buildId) {
    return googleCloudBuildAccountRepository.getGoogleCloudBuild(account).getArtifacts(buildId);
  }

  @RequestMapping(
      value = "/artifacts/extract/{account}",
      method = RequestMethod.PUT,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  List<Artifact> extractArtifacts(
      @PathVariable String account, @RequestBody String serializedBuild) {
    Build build = googleCloudBuildParser.parse(serializedBuild, Build.class);
    return googleCloudBuildAccountRepository.getGoogleCloudBuild(account).extractArtifacts(build);
  }
}
