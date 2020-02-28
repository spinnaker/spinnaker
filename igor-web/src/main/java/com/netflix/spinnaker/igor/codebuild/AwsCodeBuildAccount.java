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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.codebuild.AWSCodeBuildClient;
import com.amazonaws.services.codebuild.AWSCodeBuildClientBuilder;
import com.amazonaws.services.codebuild.model.BatchGetBuildsRequest;
import com.amazonaws.services.codebuild.model.Build;
import com.amazonaws.services.codebuild.model.BuildArtifacts;
import com.amazonaws.services.codebuild.model.ListProjectsRequest;
import com.amazonaws.services.codebuild.model.ListProjectsResult;
import com.amazonaws.services.codebuild.model.ProjectSortByType;
import com.amazonaws.services.codebuild.model.StartBuildRequest;
import com.amazonaws.services.codebuild.model.StopBuildRequest;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** Generates authenticated requests to AWS CodeBuild API for a single configured account */
@RequiredArgsConstructor
public class AwsCodeBuildAccount {
  private final AWSCodeBuildClient client;

  public AwsCodeBuildAccount(AWSCredentialsProvider credentialsProvider, String region) {
    // TODO: Add client-side rate limiting to avoid getting throttled if necessary
    this.client =
        (AWSCodeBuildClient)
            AWSCodeBuildClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRequestHandlers(new AwsCodeBuildRequestHandler())
                .withRegion(region)
                .build();
  }

  // The number of projects has an upper limit of 5000 per region per account
  // P99 latency could be high if user has more than 1000 projects
  public List<String> getProjects() {
    List<String> projects = new ArrayList<>();
    String nextToken = null;

    do {
      ListProjectsResult result =
          client.listProjects(
              new ListProjectsRequest()
                  .withSortBy(ProjectSortByType.NAME)
                  .withNextToken(nextToken));
      projects.addAll(result.getProjects());
      nextToken = result.getNextToken();
    } while (nextToken != null);

    return projects;
  }

  public Build startBuild(StartBuildRequest request) {
    return client.startBuild(request).getBuild();
  }

  public Build getBuild(String buildId) {
    return client.batchGetBuilds(new BatchGetBuildsRequest().withIds(buildId)).getBuilds().get(0);
  }

  public List<Artifact> getArtifacts(String buildId) {
    Build build = getBuild(buildId);
    return extractArtifactsFromBuild(build);
  }

  public Build stopBuild(String buildId) {
    return client.stopBuild(new StopBuildRequest().withId(buildId)).getBuild();
  }

  private List<Artifact> extractArtifactsFromBuild(Build build) {
    ArrayList<Artifact> artifactsList = new ArrayList<>();
    BuildArtifacts primaryArtifacts = build.getArtifacts();
    List<BuildArtifacts> secondaryArtifacts = build.getSecondaryArtifacts();
    if (primaryArtifacts != null) {
      artifactsList.add(getS3Artifact(primaryArtifacts.getLocation()));
    }
    if (secondaryArtifacts != null) {
      secondaryArtifacts.forEach(
          artifacts -> artifactsList.add(getS3Artifact(artifacts.getLocation())));
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
