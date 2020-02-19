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

package com.netflix.spinnaker.orca.igor.tasks

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.igor.IgorService
import com.netflix.spinnaker.orca.igor.model.AwsCodeBuildExecution
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class StartAwsCodeBuildTaskSpec extends Specification {
  def ACCOUNT = "codebuild-account"
  def PROJECT_NAME = "test"
  def ARTIFACT_ID = "edceed55-29a5-45d3-9ed8-45871e3f026a"
  def ANOTHER_ARTIFACT_ID = "c7715bbf-5c12-44d6-87ef-8149473e02f7"
  def ARN = "arn:aws:codebuild:us-west-2:123456789012:build/test:c7715bbf-5c12-44d6-87ef-8149473e02f7"

  Execution execution = Mock(Execution)
  IgorService igorService = Mock(IgorService)
  ArtifactUtils artifactUtils = Mock(ArtifactUtils)

  @Subject
  StartAwsCodeBuildTask task = new StartAwsCodeBuildTask(igorService, artifactUtils)
  def igorResponse = new AwsCodeBuildExecution(ARN, null, null)

  def "should start a build"() {
    given:
    def stage = new Stage(execution, "awsCodeBuild", [account: ACCOUNT, projectName: PROJECT_NAME])

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * igorService.startAwsCodeBuild(ACCOUNT, _) >> igorResponse
    result.status == ExecutionStatus.SUCCEEDED
    result.context.buildInfo.arn == igorResponse.arn
  }

  def "should not override source if sourceOverride is false"() {
    given:
    def stage = new Stage(
        execution,
        "awsCodeBuild",
        getDefaultContext(false)
    )

    when:
    task.execute(stage)

    then:
    1 * igorService.startAwsCodeBuild(ACCOUNT, {
      it.get("sourceLocationOverride") == null
      it.get("sourceTypeOverride") == null
    }) >> igorResponse
  }

  @Unroll
  def "should correctly override #sourceType source"() {
    given:
    def artifact = Artifact.builder()
        .type(artifactType)
        .reference(artifactReference)
        .version("master")
        .artifactAccount("my-codebuild-account").build()
    def stage = new Stage(
        execution,
        "awsCodeBuild",
        getDefaultContext()
    )

    when:
    task.execute(stage)

    then:
    1 * artifactUtils.getBoundArtifactForStage(stage, ARTIFACT_ID, null) >> artifact
    1 * igorService.startAwsCodeBuild(ACCOUNT, {
      it.get("sourceLocationOverride") == sourceLocation
      it.get("sourceTypeOverride") == sourceType
      it.get("sourceVersion") == "master"
    }) >> igorResponse

    where:
    artifactType | artifactReference                          | sourceType  | sourceLocation
    "s3/object"  | "s3://bucket/path/source.zip"              | "S3"        | "bucket/path/source.zip"
    "git/repo"   | "https://github.com/codebuild/repo.git"    | "GITHUB"    | "https://github.com/codebuild/repo.git"
    "git/repo"   | "https://bitbucket.org/codebuild/repo.git" | "BITBUCKET" | "https://bitbucket.org/codebuild/repo.git"
  }

  def "should explicitly use sourceType if type could be inferred"() {
    given:
    def context = getDefaultContext()
    context.source.put("sourceType", "GITHUB_ENTERPRISE")
    def artifact = Artifact.builder()
        .type("git/repo")
        .reference("https://github.com/codebuild/repo.git")
        .artifactAccount("my-codebuild-account").build()
    def stage = new Stage(
        execution,
        "awsCodeBuild",
        context
    )

    when:
    task.execute(stage)

    then:
    1 * artifactUtils.getBoundArtifactForStage(stage, ARTIFACT_ID, null) >> artifact
    1 * igorService.startAwsCodeBuild(ACCOUNT, {
      it.get("sourceLocationOverride") == "https://github.com/codebuild/repo.git"
      it.get("sourceTypeOverride") == "GITHUB_ENTERPRISE"
    }) >> igorResponse
  }

  def "should use sourceType if type couldn't be inferred"() {
    given:
    def context = getDefaultContext()
    context.source.put("sourceType", "GITHUB_ENTERPRISE")
    def artifact = Artifact.builder()
        .type("git/repo")
        .reference("http://enterprise.com/repo.git")
        .artifactAccount("my-codebuild-account").build()
    def stage = new Stage(
        execution,
        "awsCodeBuild",
        context
    )

    when:
    task.execute(stage)

    then:
    1 * artifactUtils.getBoundArtifactForStage(stage, ARTIFACT_ID, null) >> artifact
    1 * igorService.startAwsCodeBuild(ACCOUNT, {
      it.get("sourceLocationOverride") == "http://enterprise.com/repo.git"
      it.get("sourceTypeOverride") == "GITHUB_ENTERPRISE"
    }) >> igorResponse
  }

  def "should throw exception if artifact type is unknown"() {
    given:
    def artifact = Artifact.builder()
        .type("unknown")
        .reference("location")
        .artifactAccount("my-codebuild-account").build()
    def stage = new Stage(
        execution,
        "awsCodeBuild",
        getDefaultContext()
    )

    when:
    task.execute(stage)

    then:
    1 * artifactUtils.getBoundArtifactForStage(stage, ARTIFACT_ID, null) >> artifact
    IllegalStateException ex = thrown()
    ex.getMessage() == "Unexpected value: unknown"
  }

  def "should throw exception if artifact reference is unknown"() {
    given:
    def artifact = Artifact.builder()
        .type("git/repo")
        .reference("location")
        .artifactAccount("my-codebuild-account").build()
    def stage = new Stage(
        execution,
        "awsCodeBuild",
        getDefaultContext()
    )

    when:
    task.execute(stage)

    then:
    1 * artifactUtils.getBoundArtifactForStage(stage, ARTIFACT_ID, null) >> artifact
    IllegalStateException ex = thrown()
    ex.getMessage() == "Source type could not be inferred from location"
  }

  def "should throw exception if subpath is set"() {
    given:
    def artifact = Artifact.builder()
        .type("git/repo")
        .reference("location")
        .artifactAccount("my-codebuild-account")
        .metadata([subPath: "path"]).build()
    def stage = new Stage(
        execution,
        "awsCodeBuild",
        getDefaultContext()
    )

    when:
    task.execute(stage)

    then:
    1 * artifactUtils.getBoundArtifactForStage(stage, ARTIFACT_ID, null) >> artifact
    IllegalArgumentException ex = thrown()
    ex.getMessage() == "Subpath is not supported by AWS CodeBuild stage"
  }

  def "should use sourceVersion if presents"() {
    given:
    def context = getDefaultContext()
    context.source.put("sourceVersion", "not-master")
    def artifact = Artifact.builder()
        .type("git/repo")
        .reference("https://github.com/codebuild/repo.git")
        .version("master")
        .artifactAccount("my-codebuild-account").build()
    def stage = new Stage(
        execution,
        "awsCodeBuild",
        context
    )

    when:
    task.execute(stage)

    then:
    1 * artifactUtils.getBoundArtifactForStage(stage, ARTIFACT_ID, null) >> artifact
    1 * igorService.startAwsCodeBuild(ACCOUNT, {
      it.get("sourceVersion") == "not-master"
    }) >> igorResponse
  }

  def "should correctly append image, buildspec and env vars"() {
    given:
    def stage = new Stage(
        execution,
        "awsCodeBuild",
        getDefaultContext(false)
    )

    when:
    task.execute(stage)

    then:
    1 * igorService.startAwsCodeBuild(ACCOUNT, {
      it.get("environmentVariablesOverride") == [
          [
              type: "PLAINTEXT",
              name: "foo",
              value: "bar",
          ]
      ]
      it.get("imageOverride") == "alpine"
      it.get("buildspecOverride") == "ls"
    }) >> igorResponse
  }

  def "should not append buildspec or image when it's empty string"() {
    given:
    def context = getDefaultContext(false)
    context.source.buildspec = ""
    context.image = ""
    def stage = new Stage(
        execution,
        "awsCodeBuild",
        context
    )

    when:
    task.execute(stage)

    then:
    1 * igorService.startAwsCodeBuild(ACCOUNT, {
      it.get("imageOverride") == null
      it.get("buildspecOverride") == null
    }) >> igorResponse
  }

  def "should override secondary sources along with versions"() {
    given:
    def context = getDefaultContext(false)
    context.put("secondarySources", [
        [
            sourceArtifact: [
                artifactId: ARTIFACT_ID,
            ],
        ],
        [
            sourceArtifact: [
                artifactId: ARTIFACT_ID,
            ],
            sourceVersion: "master",
        ],
        [
            sourceArtifact: [
                artifactId: ANOTHER_ARTIFACT_ID,
            ],
            sourceVersion: "master",
        ],
    ] as Serializable)
    def artifact = Artifact.builder()
        .type("git/repo")
        .reference("https://github.com/codebuild/repo.git")
        .version("artifact-version")
        .artifactAccount("my-codebuild-account").build()
    def artifactWithoutVersion = Artifact.builder()
        .type("git/repo")
        .reference("https://github.com/codebuild/another-repo.git")
        .artifactAccount("my-codebuild-account").build()
    def stage = new Stage(
        execution,
        "awsCodeBuild",
        context
    )

    when:
    task.execute(stage)

    then:
    2 * artifactUtils.getBoundArtifactForStage(stage, ARTIFACT_ID, null) >> artifact
    1 * artifactUtils.getBoundArtifactForStage(stage, ANOTHER_ARTIFACT_ID, null) >> artifactWithoutVersion
    1 * igorService.startAwsCodeBuild(ACCOUNT, {
      it.get("secondarySourcesOverride") == [
          [
              type: "GITHUB",
              location: "https://github.com/codebuild/repo.git",
              sourceIdentifier: "0",
          ],
          [
              type: "GITHUB",
              location: "https://github.com/codebuild/repo.git",
              sourceIdentifier: "1",
          ],
          [
              type: "GITHUB",
              location: "https://github.com/codebuild/another-repo.git",
              sourceIdentifier: "2",
          ],
      ]
      it.get("secondarySourcesVersionOverride") == [
          [
              sourceIdentifier: "0",
              sourceVersion: "artifact-version",
          ],
          [
              sourceIdentifier: "1",
              sourceVersion: "master",
          ],
          [
              sourceIdentifier: "2",
              sourceVersion: "master",
          ],
      ]
    }) >> igorResponse
  }

  def "should not override secondary source version if not given"() {
    given:
    def context = getDefaultContext(false)
    context.put("secondarySources", [
        [
            sourceArtifact: [
                artifactId: ARTIFACT_ID,
            ],
        ],
        [
            sourceArtifact: [
                artifactId: ANOTHER_ARTIFACT_ID,
            ],
            sourceVersion: "master",
        ],
    ] as Serializable)
    def artifact = Artifact.builder()
        .type("git/repo")
        .reference("https://github.com/codebuild/repo.git")
        .artifactAccount("my-codebuild-account").build()
    def anotherArtifact = Artifact.builder()
        .type("git/repo")
        .reference("https://github.com/codebuild/another-repo.git")
        .artifactAccount("my-codebuild-account").build()
    def stage = new Stage(
        execution,
        "awsCodeBuild",
        context
    )

    when:
    task.execute(stage)

    then:
    1 * artifactUtils.getBoundArtifactForStage(stage, ARTIFACT_ID, null) >> artifact
    1 * artifactUtils.getBoundArtifactForStage(stage, ANOTHER_ARTIFACT_ID, null) >> anotherArtifact
    1 * igorService.startAwsCodeBuild(ACCOUNT, {
      it.get("secondarySourcesOverride") == [
          [
              type: "GITHUB",
              location: "https://github.com/codebuild/repo.git",
              sourceIdentifier: "0",
          ],
          [
              type: "GITHUB",
              location: "https://github.com/codebuild/another-repo.git",
              sourceIdentifier: "1",
          ],
      ]
      it.get("secondarySourcesVersionOverride") == [
          [
              sourceIdentifier: "1",
              sourceVersion: "master",
          ],
      ]
    }) >> igorResponse
  }

  def getDefaultContext(Boolean sourceOverride = true) {
    [
        account       : ACCOUNT,
        projectName   : PROJECT_NAME,
        source        : [
            sourceOverride: sourceOverride,
            sourceArtifact: [
                artifactId: ARTIFACT_ID,
            ],
            buildspec: "ls",
        ],
        environmentVariables: [
            "foo": "bar",
        ],
        image: "alpine",
    ]
  }
}
