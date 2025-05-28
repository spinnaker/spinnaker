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

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.web.selector.v2.SelectableService
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger
import com.netflix.spinnaker.orca.bakery.BakerySelector
import com.netflix.spinnaker.orca.bakery.api.BakeRequest
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.bakery.api.BaseImage
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.*
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils
import com.netflix.spinnaker.orca.pipeline.util.PackageType
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.mock.Calls;
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
  StageExecutionImpl bakeStage
  def mapper = OrcaObjectMapper.newInstance()

  ArtifactUtils artifactUtils = Stub() {
    getAllArtifacts(_) >> []
  }

  @Shared
  def runningStatus = new BakeStatus(id: randomUUID(), state: RUNNING)

  @Shared
  def completedStatus = new BakeStatus(id: randomUUID(), state: COMPLETED)

  def bakeConfig = [
    region   : "us-west-1",
    package  : "hodor",
    user     : "bran",
    baseOs   : "ubuntu",
    baseLabel: "release"
  ]

  PipelineExecutionImpl pipeline = pipeline {
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
    baseLabel        : "release"
  ]

  @Shared
  def bakeConfigWithAzureManagedImage = [
    region           : "us-west-1",
    package          : "hodor",
    user             : "bran",
    cloudProviderType: "azure",
    baseLabel        : "release",
    osType           : "linux",
    packageType      : "RPM",
    managedImage     : "managed",
    account          : "splat-azure"
  ]

  @Shared
  def bakeConfigWithAzureDefaultImage = [
    region           : "us-west-1",
    package          : "hodor",
    user             : "bran",
    cloudProviderType: "azure",
    baseOs           : "ubuntu",
    baseLabel        : "release",
    account          : "splat-azure"
  ]

  @Shared
  def bakeConfigWithAzureCustomImage = [
    region           : "us-west-1",
    package          : "hodor",
    user             : "bran",
    cloudProviderType: "azure",
    osType           : "windows",
    sku              : "sky",
    offer            : "offer",
    publisher        : "pub",
    baseLabel        : "release",
    account          : "splat-azure"
  ]

  @Shared
  def bakeConfigWithRebake = [
    region           : "us-west-1",
    package          : "hodor",
    user             : "bran",
    cloudProviderType: "aws",
    baseOs           : "ubuntu",
    baseLabel        : "release",
    rebake           : true
  ]

  @Shared
  def bakeConfigWithArtifacts = [
    region   : "us-west-1",
    packageArtifactIds : ["abc", "def"],
    package  : "hodor",
    user     : "bran",
    baseOs   : "ubuntu",
    baseLabel: "release"
  ]

  @Shared
  def bakeConfigWithoutOs = [
    region           : "us-west-1",
    packageArtifactIds : ["abc", "def"],
    package          : "hodor",
    user             : "bran",
    cloudProviderType: "aws",
    baseLabel        : "release",
  ]

  @Shared
  def buildInfo = new JenkinsBuildInfo(
    "name", 0, "http://jenkins", "SUCCESS",
    [
      new JenkinsArtifact("hodor_1.1_all.deb", "."),
      new JenkinsArtifact("hodor-1.1.noarch.rpm", "."),
      new JenkinsArtifact("hodor.1.1.nupkg", ".")
    ]
  )

  @Shared
  def buildInfoWithUrl = new JenkinsBuildInfo(
    "name", 0, "http://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-echo/69/", "SUCCESS",
    [
      new JenkinsArtifact("hodor_1.1_all.deb", "."),
      new JenkinsArtifact("hodor-1.1.noarch.rpm", "."),
      new JenkinsArtifact("hodor.1.1.nupkg", ".")
    ]
  )

  @Shared
  def buildInfoWithFoldersUrl = new JenkinsBuildInfo(
    "name", 0, "http://spinnaker.builds.test.netflix.net/job/folder/job/SPINNAKER-package-echo/69/", "SUCCESS",
    [
      new JenkinsArtifact("hodor_1.1_all.deb", "."),
      new JenkinsArtifact("hodor-1.1.noarch.rpm", "."),
      new JenkinsArtifact("hodor.1.1.nupkg", ".")
    ]
  )

  @Shared
  def buildInfoWithUrlAndSCM = new JenkinsBuildInfo(
    "name", 0, "http://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-echo/69/", "SUCCESS",
    [
      new JenkinsArtifact("hodor_1.1_all.deb", "."),
      new JenkinsArtifact("hodor-1.1.noarch.rpm", "."),
      new JenkinsArtifact("hodor.1.1.nupkg", ".")
    ],
    [new SourceControl("refs/remotes/origin/master", "master", "f83a447f8d02a40fa84ec9d4d0dccd263d51782d")]
  )

  @Shared
  def buildInfoWithUrlAndTwoSCMs = new JenkinsBuildInfo(
    "name", 0, "http://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-echo/69/", "SUCCESS",
    [
      new JenkinsArtifact("hodor_1.1_all.deb", "."),
      new JenkinsArtifact("hodor-1.1.noarch.rpm", "."),
      new JenkinsArtifact("hodor.1.1.nupkg", ".")
    ],
    [
      new SourceControl("refs/remotes/origin/master", "master", "f83a447f8d02a40fa84ec9d4d0dccd263d51782d"),
      new SourceControl("refs/remotes/origin/some-feature", "some-feature", "1234567f8d02a40fa84ec9d4d0dccd263d51782d")
    ]
  )

  @Shared
  def buildInfoWithUrlAndMasterAndDevelopSCMs = new JenkinsBuildInfo(
    "name", 0, "http://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-echo/69/", "SUCCESS",
    [
      new JenkinsArtifact("hodor_1.1_all.deb", "."),
      new JenkinsArtifact("hodor-1.1.noarch.rpm", "."),
      new JenkinsArtifact("hodor.1.1.nupkg", ".")
    ],
    [
      new SourceControl("refs/remotes/origin/master", "master", "f83a447f8d02a40fa84ec9d4d0dccd263d51782d"),
      new SourceControl("refs/remotes/origin/develop", "develop", "1234567f8d02a40fa84ec9d4d0dccd263d51782d")
    ]
  )

  @Shared
  def buildInfoNoMatch = new JenkinsBuildInfo(
    "name", 0, "http://jenkins", "SUCCESS",
    [
      new JenkinsArtifact("hodornodor_1.1_all.deb", "."),
      new JenkinsArtifact("hodor-1.1.noarch.rpm", "."),
      new JenkinsArtifact("hodor.1.1.nupkg", ".")
    ]
  )

  @Shared
  def invalidArtifactList = [
    artifacts: [
      [yolo: 'blinky'],
      [hulk: 'hogan']
    ]
  ]

  @Shared
  def httpError404 = makeSpinnakerHttpException(HTTP_NOT_FOUND)

  def setup() {
    task.mapper = mapper
    task.artifactUtils = artifactUtils
    bakeStage = pipeline.stages.first()
  }

  def "creates a bake for the correct region"() {
    given:
    def bakery = Mock(BakeryService)

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

    when:
    task.execute(bakeStage)

    then:
    1 * bakery.createBake(bakeConfig.region, _ as BakeRequest, null) >> Calls.response(runningStatus)
  }

  def "creates a bake for the correct region with rosco"() {
    given:
    def bakery = Mock(BakeryService) {
      getBaseImage(*_) >> Calls.response(new BaseImage().with {
        packageType = PackageType.DEB
        it
      })
    }

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
          extractBuildDetails: false,
          allowMissingPackageInstallation: false,
          roscoApisEnabled: true
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

    when:
    task.execute(bakeStage)

    then:
    1 * bakery.createBake(bakeConfig.region, _ as BakeRequest, null) >> Calls.response(runningStatus)
  }

  def "should surface error message (if available) on a 404"() {
    given:
    def bakery = Mock(BakeryService)

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

    when:
    task.execute(bakeStage)

    then:
    1 * bakery.createBake(bakeConfig.region, _ as BakeRequest, null) >> {
      throw httpError404
    }
    IllegalStateException e = thrown()
    e.message == "Error Message"
  }

  def "gets bake configuration from job context"() {
    given:
    def bake
    def bakery = Mock(BakeryService) {
      1 * createBake(*_) >> {
        bake = it[1]
        Calls.response(runningStatus)
      }
    }

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

    when:
    task.execute(bakeStage)

    then:
    bake.user == bakeConfig.user
    bake.packageName == bakeConfig.package
    bake.baseOs == bakeConfig.baseOs
    bake.baseLabel == bakeConfig.baseLabel
  }

  @Unroll
  def "finds package details from context and trigger"() {
    given:
    bakeConfig.buildInfo = contextInfo
    def pipelineWithTrigger = pipeline {
      trigger = new JenkinsTrigger("master", "job", 1, null)
      if (triggerInfo != null) {
        trigger.buildInfo = triggerInfo
      }
      stage {
        type = "bake"
        context = bakeConfig
      }
    }

    def bake
    def bakery = Mock(BakeryService) {
      1 * createBake(*_) >> {
        bake = it[1]
        Calls.response(runningStatus)
      }
    }

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
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
    def pipelineWithTrigger = pipeline {
      trigger = new JenkinsTrigger("master", "job", 1, null)
      if (triggerInfo != null) {
        trigger.buildInfo = triggerInfo
      }
      stage {
        type = "bake"
        context = bakeConfig
      }
    }
    def bakery = Mock(BakeryService)

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

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
    def pipelineWithTrigger = pipeline {
      trigger = new JenkinsTrigger("master", "job", 1, null)
      trigger.buildInfo = buildInfo
      stage {
        type = "bake"
        context = bakeConfig
      }
    }
    def bakery = Mock(BakeryService)

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

    when:
    task.execute(pipelineWithTrigger.stages.first())

    then:
    IllegalStateException ise = thrown(IllegalStateException)
    ise.message.startsWith("Found build artifact in both Jenkins")
  }

  def "outputs the status of the bake"() {
    given:
    def bakery = Stub(BakeryService) {
      createBake(*_) >> Calls.response(runningStatus)
    }

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
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
    def bakery = Stub(BakeryService) {
      createBake(*_) >> Calls.response(runningStatus)
    }

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
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
    def pipelineWithTrigger = pipeline {
      trigger = new JenkinsTrigger("master", "job", 1, null)
      if (triggerInfo != null) {
        trigger.buildInfo = triggerInfo
      }
      stage {
        type = "bake"
        context = bakeConfig
      }
    }

    def bakery = Stub(BakeryService) {
      createBake(*_) >> Calls.response(runningStatus)
    }

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: true,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

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
    def pipelineWithTrigger = pipeline {
      trigger = new JenkinsTrigger("master", "job", 1, null)
      if (triggerInfo != null) {
        trigger.buildInfo = triggerInfo
      }
      stage {
        type = "bake"
        context = bakeConfig
      }
    }

    def bakery = Stub(BakeryService) {
      createBake(*_) >> Calls.response(runningStatus)
    }

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: true,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

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
    def pipelineWithTrigger = pipeline {
      trigger = new JenkinsTrigger("master", "job", 1, null)
      if (triggerInfo != null) {
        trigger.buildInfo = triggerInfo
      }
      stage {
        type = "bake"
        context = mapper.convertValue(bakeConfig, Map)
      }
    }

    def bakery = Stub(BakeryService) {
      createBake(*_) >> Calls.response(runningStatus)
    }

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: true,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

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
    bakeConfig.buildInfo = mapper.convertValue(contextInfo, Map)
    def pipelineWithTrigger = pipeline {
      trigger = new JenkinsTrigger("master", "job", 1, null)
      if (triggerInfo != null) {
        trigger.buildInfo = triggerInfo
      }
      stage {
        type = "bake"
        context = mapper.convertValue(bakeConfig, Map)
      }
    }

    def bakery = Stub(BakeryService) {
      createBake(*_) >> Calls.response(runningStatus)
    }

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: true,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

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
    def pipelineWithTrigger = pipeline {
      trigger = new JenkinsTrigger("master", "job", 1, null)
      if (triggerInfo != null) {
        trigger.buildInfo = triggerInfo
      }
      stage {
        type = "bake"
        context = bakeConfig
      }
    }

    def bakery = Stub(BakeryService) {
      createBake(*_) >> Calls.response(runningStatus)
    }

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: extractBuildDetails,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

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
    def pipelineWithTrigger = pipeline {
      trigger = new JenkinsTrigger("master", "job", 1, null)
      if (triggerInfo != null) {
        trigger.buildInfo = triggerInfo
      }
      stage {
        type = "bake"
        context = bakeConfig
      }
    }

    def bakery = Mock(BakeryService)

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: true,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

    when:
    task.execute(pipelineWithTrigger.stages.first())

    then:
    1 * bakery.createBake(bakeConfig.region,
      {
        it.user == "bran" &&
          it.packageName == "hodor_1.1_all" &&
          it.baseLabel == "release" &&
          it.baseOs == "ubuntu" &&
          it.buildHost == "http://spinnaker.builds.test.netflix.net/" &&
          it.job == "SPINNAKER-package-echo" &&
          it.buildNumber == "69"
        it.commitHash == null
      },
      null) >> Calls.response(runningStatus)

    where:
    triggerInfo      | contextInfo
    buildInfoWithUrl | null
    null             | buildInfoWithUrl
  }

  @Unroll
  def "build info with url but without extractBuildDetails yields bake request without build host, job, build number, and commit hash"() {
    given:
    bakeConfig.buildInfo = contextInfo
    def pipelineWithTrigger = pipeline {
      trigger = new JenkinsTrigger("master", "job", 1, null)
      if (triggerInfo != null) {
        trigger.buildInfo = triggerInfo
      }
      stage {
        type = "bake"
        context = bakeConfig
      }
    }

    def bakery = Mock(BakeryService)

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

    when:
    task.execute(pipelineWithTrigger.stages.first())

    then:
    1 * bakery.createBake(bakeConfig.region,
      {
        it.user == "bran" &&
          it.packageName == "hodor_1.1_all" &&
          it.baseLabel == "release" &&
          it.baseOs == "ubuntu" &&
          it.buildHost == null &&
          it.job == null &&
          it.buildNumber == null &&
          it.commitHash == null
      },
      null) >> Calls.response(runningStatus)

    where:
    triggerInfo      | contextInfo
    buildInfoWithUrl | null
    null             | buildInfoWithUrl
  }

  @Unroll
  def "build info without url yields bake request without build host, job, build number and commit hash"() {
    given:
    bakeConfig.buildInfo = contextInfo
    def pipelineWithTrigger = pipeline {
      trigger = new JenkinsTrigger("master", "job", 1, null)
      if (triggerInfo != null) {
        trigger.buildInfo = triggerInfo
      }
      stage {
        type = "bake"
        context = bakeConfig
      }
    }

    def bakery = Mock(BakeryService)

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

    when:
    task.execute(pipelineWithTrigger.stages.first())

    then:
    1 * bakery.createBake(bakeConfig.region,
      {
        it.user == "bran" &&
          it.packageName == "hodor_1.1_all" &&
          it.baseLabel == "release" &&
          it.baseOs == "ubuntu" &&
          it.buildHost == null &&
          it.job == null &&
          it.buildNumber == null &&
          it.commitHash == null
      },
      null) >> Calls.response(runningStatus)

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
    def bakery = Mock(BakeryService) {
      1 * createBake(*_) >> {
        bake = it[1]
        Calls.response(runningStatus)
      }
    }

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

    when:
    task.execute(pipeline.stages.first())

    then:
    bake.cloudProviderType == BakeRequest.CloudProviderType.aws
    bake.user == bakeConfigWithCloudProviderType.user
    bake.packageName == bakeConfigWithCloudProviderType.package
    bake.baseOs == bakeConfigWithCloudProviderType.baseOs
    bake.baseLabel == bakeConfigWithCloudProviderType.baseLabel
  }

  def "Azure managedImage is propagated"() {
    given:
    def pipeline = pipeline {
      stage {
        type = "bake"
        context = bakeConfigWithAzureManagedImage
      }
    }
    def bake
    def bakery = Mock(BakeryService) {
      1 * createBake(*_) >> {
        bake = it[1]
        Calls.response(runningStatus)
      }
    }

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

    when:
    task.execute(pipeline.stages.first())

    then:
    bake.cloudProviderType == BakeRequest.CloudProviderType.azure
    bake.user == bakeConfigWithAzureManagedImage.user
    bake.packageName == bakeConfigWithAzureManagedImage.package
    bake.baseLabel == bakeConfigWithAzureManagedImage.baseLabel
    bake.custom_managed_image_name == bakeConfigWithAzureManagedImage.managedImage
    bake.osType == bakeConfigWithAzureManagedImage.osType
    bake.packageType == bakeConfigWithAzureManagedImage.packageType as String
    bake.accountName == bakeConfigWithAzureManagedImage.account
  }

  def "Azure defaultImage is propagated"() {
    given:
    def pipeline = pipeline {
      stage {
        type = "bake"
        context = bakeConfigWithAzureDefaultImage
      }
    }
    def bake
    def bakery = Mock(BakeryService) {
      1 * createBake(*_) >> {
        bake = it[1]
        Calls.response(runningStatus)
      }
    }

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

    when:
    task.execute(pipeline.stages.first())

    then:
    bake.cloudProviderType == BakeRequest.CloudProviderType.azure
    bake.user == bakeConfigWithAzureDefaultImage.user
    bake.packageName == bakeConfigWithAzureDefaultImage.package
    bake.baseOs == bakeConfigWithAzureDefaultImage.baseOs
    bake.baseLabel == bakeConfigWithAzureDefaultImage.baseLabel
    bake.packageType == PackageType.DEB as String
    bake.accountName == bakeConfigWithAzureManagedImage.account
  }

  def "Azure customImage is propagated"() {
    given:
    def pipeline = pipeline {
      stage {
        type = "bake"
        context = bakeConfigWithAzureCustomImage
      }
    }
    def bake
    def bakery = Mock(BakeryService) {
      1 * createBake(*_) >> {
        bake = it[1]
        Calls.response(runningStatus)
      }
    }

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
    }

    when:
    task.execute(pipeline.stages.first())

    then:
    bake.cloudProviderType == BakeRequest.CloudProviderType.azure
    bake.user == bakeConfigWithAzureCustomImage.user
    bake.packageName == bakeConfigWithAzureCustomImage.package
    bake.baseLabel == bakeConfigWithAzureCustomImage.baseLabel
    bake.sku == bakeConfigWithAzureCustomImage.sku
    bake.offer == bakeConfigWithAzureCustomImage.offer
    bake.publisher == bakeConfigWithAzureCustomImage.publisher
    bake.osType == bakeConfigWithAzureCustomImage.osType
    bake.packageType == PackageType.NUPKG as String
    bake.accountName == bakeConfigWithAzureManagedImage.account
  }

  @Unroll
  def "sets previouslyBaked flag to #previouslyBaked when status is #status.state"() {
    given:
    def bakery = Stub(BakeryService) {
      createBake(*_) >> Calls.response(status)
    }

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    task.bakerySelector = Mock(BakerySelector) {
      select(_) >> selectedBakeryService
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
    def bakery = Mock(BakeryService)

    and:
    task.bakerySelector = Mock(BakerySelector)
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    when:
    task.execute(stage)

    then:
    1 * task.bakerySelector.select(_) >> selectedBakeryService
    1 * bakery.createBake(bakeConfig.region,
      {
        it.user == "bran" &&
          it.packageName == "hodor" &&
          it.baseLabel == "release" &&
          it.baseOs == "ubuntu"
      } as BakeRequest,
      "1") >> Calls.response(runningStatus)
    0 * _
  }

  @Unroll
  def "sets rebake query parameter to #queryParameter when trigger is #triggerConfig"() {
    given:
    def pipeline = pipeline {
        trigger = mapper.convertValue(triggerConfig, Trigger)
      stage {
        type = "bake"
        context = bakeConfig
      }
    }
    def bakery = Mock(BakeryService)

    and:
    task.bakerySelector = Mock(BakerySelector)
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    when:
    task.execute(pipeline.stages.first())

    then:
    1 * task.bakerySelector.select(_) >> selectedBakeryService
    1 * bakery.createBake(bakeConfig.region,
      {
        it.user == "bran" &&
          it.packageName == "hodor" &&
          it.baseLabel == "release" &&
          it.baseOs == "ubuntu"
      } as BakeRequest,
      queryParameter) >> Calls.response(runningStatus)
    0 * _

    when:
    task.execute(stage {
      type = "bake"
      context = bakeConfig
    })

    then:
    1 * task.bakerySelector.select(_) >> selectedBakeryService
    1 * bakery.createBake(bakeConfig.region,
      {
        it.user == "bran" &&
          it.packageName == "hodor" &&
          it.baseLabel == "release" &&
          it.baseOs == "ubuntu"
      } as BakeRequest,
      null) >> Calls.response(runningStatus)
    0 * _

    where:
    triggerConfig                                                 | queryParameter
    [type: "jenkins", master: "master", job: "job", buildNumber: 1, rebake: true]  | "1"
    [type: "jenkins", master: "master", job: "job", buildNumber: 1, rebake: false] | null
    [type: "jenkins", master: "master", job: "job", buildNumber: 1]                | null
  }

  def "properly resolves package artifacts"() {
    given:
    def stage = stage {
      type = "bake"
      context = bakeConfigWithArtifacts
    }
    task.artifactUtils = Mock(ArtifactUtils)
    def bakery = Mock(BakeryService)

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    when:
    def bakeResult = task.bakeFromContext(stage, selectedBakeryService)

    then:
    2 * task.artifactUtils.getBoundArtifactForId(stage, _) >> Artifact.builder().build()
    1 * task.artifactUtils.getAllArtifacts(_) >> []
    bakeResult.getPackageArtifacts().size() == 2
  }

  def "handles null packageArtifactIds field"() {
    given:
    def stage = stage {
      type = "bake"
      context = bakeConfig
    }
    task.artifactUtils = Mock(ArtifactUtils)
    def bakery = Mock(BakeryService)

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    when:
    def bakeResult = task.bakeFromContext(stage, selectedBakeryService)

    then:
    0 * task.artifactUtils.getBoundArtifactForId(*_) >> Artifact.builder().build()
    1 * task.artifactUtils.getAllArtifacts(_) >> []
    bakeResult.getPackageArtifacts().size() == 0
  }

  def "handles null baseOs field"() {
    given:
    def stage = stage {
      type = "bake"
      context = bakeConfigWithoutOs
    }
    task.artifactUtils = Mock(ArtifactUtils)
    def bakery = Mock(BakeryService)

    and:
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    when:
    def bakeResult = task.bakeFromContext(stage, selectedBakeryService)

    then:
    noExceptionThrown()
    2 * task.artifactUtils.getBoundArtifactForId(stage, _) >> Artifact.builder().build()
    1 * task.artifactUtils.getAllArtifacts(_) >> []
    bakeResult.getPackageArtifacts().size() == 2
  }

  static SpinnakerHttpException makeSpinnakerHttpException(int status) {

    String url = "https://bakery";

    Response retrofit2Response =
        Response.error(
            status,
            ResponseBody.create(
                MediaType.parse("application/json"), "{ \"messages\": [\"Error Message\"]}"))

    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build()

    return new SpinnakerHttpException(retrofit2Response, retrofit)
  }
}
