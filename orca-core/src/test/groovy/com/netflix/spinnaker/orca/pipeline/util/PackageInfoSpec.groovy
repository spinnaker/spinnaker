/*
 * Copyright 2016 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.pipeline.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.orca.pipeline.model.*
import com.netflix.spinnaker.orca.test.model.ExecutionBuilder
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification
import spock.lang.Unroll

import java.util.regex.Pattern

import static com.netflix.spinnaker.orca.pipeline.util.PackageType.DEB
import static com.netflix.spinnaker.orca.pipeline.util.PackageType.RPM
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class PackageInfoSpec extends Specification {

  @Autowired
  ObjectMapper mapper

  @Unroll
  def "Test that the artifact matching method returns whether or not there is a match"() {

    given:
    def pattern = Pattern.compile(artifactPattern)
    def artifacts = artifactFilenames.collect { [fileName: it] }

    expect:
    PackageInfo.artifactMatch(artifacts, [pattern]) == expectedMatch

    where:
    artifactPattern  || artifactFilenames                          || expectedMatch
    "foo.*"          || []                                         || false
    "bat.*"          || [[], []]                                   || false
    "testFileName.*" || ["testFileName_1"]                         || true
    "baz.*"          || [[fileName: "badTestFileName_1"]]          || false
    "testFileName.*" || ["testFileName_1", "testFileName_2"]       || true
    "blah.*"         || ["badTestFileName_1", "badTestFileName_1"] || false
    "testFileName.*" || ["badTestFileName", "testFileName_1"]      || true
    "testFileName.*" || ["testFileName_1", "badTestFileName"]      || true
  }

  def "If no artifacts in current build info or ancestor stages, code should execute as normal"() {

    given:
    def execution = pipeline {
      trigger = new JenkinsTrigger("master", "job", 1, null)
      trigger.buildInfo = new JenkinsBuildInfo("name", 1, "http://jenkins", "SUCCESS", [new JenkinsArtifact("testFileName", ".")])
      stage {
        context = [buildInfo: [name: "someName"], package: "testPackageName"]
      }
    }
    def quipStage = execution.stages.first()

    def packageType = DEB
    def packageInfo =
      new PackageInfo(quipStage, [], packageType.packageType, packageType.versionDelimiter, true, false, new ObjectMapper())

    when:
    packageInfo.findTargetPackage(false)

    then:
    def ex = thrown(IllegalStateException)
    ex.message.startsWith("Unable to find deployable artifact starting with")
  }

  @Unroll
  def "If no artifacts in current build info, find from first ancestor that does"() {
    given:
    def pipeline = pipeline {
      inputFileNames.eachWithIndex { name, i ->
        stage {
          refId = "$i"
          outputs["buildInfo"] = [
            artifacts: [
              [fileName: "${name}.deb".toString(), relativePath: "."],
              [fileName: "${name}.war".toString(), relativePath: "."],
              [fileName: "build.properties", relativePath: "."]
            ],
            url      : "http://localhost",
            name     : "testBuildName",
            number   : "1",
            scm      : []
          ]
        }
      }
      stage {
        refId = "${inputFileNames.size()}"
        context["package"] = "testPackageName"
        requisiteStageRefIds = inputFileNames.indices*.toString()
      }
    }

    def quipStage = pipeline.stages.last()

    PackageInfo packageInfo =
      new PackageInfo(quipStage, [], DEB.packageType, DEB.versionDelimiter, true, false, new ObjectMapper())

    when:
    def requestMap = packageInfo.findTargetPackage(true)

    then:
    requestMap.package == expected

    where:
    inputFileNames                                                                         || expected
    ["testPackageName_1.0abc298"]                                                          || "testPackageName_1.0abc298"
    ["testPackageName_1.0abc298", "badPackageName_1.0abc298"]                              || "testPackageName_1.0abc298"
    ["testPackageName_1.0abc298"]                                                          || "testPackageName_1.0abc298"
    ["testPackageName_1.0abc298", "badPackageName_1.0abc298"]                              || "testPackageName_1.0abc298"
    ["badPackageName_1.0abc298", "testPackageName_1.0abc298"]                              || "testPackageName_1.0abc298"
    ["badPackageName_1.0abc298", "testPackageName_1.0abc298", "badPackageName_1.0abc298"]  || "testPackageName_1.0abc298"
    ["testPackageName_1.0abc298", "badPackageName_1.0abc298", "testPackageName_1.0abc298"] || "testPackageName_1.0abc298"
    ["badPackageName_1.0abc298", "testPackageName_1.0abc298", "testPackageName_2.0abc298"] || "testPackageName_1.0abc298"
    ["testPackageName_1.0abc298", "badPackageName_1.0abc298", "testPackageName_2.0abc298"] || "testPackageName_1.0abc298"
  }

  @Unroll("#filename -> #result")
  def "All the matching packages get replaced with the build ones, while others just pass-through"() {
    given:
    Stage bakeStage = new Stage()
    PackageType packageType = DEB
    boolean extractBuildDetails = false
    PackageInfo packageInfo = new PackageInfo(bakeStage,
      [],
      packageType.packageType,
      packageType.versionDelimiter,
      extractBuildDetails,
      false,
      mapper)
    def allowMissingPackageInstallation = true

    Map trigger = ["buildInfo": ["artifacts": filename]]
    Map buildInfo = ["artifacts": []]
    Map request = ["package": requestPackage]

    when:
    Map requestMap = packageInfo.createAugmentedRequest(trigger, buildInfo, request, allowMissingPackageInstallation)

    then:
    requestMap.package == result

    where:
    filename                                    | requestPackage                                                           | result
    [["fileName": "testEmpty.txt"]]             | null                                                                     | null
    [["fileName": "testEmpty.txt"]]             | ""                                                                       | ""
    [["fileName": "testEmpty2.txt"]]            | "  "                                                                     | ""
    [["fileName": "test-package_1.0.0.deb"]]    | "test-package"                                                           | "test-package_1.0.0"
    [["fileName": "test-package_1.0.0.deb"]]    | "another-package"                                                        | "another-package"
    [["fileName": "test-package_1.0.0.deb"]]    | "another-package test-package"                                           | "another-package test-package_1.0.0"
    [["fileName": "test-package_1.0.0.deb"]]    | "ssh://git@stash.corp.netflix.com:7999/uiplatform/nodequark.git?v1.0.51" | "ssh://git@stash.corp.netflix.com:7999/uiplatform/nodequark.git?v1.0.51"
    [["fileName": "first-package_1.0.1.deb"],
     ["fileName": "second-package_2.3.42.deb"]] | "first-package another-package second-package"                           | "first-package_1.0.1 another-package second-package_2.3.42"

  }

  @Unroll
  def "Find the right package, don't be dependant on artifact order"() {
    given:
    Stage bakeStage = new Stage()
    boolean extractBuildDetails = false
    def allowMissingPackageInstallation = true

    Map trigger = ["buildInfo": ["artifacts": filename]]
    Map buildInfo = ["artifacts": []]
    Map request = ["package": requestPackage]

    when:
    PackageInfo packageInfo = new PackageInfo(bakeStage,
      [],
      packageType.packageType,
      packageType.versionDelimiter,
      extractBuildDetails,
      false,
      mapper)
    Map requestMap = packageInfo.createAugmentedRequest(trigger, buildInfo, request, allowMissingPackageInstallation)

    then:
    requestMap.package == result

    where:
    filename                                       | requestPackage | packageType || result
    [["fileName": "package-4.11.4h-1.x86_64.rpm"]] | "package"      | RPM         || "package-4.11.4h-1.x86_64"
    [["fileName": "package-something-4.11.4h-1.x86_64.rpm"]]                                                 | "package"           | RPM         || "package"
    [["fileName": "package-4.11.4h-1.x86_64.rpm"], ["fileName": "package-something-4.11.4h-1.x86_64.rpm"]]   | "package"           | RPM         || "package-4.11.4h-1.x86_64"
    [["fileName": "package-something-4.11.4h-1.x86_64.rpm"], ["fileName": "package-4.11.4h-1.x86_64.rpm"]]   | "package"           | RPM         || "package-4.11.4h-1.x86_64"
    [["fileName": "package_4.11.4-h02.sha123_amd64.deb"]]                                                    | "package"           | DEB         || "package_4.11.4-h02.sha123_amd64"
    [["fileName": "package-something_4.11.4-h02.sha123_amd64.deb"]]                                          | "package"           | DEB         || "package"
    [["fileName": "package_4.11.4-h02.deb"], ["fileName": "package-something_4.11.4-h02.deb"]]               | "package"           | DEB         || "package_4.11.4-h02"
    [["fileName": "package_4.11.4-h02.sha123.deb"], ["fileName": "package-something_4.11.4-h02.deb"]]        | "package-something" | DEB         || "package-something_4.11.4-h02"
    [["fileName": "package_4.11.4-h02.sha123.deb"], ["fileName": "package-something_4.11.4-h02.sha123.deb"]] | "package"           | DEB         || "package_4.11.4-h02.sha123"
  }

  def "findTargetPackage: bake execution with only a package set and jenkins stage artifacts"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
        type = "jenkins"
        outputs["buildInfo"] = [
          url      : "http://jenkins",
          master   : "master",
          name     : "job",
          number   : 1,
          artifacts: [[fileName: "api_1.1.1-h02.sha123_all.deb", relativePath: "."]],
          scm      : []
        ]
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
        context["package"] = "api"
      }
    }
    def bakeStage = pipeline.stageByRef("2")

    PackageType packageType = DEB
    ObjectMapper objectMapper = new ObjectMapper()
    PackageInfo packageInfo = new PackageInfo(bakeStage, [], packageType.packageType, packageType.versionDelimiter, true, true, objectMapper)

    when:
    Map targetPkg = packageInfo.findTargetPackage(false)

    then:
    targetPkg.packageVersion == "1.1.1-h02.sha123"
  }

  def "findTargetPackage: bake execution with empty package set and jenkins stage artifacts sho"() {
    given:
    def pipeline = pipeline {
      stage {
        type = "bake"
        refId = "1"
        context["package"] = ""
      }
    }
    def bakeStage = pipeline.stageByRef("1")

    PackageType packageType = DEB
    ObjectMapper objectMapper = new ObjectMapper()
    PackageInfo packageInfo = new PackageInfo(bakeStage, [], packageType.packageType, packageType.versionDelimiter, true, true, objectMapper)

    when:
    Map targetPkg = packageInfo.findTargetPackage(false)

    then:
    targetPkg.packageVersion == null
  }

  def "findBuildInfoUpstreamStage: finds artifacts when upstream stages have buildInfo w/o artifacts"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
        type = "jenkins"
        outputs["buildInfo"] = [
          url      : "http://jenkins",
          master   : "master",
          name     : "job",
          number   : 1,
          artifacts: [[fileName: "api_1.1.1-h01.sha123_all.deb", relativePath: "."]],
          scm      : []
        ]
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
        context["package"] = "api"
        outputs["buildInfo"] = [
          url      : "http://jenkins",
          master   : "master",
          name     : "job",
          number   : 1,
          scm      : []
        ]
      }
      stage {
        type = "bake"
        refId = "3"
        requisiteStageRefIds = ["2"]
        context["package"] = "api"
      }
    }

    def bakeStage = pipeline.stageByRef("3")
    PackageType packageType = DEB
    ObjectMapper objectMapper = new ObjectMapper()
    PackageInfo packageInfo = new PackageInfo(bakeStage, [], packageType.packageType, packageType.versionDelimiter, true, true, objectMapper)
    def pattern = Pattern.compile("api.*")

    when:
    def buildInfo = packageInfo.findBuildInfoInUpstreamStage(bakeStage, [pattern])

    then:
    noExceptionThrown()
    buildInfo.containsKey("artifacts")
  }

  def "findTargetPackage: stage execution instance of Pipeline with no trigger"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
        type = "jenkins"
        outputs["buildInfo"] = [
          url      : "http://jenkins",
          master   : "master",
          name     : "job",
          number   : 1,
          artifacts: [[fileName: "api_1.1.1-h01.sha123_all.deb", relativePath: "."]],
          scm      : []
        ]
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
        context["package"] = "api"
      }
    }
    def quipStage = pipeline.stageByRef("2")

    PackageType packageType = DEB
    ObjectMapper objectMapper = new ObjectMapper()
    PackageInfo packageInfo = new PackageInfo(quipStage, [], packageType.packageType, packageType.versionDelimiter, true, true, objectMapper)

    when:
    Map targetPkg = packageInfo.findTargetPackage(false)

    then:
    targetPkg.packageVersion == "1.1.1-h01.sha123"
  }

  @Unroll
  def "findTargetPackage: matched packages are always allowed"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
        type = "jenkins"
        outputs["buildInfo"] = [
          url      : "http://jenkins",
          master   : "master",
          name     : "job",
          number   : 1,
          artifacts: [[fileName: "api_1.1.1-h01.sha123_all.deb", relativePath: "."]],
          scm      : []
        ]
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
        context["package"] = "api"
      }
    }
    def quipStage = pipeline.stageByRef("2")

    PackageType packageType = DEB
    ObjectMapper objectMapper = new ObjectMapper()
    PackageInfo packageInfo = new PackageInfo(quipStage, [], packageType.packageType, packageType.versionDelimiter, true, true, objectMapper)

    when:
    Map targetPkg = packageInfo.findTargetPackage(allowMissingPackageInstallation)

    then:
    targetPkg.packageVersion == packageVersion

    where:
    allowMissingPackageInstallation || packageVersion
    false                           || "1.1.1-h01.sha123"
    true                            || "1.1.1-h01.sha123"
  }

  @Unroll
  def "findTargetPackage: allowing unmatched packages is guarded by the allowMissingPackageInstallation flag"() {
    given:
    def pipeline = pipeline {
      trigger = new JenkinsTrigger("master", "job", 1, null)
      trigger.buildInfo = new JenkinsBuildInfo("name", 1, "http://jenkins", "SUCCESS", [new JenkinsArtifact("api_1.1.1-h01.sha123_all.deb", ".")])
      stage {
        refId = "1"
        context["package"] = "another_package"
      }
    }
    def quipStage = pipeline.stageByRef("1")

    PackageType packageType = DEB
    ObjectMapper objectMapper = new ObjectMapper()
    PackageInfo packageInfo = new PackageInfo(quipStage, [], packageType.packageType, packageType.versionDelimiter, true, true, objectMapper)

    expect:
    Map targetPkg
    try {
      targetPkg = packageInfo.findTargetPackage(allowMissingPackageInstallation)
      assert !expectedException
    } catch (IllegalStateException ex) {
      assert expectedException
      assert ex.message == expectedMessage
      return
    }

    targetPkg.package == "another_package"
    targetPkg.packageVersion == null

    where:
    allowMissingPackageInstallation || expectedException || expectedMessage
    true                            || false             || null
    false                           || true              || "Unable to find deployable artifact starting with [another_package_] and ending with .deb in [] and [api_1.1.1-h01.sha123_all.deb]. Make sure your deb package file name complies with the naming convention: name_version-release_arch."
  }

  def "findTargetPackage: stage execution instance of Pipeline with trigger and no buildInfo"() {
    given:
    def pipeline = pipeline {
      trigger = new JenkinsTrigger("master", "job", 1, null)
      trigger.buildInfo = new JenkinsBuildInfo("name", 1, "http://jenkins", "SUCCESS", [new JenkinsArtifact("api_2.2.2-h02.sha321_all.deb", ".")])
      stage {
        context = [package: 'api']
      }
    }
    def quipStage = pipeline.stages.first()

    PackageType packageType = DEB
    ObjectMapper objectMapper = new ObjectMapper()
    PackageInfo packageInfo = new PackageInfo(quipStage, [], packageType.packageType, packageType.versionDelimiter, true, true, objectMapper)

    when:
    Map targetPkg = packageInfo.findTargetPackage(false)

    then:
    targetPkg.packageVersion == "2.2.2-h02.sha321"
  }

  def "Raise an exception if allowMissingPackageInstallation is false and there's no match"() {
    given:
    Stage bakeStage = new Stage()
    PackageType packageType = DEB
    boolean extractBuildDetails = false
    PackageInfo packageInfo = new PackageInfo(bakeStage,
      [],
      packageType.packageType,
      packageType.versionDelimiter,
      extractBuildDetails,
      false,
      mapper)
    def allowMissingPackageInstallation = false

    Map trigger = ["buildInfo": ["artifacts": [["fileName": "test-package_1.0.0.deb"]]]]
    Map buildInfo = ["artifacts": []]
    Map request = ["package": "foo bar"]

    when:
    packageInfo.createAugmentedRequest(trigger, buildInfo, request, allowMissingPackageInstallation)

    then:
    def exception = thrown(IllegalStateException)
    exception.message == "Unable to find deployable artifact starting with [foo_, bar_] and ending with .deb in [] and [test-package_1.0.0.deb]. Make sure your deb package file name complies with the naming convention: name_version-release_arch."
  }

  @Unroll
  def "getArtifactSourceBuildInfo: get buildInfo from nearest trigger with artifact"() {
    given:
    Stage stage = new Stage(context: [package: "package"])
    PackageInfo packageInfo = new PackageInfo(stage, [], null, null, true, true, null)

    expect:
    packageInfo.getBuildInfoFromTriggerOrParentTrigger(trigger) == buildInfo

    where:
    trigger                                                                                                                                                                      || buildInfo
    [buildInfo: [something: "else"]]                                                                                                                                             || [:]
    [parentExecution: [trigger: [buildInfo: [artifacts: [[fileName: "api_1.1.1-h01.sha123_all.deb"]]]]]]                                                                         || [artifacts: [[fileName: "api_1.1.1-h01.sha123_all.deb"]]]
    [buildInfo: [artifacts: [[fileName: "api_1.1.1-h01.sha123_all.deb"]]]]                                                                                                       || [artifacts: [[fileName: "api_1.1.1-h01.sha123_all.deb"]]]
    [buildInfo: [artifacts: [[fileName: "first_1.1.1-h01.sha123_all.deb"]]], parentExecution: [trigger: [buildInfo: [artifacts: [[fileName: "api_1.1.1-h01.sha123_all.deb"]]]]]] || [artifacts: [[fileName: "first_1.1.1-h01.sha123_all.deb"]]]
  }

  @Unroll
  def "findTargetPackage: get packageVersion from trigger and parentExecution.trigger"() {
    given:
    def pipeline = pipeline {
      trigger = pipelineTrigger
      stage {
        context = [package: 'api']
      }
    }
    def quipStage = pipeline.stages.first()

    PackageType packageType = DEB
    ObjectMapper objectMapper = new ObjectMapper()
    PackageInfo packageInfo = new PackageInfo(quipStage, [], packageType.packageType, packageType.versionDelimiter, true, true, objectMapper)

    when:
    Map targetPkg = packageInfo.findTargetPackage(true)

    then:
    targetPkg.packageVersion == packageVersion

    where:
    packageVersion = "1.1.1-h01.sha123"
    pipelineTrigger << [
      new PipelineTrigger(ExecutionBuilder.pipeline {
        trigger = new JenkinsTrigger("master", "job", 1, null)
        trigger.buildInfo = new JenkinsBuildInfo("name", 1, "http://jenkins", "SUCCESS", [new JenkinsArtifact("api_1.1.1-h01.sha123_all.deb", ".")])
      }),
      new JenkinsTrigger("master", "job", 1, null).with {
        it.buildInfo = new JenkinsBuildInfo("name", 1, "http://jenkins", "SUCCESS", [new JenkinsArtifact("api_1.1.1-h01.sha123_all.deb", ".")])
        it
      }
    ]
  }

  def "should fetch artifacts from upstream stage when not specified on pipeline trigger"() {
    given:
    def jenkinsTrigger = new JenkinsTrigger("master", "job", 1, "propertyFile")
    jenkinsTrigger.buildInfo = new JenkinsBuildInfo("name", 0, "url", "result")

    def pipeline = pipeline {
      trigger = jenkinsTrigger    // has no artifacts!
      stage {
        refId = "1"
        outputs = [
          buildInfo: [
            "artifacts": [
              ["fileName": "spinnaker_0.2.0-114_all.deb"],
              ["fileName": "spinnakerdeps_0.1.0-114_all.deb"]
            ]
          ]
        ]
      }
      stage {
        id = "2"
        requisiteStageRefIds = ["1"]

        stage {
          id = "3"
          context = [
            "package": "spinnakerdeps spinnaker"
          ]
          parentStageId = "2"
        }
      }
    }

    and:
    def packageInfo = new PackageInfo(pipeline.stageById("3"), [], "deb", "_", false, false, new ObjectMapper())

    expect:
    packageInfo.findTargetPackage(false).package == "spinnakerdeps_0.1.0-114_all spinnaker_0.2.0-114_all"
  }

  @Unroll("#requestPackage -> #result")
  def "should consume kork artifact format when only artifacts are present"() {
    given:
    Stage bakeStage = new Stage()
    PackageType packageType = DEB
    boolean extractBuildDetails = false

    Artifact artifact1 = new Artifact.ArtifactBuilder()
      .type("DEB")
      .name("deb-sample-app-server")
      .version("0.0.1~rc.52-h53.96b4f22")
      .reference("debian-local:pool/d/deb-sample-app-server/deb-sample-app-server_0.0.1~rc.52-h53.96b4f22.deb")
      .provenance("https://jenkins/deb-sample-app-build-master")
      .build()
    Artifact artifact2 = new Artifact.ArtifactBuilder()
      .type("DEB")
      .name("my-package")
      .version("0.0.1")
      .reference("debian-local:pool/d/my-package/my-package_0.0.1_all.deb")
      .provenance("https://jenkins/my-package-build-master")
      .build()
    List<Artifact> artifacts = new ArrayList<>()
    artifacts.add(artifact1)
    artifacts.add(artifact2)

    PackageInfo packageInfo = new PackageInfo(bakeStage,
      artifacts,
      packageType.packageType,
      packageType.versionDelimiter,
      extractBuildDetails,
      false,
      mapper)
    def allowMissingPackageInstallation = true

    Map trigger = ["artifacts": artifacts]
    Map buildInfo = [:]
    Map stageContext = ["package": requestPackage]

    when:
    Map returnedStageContext = packageInfo.createAugmentedRequest(trigger, buildInfo, stageContext, allowMissingPackageInstallation)

    then:
    returnedStageContext.package == result

    where:
    requestPackage                         | result
    "deb-sample-app-server"                | "deb-sample-app-server_0.0.1~rc.52-h53.96b4f22"
    "deb-sample-app"                       | "deb-sample-app"
    "deb-sample-app-server deb-sample-app" | "deb-sample-app-server_0.0.1~rc.52-h53.96b4f22 deb-sample-app"
  }

  @Unroll("#requestPackage -> #result")
  def "should consume kork artifact format there are other build and trigger artifacts"() {
    given:
    Stage bakeStage = new Stage()
    PackageType packageType = DEB
    boolean extractBuildDetails = false

    Artifact artifact1 = new Artifact.ArtifactBuilder()
      .type("DEB")
      .name("deb-sample-app-server")
      .version("0.0.1~rc.52-h53.96b4f22")
      .reference("debian-local:pool/d/deb-sample-app-server/deb-sample-app-server_0.0.1~rc.52-h53.96b4f22.deb")
      .provenance("https://jenkins/deb-sample-app-build-master")
      .build()
    List<Artifact> artifacts = new ArrayList<>()
    artifacts.add(artifact1)

    PackageInfo packageInfo = new PackageInfo(bakeStage,
      artifacts,
      packageType.packageType,
      packageType.versionDelimiter,
      extractBuildDetails,
      false,
      mapper)
    def allowMissingPackageInstallation = true

    Map trigger = ["buildInfo": ["artifacts": filename], "artifacts": artifacts]
    Map buildInfo = ["artifacts": [["fileName": "blabla.txt"]]]
    Map stageContext = ["package": requestPackage]

    when:
    Map returnedStageContext = packageInfo.createAugmentedRequest(trigger, buildInfo, stageContext, allowMissingPackageInstallation)

    then:
    returnedStageContext.package == result

    where:
    filename                                 | requestPackage                         | result
    [["fileName": "testEmpty.txt"]]          | "deb-sample-app-server"                | "deb-sample-app-server_0.0.1~rc.52-h53.96b4f22"
    [["fileName": "testEmpty.txt"]]          | "deb-sample-app"                       | "deb-sample-app"
    [["fileName": "test-package_1.0.0.deb"]] | "deb-sample-app-server deb-sample-app" | "deb-sample-app-server_0.0.1~rc.52-h53.96b4f22 deb-sample-app"
  }

  def "should fail if artifact is present with different versions in artifact and either trigger or build info"() {
    given:
    Stage bakeStage = new Stage()
    PackageType packageType = DEB
    boolean extractBuildDetails = false

    Artifact artifact1 = new Artifact.ArtifactBuilder()
      .type("DEB")
      .name("test-package")
      .version("1.0.0")
      .reference("debian-local:pool/d/test-package/test-package_1.0.0.deb")
      .provenance("https://jenkins/test-package-build-master")
      .build()
    List<Artifact> artifacts = new ArrayList<>()
    artifacts.add(artifact1)

    PackageInfo packageInfo = new PackageInfo(bakeStage,
      artifacts,
      packageType.packageType,
      packageType.versionDelimiter,
      extractBuildDetails,
      false,
      mapper)
    def allowMissingPackageInstallation = true

    Map trigger = ["buildInfo": ["artifacts": triggerFilename], "artifacts": artifacts]
    Map buildInfo = ["artifacts": buildFilename]
    Map stageContext = ["package": requestPackage]

    when:
    packageInfo.createAugmentedRequest(trigger, buildInfo, stageContext, allowMissingPackageInstallation)

    then:
    def exception = thrown(IllegalStateException)
    exception.message.contains("build artifact in both")

    where:
    triggerFilename                            | buildFilename                                | requestPackage
    [["fileName": "test-package_2.0.0.deb"]]   | [["fileName": "bla_1.0.0.deb"]]              | "test-package"
    [["fileName": "bla_1.0.0.deb"]]            | [["fileName": "test-package_2.0.0.deb"]]     | "test-package"
  }

  def "should work if the same artifact is present in different places"() {
    given:
    Stage bakeStage = new Stage()
    PackageType packageType = DEB
    boolean extractBuildDetails = false

    Artifact artifact1 = new Artifact.ArtifactBuilder()
      .type("DEB")
      .name("test-package")
      .version("1.0.0")
      .reference("debian-local:pool/d/test-package/test-package_1.0.0.deb")
      .provenance("https://jenkins/test-package-build-master")
      .build()
    List<Artifact> artifacts = new ArrayList<>()
    artifacts.add(artifact1)

    PackageInfo packageInfo = new PackageInfo(bakeStage,
      artifacts,
      packageType.packageType,
      packageType.versionDelimiter,
      extractBuildDetails,
      false,
      mapper)
    def allowMissingPackageInstallation = true

    Map trigger = ["buildInfo": ["artifacts": triggerFilename], "artifacts": artifacts]
    Map buildInfo = ["artifacts": buildFilename]
    Map stageContext = ["package": requestPackage]

    when:
    Map returnedStageContext = packageInfo.createAugmentedRequest(trigger, buildInfo, stageContext, allowMissingPackageInstallation)

    then:
    returnedStageContext.package == result

    where:
    triggerFilename                            | buildFilename                                | requestPackage     | result
    [["fileName": "test-package_1.0.0.deb"]]   | [["fileName": "test-package_1.0.0.deb"]]     | "test-package"     | "test-package_1.0.0"
  }

  def "should work if the same RPM artifact is present in different places"() {
    given:
    Stage bakeStage = new Stage()
    PackageType packageType = RPM
    boolean extractBuildDetails = false

    Artifact artifact1 = new Artifact.ArtifactBuilder()
            .type("rpm")
            .name("test-package")
            .version("1539516142-1.x86_64")
            .reference("test-package-1539516142-1.x86_64.rpm")
            .provenance("https://jenkins/test-package-build-master")
            .build()
    List<Artifact> artifacts = new ArrayList<>()
    artifacts.add(artifact1)

    PackageInfo packageInfo = new PackageInfo(bakeStage,
            artifacts,
            packageType.packageType,
            packageType.versionDelimiter,
            extractBuildDetails,
            false,
            mapper)
    def allowMissingPackageInstallation = true

    Map trigger = ["buildInfo": ["artifacts": triggerFilename], "artifacts": artifacts]
    Map buildInfo = ["artifacts": buildFilename]
    Map stageContext = ["package": requestPackage]

    when:
    Map returnedStageContext = packageInfo.createAugmentedRequest(trigger, buildInfo, stageContext, allowMissingPackageInstallation)

    then:
    returnedStageContext.package == result

    where:
    triggerFilename                            | buildFilename                                | requestPackage     | result
    [["fileName": "test-package-1539516142-1.x86_64.rpm"]]   | [["fileName": "test-package-1539516142-1.x86_64.rpm"]]     | "test-package"     | "test-package-1539516142-1.x86_64"
  }
}
