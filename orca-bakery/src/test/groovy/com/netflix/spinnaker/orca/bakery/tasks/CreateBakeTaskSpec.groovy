/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.orca.bakery.api.BakeRequest
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedString
import rx.Observable
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.bakery.api.BakeStatus.State.COMPLETED
import static com.netflix.spinnaker.orca.bakery.api.BakeStatus.State.RUNNING
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage
import static java.net.HttpURLConnection.HTTP_NOT_FOUND
import static java.util.UUID.randomUUID

class CreateBakeTaskSpec extends Specification {

  @Subject
    task = new CreateBakeTask()
  Stage bakeStage
  def mapper = OrcaObjectMapper.newInstance()

  @Shared
  def runningStatus = new BakeStatus(id: randomUUID(), state: RUNNING)

  @Shared
  def completedStatus = new BakeStatus(id: randomUUID(), state: COMPLETED)

  def bakeConfig = [
    region   : "us-west-1",
    package  : "hodor",
    user     : "bran",
    baseOs   : "ubuntu",
    baseLabel: BakeRequest.Label.release.name()
  ]

  Pipeline pipeline = pipeline {
    stage {
      type = "bake"
      context = bakeConfig
    }
  }

  @Shared
  def bakeConfigWithCloudProviderType = [
    region           : "us-west-1",
    package          : "hodor",
    user             : "bran",
    cloudProviderType: "aws",
    baseOs           : "ubuntu",
    baseLabel        : BakeRequest.Label.release.name()
  ]

  @Shared
  def bakeConfigWithRebake = [
    region           : "us-west-1",
    package          : "hodor",
    user             : "bran",
    cloudProviderType: "aws",
    baseOs           : "ubuntu",
    baseLabel        : BakeRequest.Label.release.name(),
    rebake           : true
  ]

  @Shared
  def buildInfo = [
    artifacts: [
      [fileName: 'hodor_1.1_all.deb'],
      [fileName: 'hodor-1.1.noarch.rpm'],
      [fileName: 'hodor.1.1.nupkg']
    ]
  ]

  @Shared
  def buildInfoWithUrl = [
    url      : "http://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-echo/69/",
    artifacts: [
      [fileName: 'hodor_1.1_all.deb'],
      [fileName: 'hodor-1.1.noarch.rpm'],
      [fileName: 'hodor.1.1.nupkg']
    ]
  ]

  @Shared
  def buildInfoWithFoldersUrl = [
    url      : "http://spinnaker.builds.test.netflix.net/job/folder/job/SPINNAKER-package-echo/69/",
    artifacts: [
      [fileName: 'hodor_1.1_all.deb'],
      [fileName: 'hodor-1.1.noarch.rpm'],
      [fileName: 'hodor.1.1.nupkg']
    ]
  ]

  @Shared
  def buildInfoWithUrlAndSCM = [
    url      : "http://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-echo/69/",
    artifacts: [
      [fileName: 'hodor_1.1_all.deb'],
      [fileName: 'hodor-1.1.noarch.rpm'],
      [fileName: 'hodor.1.1.nupkg']
    ],
    scm      : [
      [name  : "refs/remotes/origin/master",
       sha1  : "f83a447f8d02a40fa84ec9d4d0dccd263d51782d",
       branch: "master"]
    ]
  ]

  @Shared
  def buildInfoWithUrlAndTwoSCMs = [
    url      : "http://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-echo/69/",
    artifacts: [
      [fileName: 'hodor_1.1_all.deb'],
      [fileName: 'hodor-1.1.noarch.rpm'],
      [fileName: 'hodor.1.1.nupkg']
    ],
    scm      : [
      [name  : "refs/remotes/origin/master",
       sha1  : "f83a447f8d02a40fa84ec9d4d0dccd263d51782d",
       branch: "master"],
      [name  : "refs/remotes/origin/some-feature",
       sha1  : "1234567f8d02a40fa84ec9d4d0dccd263d51782d",
       branch: "some-feature"]
    ]
  ]

  @Shared
  def buildInfoWithUrlAndMasterAndDevelopSCMs = [
    url      : "http://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-echo/69/",
    artifacts: [
      [fileName: 'hodor_1.1_all.deb'],
      [fileName: 'hodor-1.1.noarch.rpm'],
      [fileName: 'hodor.1.1.nupkg']
    ],
    scm      : [
      [name  : "refs/remotes/origin/master",
       sha1  : "f83a447f8d02a40fa84ec9d4d0dccd263d51782d",
       branch: "master"],
      [name  : "refs/remotes/origin/develop",
       sha1  : "1234567f8d02a40fa84ec9d4d0dccd263d51782d",
       branch: "develop"]
    ]
  ]

  @Shared
  def buildInfoNoMatch = [
    artifacts: [
      [fileName: 'hodornodor_1.1_all.deb'],
      [fileName: 'hodor-1.1.noarch.rpm'],
      [fileName: 'hodor.1.1.nupkg']
    ]
  ]

  @Shared
  def invalidArtifactList = [
    artifacts: [
      [yolo: 'blinky'],
      [hulk: 'hogan']
    ]
  ]

  @Shared
  def error404 = RetrofitError.httpError(
    null,
    new Response("", HTTP_NOT_FOUND, "Not Found", [], new TypedString("{ \"messages\": [\"Error Message\"]}")),
    null,
    null
  )

  def setup() {
    task.mapper = mapper
    bakeStage = pipeline.stages.first()
  }

  def "creates a bake for the correct region"() {
    given:
    task.bakery = Mock(BakeryService)

    when:
    task.execute(bakeStage)

    then:
    1 * task.bakery.createBake(bakeConfig.region, _ as BakeRequest, null) >> Observable.from(runningStatus)
  }

  def "should surface error message (if available) on a 404"() {
    given:
    task.bakery = Mock(BakeryService)

    when:
    task.execute(bakeStage)

    then:
    1 * task.bakery.createBake(bakeConfig.region, _ as BakeRequest, null) >> {
      throw error404
    }
    IllegalStateException e = thrown()
    e.message == "Error Message"
  }

  def "gets bake configuration from job context"() {
    given:
    def bake
    task.bakery = Mock(BakeryService) {
      1 * createBake(*_) >> {
        bake = it[1]
        Observable.from(runningStatus)
      }
    }

    when:
    task.execute(bakeStage)

    then:
    bake.user == bakeConfig.user
    bake.packageName == bakeConfig.package
    bake.baseOs == bakeConfig.baseOs
    bake.baseLabel.name() == bakeConfig.baseLabel
  }

  @Unroll
  def "finds package details from context and trigger"() {
    given:
    bakeConfig.buildInfo = contextInfo
    Pipeline pipelineWithTrigger = pipeline {
      trigger.putAll(buildInfo: triggerInfo)
      stage {
        type = "bake"
        context = bakeConfig
      }
    }

    def bake
    task.bakery = Mock(BakeryService) {
      1 * createBake(*_) >> {
        bake = it[1]
        Observable.from(runningStatus)
      }
    }

    when:
    def result = task.execute(pipelineWithTrigger.stages.first())

    then:
    bake.packageName == 'hodor_1.1_all'
    result.context.bakePackageName == 'hodor_1.1_all'

    where:
    triggerInfo      | contextInfo
    null             | buildInfo
    buildInfo        | null
    buildInfo        | buildInfo
    buildInfo        | buildInfoNoMatch
    buildInfoNoMatch | buildInfo
  }

  @Unroll
  def "fails if pipeline trigger or context includes artifacts but no artifact for the bake package"() {
    given:
    bakeConfig.buildInfo = contextInfo
    Pipeline pipelineWithTrigger = pipeline {
      trigger.putAll(buildInfo: triggerInfo)
      stage {
        type = "bake"
        context = bakeConfig
      }
    }
    task.bakery = Mock(BakeryService)

    when:
    task.execute(pipelineWithTrigger.stages.first())

    then:
    IllegalStateException ise = thrown(IllegalStateException)
    ise.message.startsWith("Unable to find deployable artifact starting with [hodor_] and ending with .deb in")

    where:
    contextInfo         | triggerInfo
    null                | buildInfoNoMatch
    buildInfoNoMatch    | null
    buildInfoNoMatch    | buildInfoNoMatch
    buildInfoNoMatch    | invalidArtifactList
    invalidArtifactList | buildInfoNoMatch
  }

  @Unroll
  def "fails if pipeline trigger and context includes artifacts have a different match"() {
    given:
    bakeConfig.buildInfo = [
      artifacts: [
        [fileName: 'hodor_1.2_all.deb'],
        [fileName: 'hodor-1.2.noarch.rpm'],
        [fileName: 'hodor.1.2.nupkg']
      ]
    ]
    Pipeline pipelineWithTrigger = pipeline {
      trigger.putAll(buildInfo: buildInfo)
      stage {
        type = "bake"
        context = bakeConfig
      }
    }
    task.bakery = Mock(BakeryService)

    when:
    task.execute(pipelineWithTrigger.stages.first())

    then:
    IllegalStateException ise = thrown(IllegalStateException)
    ise.message.startsWith("Found build artifact in Jenkins")
  }

  def "outputs the status of the bake"() {
    given:
    task.bakery = Stub(BakeryService) {
      createBake(*_) >> Observable.from(runningStatus)
    }

    when:
    def result = task.execute(bakeStage)

    then:
    with(result.context.status) {
      id == runningStatus.id
      state == runningStatus.state
    }
  }

  def "outputs the packageName of the bake"() {
    given:
    task.bakery = Stub(BakeryService) {
      createBake(*_) >> Observable.from(runningStatus)
    }

    when:
    def result = task.execute(bakeStage)

    then:
    result.context.bakePackageName == bakeConfig.package
  }

  @Unroll
  def "build info with url yields bake stage output containing build host, job and build number"() {
    given:
    bakeConfig.buildInfo = contextInfo
    Pipeline pipelineWithTrigger = pipeline {
      trigger.putAll(buildInfo: triggerInfo)
      stage {
        type = "bake"
        context = bakeConfig
      }
    }

    task.bakery = Stub(BakeryService) {
      createBake(*_) >> Observable.from(runningStatus)
    }
    task.extractBuildDetails = true

    when:
    def result = task.execute(pipelineWithTrigger.stages.first())

    then:
    result.context.bakePackageName == "hodor_1.1_all"
    result.context.buildHost == "http://spinnaker.builds.test.netflix.net/"
    result.context.job == jobName
    result.context.buildNumber == "69"
    !result.context.commitHash

    where:
    triggerInfo             | contextInfo             | jobName
    buildInfoWithUrl        | null                    | "SPINNAKER-package-echo"
    null                    | buildInfoWithUrl        | "SPINNAKER-package-echo"
    buildInfoWithFoldersUrl | null                    | "folder/job/SPINNAKER-package-echo"
    null                    | buildInfoWithFoldersUrl | "folder/job/SPINNAKER-package-echo"
  }

  @Unroll
  def "build info with url and scm yields bake stage output containing build host, job, build number and commit hash"() {
    given:
    bakeConfig.buildInfo = contextInfo
    Pipeline pipelineWithTrigger = pipeline {
      trigger.putAll(buildInfo: triggerInfo)
      stage {
        type = "bake"
        context = bakeConfig
      }
    }

    task.bakery = Stub(BakeryService) {
      createBake(*_) >> Observable.from(runningStatus)
    }
    task.extractBuildDetails = true

    when:
    def result = task.execute(pipelineWithTrigger.stages.first())

    then:
    result.context.bakePackageName == "hodor_1.1_all"
    result.context.buildHost == "http://spinnaker.builds.test.netflix.net/"
    result.context.job == "SPINNAKER-package-echo"
    result.context.buildNumber == "69"
    result.context.commitHash == "f83a447f8d02a40fa84ec9d4d0dccd263d51782d"

    where:
    triggerInfo            | contextInfo
    buildInfoWithUrlAndSCM | null
    null                   | buildInfoWithUrlAndSCM
  }

  @Unroll
  def "build info with url and two scms yields bake stage output containing build host, job, build number and correctly-chosen commit hash"() {
    given:
    bakeConfig.buildInfo = contextInfo
    Pipeline pipelineWithTrigger = pipeline {
      trigger.putAll(buildInfo: triggerInfo)
      stage {
        type = "bake"
        context = bakeConfig
      }
    }

    task.bakery = Stub(BakeryService) {
      createBake(*_) >> Observable.from(runningStatus)
    }
    task.extractBuildDetails = true

    when:
    def result = task.execute(pipelineWithTrigger.stages.first())

    then:
    result.context.bakePackageName == "hodor_1.1_all"
    result.context.buildHost == "http://spinnaker.builds.test.netflix.net/"
    result.context.job == "SPINNAKER-package-echo"
    result.context.buildNumber == "69"
    result.context.commitHash == "1234567f8d02a40fa84ec9d4d0dccd263d51782d"

    where:
    triggerInfo                | contextInfo
    buildInfoWithUrlAndTwoSCMs | null
    null                       | buildInfoWithUrlAndTwoSCMs
  }

  @Unroll
  def "build info with url and master and develop scms yields bake stage output containing build host, job, build number and first commit hash"() {
    given:
    bakeConfig.buildInfo = contextInfo
    Pipeline pipelineWithTrigger = pipeline {
      trigger.putAll(buildInfo: triggerInfo)
      stage {
        type = "bake"
        context = bakeConfig
      }
    }

    task.bakery = Stub(BakeryService) {
      createBake(*_) >> Observable.from(runningStatus)
    }
    task.extractBuildDetails = true

    when:
    def result = task.execute(pipelineWithTrigger.stages.first())

    then:
    result.context.bakePackageName == "hodor_1.1_all"
    result.context.buildHost == "http://spinnaker.builds.test.netflix.net/"
    result.context.job == "SPINNAKER-package-echo"
    result.context.buildNumber == "69"
    result.context.commitHash == "f83a447f8d02a40fa84ec9d4d0dccd263d51782d"

    where:
    triggerInfo                             | contextInfo
    buildInfoWithUrlAndMasterAndDevelopSCMs | null
    null                                    | buildInfoWithUrlAndMasterAndDevelopSCMs
  }

  @Unroll
  def "build info without url yields bake stage output without build host, job, build number and commit hash"() {
    given:
    bakeConfig.buildInfo = contextInfo
    Pipeline pipelineWithTrigger = pipeline {
      trigger.putAll(buildInfo: triggerInfo)
      stage {
        type = "bake"
        context = bakeConfig
      }
    }

    task.bakery = Stub(BakeryService) {
      createBake(*_) >> Observable.from(runningStatus)
    }
    task.extractBuildDetails = extractBuildDetails

    when:
    def result = task.execute(pipelineWithTrigger.stages.first())

    then:
    result.context.bakePackageName == "hodor_1.1_all"
    !result.context.buildHost
    !result.context.job
    !result.context.buildNumber
    !result.context.commitHash

    where:
    triggerInfo | contextInfo | extractBuildDetails
    buildInfo   | null        | true
    null        | buildInfo   | true
    buildInfo   | null        | false
    null        | buildInfo   | false
  }

  @Unroll
  def "build info with url yields bake request containing build host, job and build number"() {
    given:
    bakeConfig.buildInfo = contextInfo
    Pipeline pipelineWithTrigger = pipeline {
      trigger.putAll(buildInfo: triggerInfo)
      stage {
        type = "bake"
        context = bakeConfig
      }
    }

    task.bakery = Mock(BakeryService)
    task.extractBuildDetails = true

    when:
    task.execute(pipelineWithTrigger.stages.first())

    then:
    1 * task.bakery.createBake(bakeConfig.region,
      {
        it.user == "bran" &&
          it.packageName == "hodor_1.1_all" &&
          it.baseLabel == BakeRequest.Label.release &&
          it.baseOs == "ubuntu" &&
          it.buildHost == "http://spinnaker.builds.test.netflix.net/" &&
          it.job == "SPINNAKER-package-echo" &&
          it.buildNumber == "69"
        it.commitHash == null
      },
      null) >> Observable.from(runningStatus)

    where:
    triggerInfo      | contextInfo
    buildInfoWithUrl | null
    null             | buildInfoWithUrl
  }

  @Unroll
  def "build info with url but without extractBuildDetails yields bake request without build host, job, build number, and commit hash"() {
    given:
    bakeConfig.buildInfo = contextInfo
    Pipeline pipelineWithTrigger = pipeline {
      trigger.putAll(buildInfo: triggerInfo)
      stage {
        type = "bake"
        context = bakeConfig
      }
    }

    task.bakery = Mock(BakeryService)

    when:
    task.execute(pipelineWithTrigger.stages.first())

    then:
    1 * task.bakery.createBake(bakeConfig.region,
      {
        it.user == "bran" &&
          it.packageName == "hodor_1.1_all" &&
          it.baseLabel == BakeRequest.Label.release &&
          it.baseOs == "ubuntu" &&
          it.buildHost == null &&
          it.job == null &&
          it.buildNumber == null &&
          it.commitHash == null
      },
      null) >> Observable.from(runningStatus)

    where:
    triggerInfo      | contextInfo
    buildInfoWithUrl | null
    null             | buildInfoWithUrl
  }

  @Unroll
  def "build info without url yields bake request without build host, job, build number and commit hash"() {
    given:
    bakeConfig.buildInfo = contextInfo
    Pipeline pipelineWithTrigger = pipeline {
      trigger.putAll(buildInfo: triggerInfo)
      stage {
        type = "bake"
        context = bakeConfig
      }
    }

    task.bakery = Mock(BakeryService)

    when:
    task.execute(pipelineWithTrigger.stages.first())

    then:
    1 * task.bakery.createBake(bakeConfig.region,
      {
        it.user == "bran" &&
          it.packageName == "hodor_1.1_all" &&
          it.baseLabel == BakeRequest.Label.release &&
          it.baseOs == "ubuntu" &&
          it.buildHost == null &&
          it.job == null &&
          it.buildNumber == null &&
          it.commitHash == null
      },
      null) >> Observable.from(runningStatus)

    where:
    triggerInfo | contextInfo
    buildInfo   | null
    null        | buildInfo
  }

  def "cloudProviderType is propagated"() {
    given:
    def pipeline = pipeline {
      stage {
        type = "bake"
        context = bakeConfigWithCloudProviderType
      }
    }
    def bake
    task.bakery = Mock(BakeryService) {
      1 * createBake(*_) >> {
        bake = it[1]
        Observable.from(runningStatus)
      }
    }

    when:
    task.execute(pipeline.stages.first())

    then:
    bake.cloudProviderType == BakeRequest.CloudProviderType.aws
    bake.user == bakeConfigWithCloudProviderType.user
    bake.packageName == bakeConfigWithCloudProviderType.package
    bake.baseOs == bakeConfigWithCloudProviderType.baseOs
    bake.baseLabel.name() == bakeConfigWithCloudProviderType.baseLabel
  }

  @Unroll
  def "sets previouslyBaked flag to #previouslyBaked when status is #status.state"() {
    given:
    task.bakery = Stub(BakeryService) {
      createBake(*_) >> Observable.from(status)
    }

    when:
    def result = task.execute(bakeStage)

    then:
    result.context.status == status
    result.context.previouslyBaked == previouslyBaked

    where:
    status          | previouslyBaked
    runningStatus   | false
    completedStatus | true
  }

  def "sets rebake query parameter if rebake flag is set in job context"() {
    given:
    def stage = stage {
      type = "bake"
      context = bakeConfigWithRebake
    }
    task.bakery = Mock(BakeryService)

    when:
    task.execute(stage)

    then:
    1 * task.bakery.createBake(bakeConfig.region,
      {
        it.user == "bran" &&
          it.packageName == "hodor" &&
          it.baseLabel == BakeRequest.Label.release &&
          it.baseOs == "ubuntu"
      } as BakeRequest,
      "1") >> Observable.from(runningStatus)
    0 * _
  }

  @Unroll
  def "sets rebake query parameter to #queryParameter when trigger is #trigger"() {
    given:
    Pipeline pipeline = pipeline {
      if (triggerConfig) {
        trigger.putAll(triggerConfig)
      }
      stage {
        type = "bake"
        context = bakeConfig
      }
    }
    task.bakery = Mock(BakeryService)

    when:
    task.execute(pipeline.stages.first())

    then:
    1 * task.bakery.createBake(bakeConfig.region,
      {
        it.user == "bran" &&
          it.packageName == "hodor" &&
          it.baseLabel == BakeRequest.Label.release &&
          it.baseOs == "ubuntu"
      } as BakeRequest,
      queryParameter) >> Observable.from(runningStatus)
    0 * _

    when:
    task.execute(stage {
      type = "bake"
      context = bakeConfig
    })

    then:
    1 * task.bakery.createBake(bakeConfig.region,
      {
        it.user == "bran" &&
          it.packageName == "hodor" &&
          it.baseLabel == BakeRequest.Label.release &&
          it.baseOs == "ubuntu"
      } as BakeRequest,
      null) >> Observable.from(runningStatus)
    0 * _

    where:
    triggerConfig   | queryParameter
    [rebake: true]  | "1"
    [rebake: false] | null
    null            | null
  }

}
