/*
 * Copyright 2020 Amazon.com, Inc.
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

package com.netflix.spinnaker.igor.codebuild;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.codebuild.CodeBuildClient;
import software.amazon.awssdk.services.codebuild.model.BatchGetBuildsRequest;
import software.amazon.awssdk.services.codebuild.model.Build;
import software.amazon.awssdk.services.codebuild.model.BuildArtifacts;
import software.amazon.awssdk.services.codebuild.model.ListProjectsRequest;
import software.amazon.awssdk.services.codebuild.model.ListProjectsResponse;
import software.amazon.awssdk.services.codebuild.model.ProjectSortByType;
import software.amazon.awssdk.services.codebuild.model.StartBuildRequest;
import software.amazon.awssdk.services.codebuild.model.StopBuildRequest;

/** Generates authenticated requests to AWS CodeBuild API for a single configured account */
@RequiredArgsConstructor
public class AwsCodeBuildAccount {
  private final CodeBuildClient client;

  public AwsCodeBuildAccount(AwsCredentialsProvider credentialsProvider, String region) {
    // TODO: Add client-side rate limiting to avoid getting throttled if necessary
    this.client =
        CodeBuildClient.builder()
            .credentialsProvider(credentialsProvider)
            .region(Region.of(region))
            .overrideConfiguration(
                cfg -> cfg.addExecutionInterceptor(new AwsCodeBuildRequestInterceptor()))
            .build();
  }

  // The number of projects has an upper limit of 5000 per region per account
  // P99 latency could be high if user has more than 1000 projects
  public List<String> getProjects() {
    List<String> projects = new ArrayList<>();
    String nextToken = null;

    do {
      ListProjectsRequest.Builder requestBuilder =
          ListProjectsRequest.builder().sortBy(ProjectSortByType.NAME);
      if (nextToken != null) {
        requestBuilder.nextToken(nextToken);
      }
      ListProjectsResponse result = client.listProjects(requestBuilder.build());
      projects.addAll(result.projects());
      nextToken = result.nextToken();
    } while (nextToken != null);

    return projects;
  }

  public Build startBuild(StartBuildRequest request) {
    return client.startBuild(request).build();
  }

  public Build getBuild(String buildId) {
    BatchGetBuildsRequest request = BatchGetBuildsRequest.builder().ids(buildId).build();
    return client.batchGetBuilds(request).builds().get(0);
  }

  public List<Artifact> getArtifacts(String buildId) {
    Build build = getBuild(buildId);
    return extractArtifactsFromBuild(build);
  }

  public Build stopBuild(String buildId) {
    StopBuildRequest request = StopBuildRequest.builder().id(buildId).build();
    return client.stopBuild(request).build();
  }

  private List<Artifact> extractArtifactsFromBuild(Build build) {
    ArrayList<Artifact> artifactsList = new ArrayList<>();
    BuildArtifacts primaryArtifacts = build.artifacts();
    List<BuildArtifacts> secondaryArtifacts = build.secondaryArtifacts();
    if (primaryArtifacts != null) {
      artifactsList.add(getS3Artifact(primaryArtifacts.location()));
    }
    if (secondaryArtifacts != null) {
      secondaryArtifacts.forEach(
          artifacts -> artifactsList.add(getS3Artifact(artifacts.location())));
    }
    return artifactsList;
  }

  private Artifact getS3Artifact(String s3Arn) {
    String reference = getS3ArtifactReference(s3Arn);
    return Artifact.builder().type("s3/object").reference(reference).name(reference).build();
  }

  private String getS3ArtifactReference(String s3Arn) {
    String[] s3ArnSplit = s3Arn.split(":");
    return "s3://" + s3ArnSplit[s3ArnSplit.length - 1];
  }
}
