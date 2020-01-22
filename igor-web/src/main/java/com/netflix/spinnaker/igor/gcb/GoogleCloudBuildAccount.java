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
import com.google.api.services.cloudbuild.v1.model.ListBuildTriggersResponse;
import com.google.api.services.cloudbuild.v1.model.Operation;
import com.google.api.services.cloudbuild.v1.model.RepoSource;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Handles getting and updating build information for a single account. Delegates operations to
 * either the GoogleCloudBuildCache or GoogleCloudBuildClient.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class GoogleCloudBuildAccount {
  private static final String BUILD_TAG = "started-by.spinnaker.io";

  private final GoogleCloudBuildClient client;
  private final GoogleCloudBuildCache cache;
  private final GoogleCloudBuildParser googleCloudBuildParser;
  private final GoogleCloudBuildArtifactFetcher googleCloudBuildArtifactFetcher;

  Build createBuild(Build buildRequest) {
    appendTags(buildRequest);
    Operation operation = client.createBuild(buildRequest);
    Build buildResponse =
        googleCloudBuildParser.convert(operation.getMetadata().get("build"), Build.class);
    this.updateBuild(
        buildResponse.getId(),
        buildResponse.getStatus(),
        googleCloudBuildParser.serialize(buildResponse));
    return buildResponse;
  }

  private void appendTags(Build build) {
    List<String> tags = Optional.ofNullable(build.getTags()).orElseGet(ArrayList::new);
    if (!tags.contains(BUILD_TAG)) {
      tags.add(BUILD_TAG);
    }
    build.setTags(tags);
  }

  void updateBuild(String buildId, @Nullable String status, String serializedBuild) {
    cache.updateBuild(buildId, status, serializedBuild);
  }

  Build getBuild(String buildId) {
    String buildString = cache.getBuild(buildId);
    if (buildString == null) {
      Build build = client.getBuild(buildId);
      buildString = googleCloudBuildParser.serialize(build);
      this.updateBuild(buildId, build.getStatus(), buildString);
    }
    return googleCloudBuildParser.parse(buildString, Build.class);
  }

  ImmutableList<BuildTrigger> listTriggers() {
    ListBuildTriggersResponse listBuildTriggersResponse = client.listTriggers();
    return ImmutableList.copyOf(listBuildTriggersResponse.getTriggers());
  }

  Build runTrigger(String triggerId, RepoSource repoSource) {
    Operation operation = client.runTrigger(triggerId, repoSource);
    Build triggerResponse =
        googleCloudBuildParser.convert(operation.getMetadata().get("build"), Build.class);
    this.updateBuild(
        triggerResponse.getId(),
        triggerResponse.getStatus(),
        googleCloudBuildParser.serialize(triggerResponse));
    return triggerResponse;
  }

  ImmutableList<Artifact> getArtifacts(String buildId) {
    Build build = getBuild(buildId);
    return googleCloudBuildArtifactFetcher.getArtifacts(build);
  }

  ImmutableList<Artifact> extractArtifacts(Build build) {
    return googleCloudBuildArtifactFetcher.getArtifacts(build);
  }
}
