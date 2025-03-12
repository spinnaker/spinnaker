/*
 * Copyright 2020 Amazon.com, Inc.
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

package com.netflix.spinnaker.igor.codebuild

import com.amazonaws.services.codebuild.AWSCodeBuildClient
import com.amazonaws.services.codebuild.model.BatchGetBuildsRequest
import com.amazonaws.services.codebuild.model.BatchGetBuildsResult
import com.amazonaws.services.codebuild.model.Build
import com.amazonaws.services.codebuild.model.BuildArtifacts
import com.amazonaws.services.codebuild.model.ListProjectsRequest
import com.amazonaws.services.codebuild.model.ListProjectsResult
import com.amazonaws.services.codebuild.model.ProjectSortByType
import com.amazonaws.services.codebuild.model.StartBuildRequest
import com.amazonaws.services.codebuild.model.StartBuildResult
import spock.lang.Specification

class AwsCodeBuildAccountSpec extends Specification {
  AWSCodeBuildClient client = Mock(AWSCodeBuildClient)
  AwsCodeBuildAccount awsCodeBuildAccount = new AwsCodeBuildAccount(client)

  def "startBuild starts a build and returns the result"() {
    given:
    def inputRequest = getStartBuildInput("test")
    def mockOutputBuild = getOutputBuild("test")

    when:
    def result = awsCodeBuildAccount.startBuild(inputRequest)

    then:
    1 * client.startBuild(inputRequest) >> new StartBuildResult().withBuild(mockOutputBuild)
    result == mockOutputBuild
  }

  def "getBuild returns the build details"() {
    given:
    String buildId = "test:c7715bbf-5c12-44d6-87ef-8149473e02f7"
    def inputRequest = getBatchGetBuildsInput(buildId)
    def mockOutputBuild = getOutputBuild("test")

    when:
    def result = awsCodeBuildAccount.getBuild(buildId)

    then:
    1 * client.batchGetBuilds(inputRequest) >> new BatchGetBuildsResult().withBuilds(mockOutputBuild)
    result == mockOutputBuild
  }

  def "getArtifacts returns empty list if no artifact exists"() {
    given:
    String buildId = "test:c7715bbf-5c12-44d6-87ef-8149473e02f7"
    def inputRequest = getBatchGetBuildsInput(buildId)
    def mockOutputBuild = getOutputBuild("test")

    when:
    def result = awsCodeBuildAccount.getArtifacts(buildId)

    then:
    1 * client.batchGetBuilds(inputRequest) >> new BatchGetBuildsResult().withBuilds(mockOutputBuild)
    result.size() == 0
  }

  def "getArtifacts returns primary artifacts"() {
    given:
    String buildId = "test:c7715bbf-5c12-44d6-87ef-8149473e02f7"
    def inputRequest = getBatchGetBuildsInput(buildId)
    def mockOutputBuild = getOutputBuild("test")
    mockOutputBuild.setArtifacts(
      new BuildArtifacts()
        .withLocation("arn:aws:s3:::bucket/path/file.zip")
    )

    when:
    def result = awsCodeBuildAccount.getArtifacts(buildId)

    then:
    1 * client.batchGetBuilds(inputRequest) >> new BatchGetBuildsResult().withBuilds(mockOutputBuild)
    result.size() == 1
    result.get(0).getType() == "s3/object"
    result.get(0).getReference() == "s3://bucket/path/file.zip"
    result.get(0).getName() == "s3://bucket/path/file.zip"
  }

  def "getArtifacts returns secondary artifacts"() {
    given:
    String buildId = "test:c7715bbf-5c12-44d6-87ef-8149473e02f7"
    def inputRequest = getBatchGetBuildsInput(buildId)
    def mockOutputBuild = getOutputBuild("test")
    mockOutputBuild.setSecondaryArtifacts([
      new BuildArtifacts()
        .withLocation("arn:aws:s3:::bucket/path/file.zip"),
      new BuildArtifacts()
        .withLocation("arn:aws:s3:::another-bucket/another/path/file.zip"),
    ])

    when:
    def result = awsCodeBuildAccount.getArtifacts(buildId)

    then:
    1 * client.batchGetBuilds(inputRequest) >> new BatchGetBuildsResult().withBuilds(mockOutputBuild)
    result.size() == 2
    result.get(0).getType() == "s3/object"
    result.get(0).getReference() == "s3://bucket/path/file.zip"
    result.get(0).getName() == "s3://bucket/path/file.zip"
    result.get(1).getType() == "s3/object"
    result.get(1).getReference() == "s3://another-bucket/another/path/file.zip"
    result.get(1).getName() == "s3://another-bucket/another/path/file.zip"
  }

  def "getArtifacts returns both primary and secondary artifacts"() {
    given:
    String buildId = "test:c7715bbf-5c12-44d6-87ef-8149473e02f7"
    def inputRequest = getBatchGetBuildsInput(buildId)
    def mockOutputBuild = getOutputBuild("test")
    mockOutputBuild.setArtifacts(
      new BuildArtifacts()
        .withLocation("arn:aws:s3:::bucket/path/file.zip")
    )
    mockOutputBuild.setSecondaryArtifacts([
      new BuildArtifacts()
        .withLocation("arn:aws:s3:::another-bucket/another/path/file.zip")
    ])

    when:
    def result = awsCodeBuildAccount.getArtifacts(buildId)

    then:
    1 * client.batchGetBuilds(inputRequest) >> new BatchGetBuildsResult().withBuilds(mockOutputBuild)
    result.size() == 2
    result.get(0).getType() == "s3/object"
    result.get(0).getReference() == "s3://bucket/path/file.zip"
    result.get(0).getName() == "s3://bucket/path/file.zip"
    result.get(1).getType() == "s3/object"
    result.get(1).getReference() == "s3://another-bucket/another/path/file.zip"
    result.get(1).getName() == "s3://another-bucket/another/path/file.zip"
  }

  def "getProjects should return all projects in the account"() {
    given:
    def firstPage = (1..100).collect{ it.toString() }
    def secondPage = (101..150).collect{ it.toString() }

    when:
    def result = awsCodeBuildAccount.getProjects()

    then:
    1 * client.listProjects(new ListProjectsRequest().withSortBy(ProjectSortByType.NAME)) >> new ListProjectsResult().withProjects(firstPage).withNextToken("nextToken")
    1 * client.listProjects(new ListProjectsRequest().withSortBy(ProjectSortByType.NAME).withNextToken("nextToken")) >> new ListProjectsResult().withProjects(secondPage)
    result.size() == 150
    result == (1..150).collect{ it.toString() }
  }

  def "getProjects should return empty when no project found"() {
    when:
    def result = awsCodeBuildAccount.getProjects()

    then:
    1 * client.listProjects(new ListProjectsRequest().withSortBy(ProjectSortByType.NAME)) >> new ListProjectsResult().withProjects([])
    result == []
  }

  private static StartBuildRequest getStartBuildInput(String projectName) {
    return new StartBuildRequest()
      .withProjectName(projectName);
  }

  private static BatchGetBuildsRequest getBatchGetBuildsInput(String... ids) {
    return new BatchGetBuildsRequest()
      .withIds(ids);
  }

  private static Build getOutputBuild(String projectName) {
    return new Build()
      .withProjectName(projectName);
  }
}
