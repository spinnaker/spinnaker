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

import java.util.regex.Pattern
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.pipeline.util.PackageType.DEB

class PackageInfoSpec extends Specification {

  @Autowired
  ObjectMapper mapper

  @Unroll
  def "Test that the artifact matching method returns whether or not there is a match"() {

    given:
    def pattern = Pattern.compile(artifactPattern)
    def artifacts = artifactFilenames.collect { [fileName: it] }

    expect:
    PackageInfo.artifactMatch(artifacts, pattern) == expectedMatch

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
    def quipStage = new Stage<>(
      execution: Pipeline.builder().withTrigger(["buildInfo": ["artifacts": [["fileName": "testFileName"]]]]).build(),
      context: [buildInfo: [name: "someName"], package: "testPackageName"]
    )

    def packageType = DEB
    def packageInfo =
      new PackageInfo(quipStage, packageType.packageType, packageType.versionDelimiter, true, false, new ObjectMapper())

    when:
    packageInfo.findTargetPackage(false)

    then:
    def ex = thrown(IllegalStateException)
    ex.message.startsWith("Unable to find deployable artifact starting with")
  }

  @Unroll
  def "If no artifacts in current build info, find from first ancestor that does"() {
    given:
    def pipeline = new Pipeline()
    def ancestorStages = inputFileNames.collect {
      new Stage<>(
        execution: pipeline,
        refId: it,
        context: [
          buildInfo: [
            artifacts: [
              [fileName: "${it}.deb"],
              [fileName: "${it}.war"],
              [fileName: "build.properties"]
            ],
            url      : "http://localhost",
            name     : "testBuildName",
            number   : "1"
          ]
        ]
      )
    }
    Stage quipStage = new Stage<>(pipeline, "type", "name", [buildInfo: [name: "someName"], package: "testPackageName"]).with {
      it.requisiteStageRefIds = ancestorStages.refId
      return it
    }
    (ancestorStages + [quipStage]).each {
      pipeline.stages << it
    }

    PackageInfo packageInfo =
      new PackageInfo(quipStage, DEB.packageType, DEB.versionDelimiter, true, false, new ObjectMapper())

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
    Stage bakeStage = new Stage<>()
    PackageType packageType = DEB
    boolean extractBuildDetails = false
    PackageInfo packageInfo = new PackageInfo(bakeStage,
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
    Stage bakeStage = new Stage<>()
    boolean extractBuildDetails = false
    def allowMissingPackageInstallation = true

    Map trigger = ["buildInfo": ["artifacts": filename]]
    Map buildInfo = ["artifacts": []]
    Map request = ["package": requestPackage]

    when:
    PackageInfo packageInfo = new PackageInfo(bakeStage,
      packageType.packageType,
      packageType.versionDelimiter,
      extractBuildDetails,
      false,
      mapper)
    Map requestMap = packageInfo.createAugmentedRequest(trigger, buildInfo, request, allowMissingPackageInstallation)

    then:
    requestMap.package == result

    where:
    filename                                                        || requestPackage      || packageType       || result
    [["fileName": "package-4.11.4h-1.x86_64.rpm"]]                  || "package"           || PackageType.RPM   || "package-4.11.4h-1.x86_64"
    [["fileName": "package-something-4.11.4h-1.x86_64.rpm"]]        || "package"           || PackageType.RPM   || "package"
    [["fileName": "package-4.11.4h-1.x86_64.rpm"],
     ["fileName": "package-something-4.11.4h-1.x86_64.rpm"]]        || "package"           || PackageType.RPM   || "package-4.11.4h-1.x86_64"
    [["fileName": "package-something-4.11.4h-1.x86_64.rpm"],
     ["fileName": "package-4.11.4h-1.x86_64.rpm"]]                  || "package"           || PackageType.RPM   || "package-4.11.4h-1.x86_64"
    [["fileName": "package_4.11.4-h02.sha123_amd64.deb"]]           || "package"           || DEB               || "package_4.11.4-h02.sha123_amd64"
    [["fileName": "package-something_4.11.4-h02.sha123_amd64.deb"]] || "package"           || DEB               || "package"
    [["fileName": "package_4.11.4-h02.deb"],
     ["fileName": "package-something_4.11.4-h02.deb"]]              || "package"           || DEB               || "package_4.11.4-h02"
    [["fileName": "package_4.11.4-h02.sha123.deb"],
     ["fileName": "package-something_4.11.4-h02.deb"]]              || "package-something" || DEB               || "package-something_4.11.4-h02"
    [["fileName": "package_4.11.4-h02.sha123.deb"],
     ["fileName": "package-something_4.11.4-h02.sha123.deb"]]       || "package"           || DEB               || "package_4.11.4-h02.sha123"
    [["fileName": "package.4.11.4-1+x86_64.nupkg"]]                 || "package"           || PackageType.NUPKG || "package.4.11.4-1+x86_64"
    [["fileName": "package-something.4.11.4-1+x86_64.nupkg"]]       || "package-something" || PackageType.NUPKG || "package-something.4.11.4-1+x86_64"
    [["fileName": "package.4.11.4-1+x86_64.nupkg"],
     ["fileName": "package-something.4.11.4-1+x86_64.nupkg"]]       || "package-something" || PackageType.NUPKG || "package-something.4.11.4-1+x86_64"
    [["fileName": "package-something.4.11.4-1+x86_64.nupkg"],
     ["fileName": "package.4.11.4-1+x86_64.nupkg"]]                 || "package"           || PackageType.NUPKG || "package.4.11.4-1+x86_64"

  }

  def "findTargetPackage: bake execution with only a package set and jenkins stage artifacts"() {
    given:
    Stage bakeStage = new Stage<>()
    Pipeline pipeline = new Pipeline()
    pipeline.context << [buildInfo: [artifacts: [[fileName: "api_1.1.1-h02.sha123_all.deb"]]]]
    bakeStage.execution = pipeline
    bakeStage.context = [package: 'api']


    PackageType packageType = DEB
    ObjectMapper objectMapper = new ObjectMapper()
    PackageInfo packageInfo = new PackageInfo(bakeStage, packageType.packageType, packageType.versionDelimiter, true, true, objectMapper)

    when:
    Map targetPkg = packageInfo.findTargetPackage(false)

    then:
    targetPkg.packageVersion == "1.1.1-h02.sha123"
  }

  def "findTargetPackage: bake execution with empty package set and jenkins stage artifacts sho"() {
    given:
    Stage bakeStage = new Stage<>()
    Pipeline pipeline = new Pipeline()
    pipeline.context << [buildInfo: [artifacts: [[fileName: "api_1.1.1-h03.sha123_all.deb"]]]]
    bakeStage.execution = pipeline
    bakeStage.context = [package: '']


    PackageType packageType = DEB
    ObjectMapper objectMapper = new ObjectMapper()
    PackageInfo packageInfo = new PackageInfo(bakeStage, packageType.packageType, packageType.versionDelimiter, true, true, objectMapper)

    when:
    Map targetPkg = packageInfo.findTargetPackage(false)

    then:
    targetPkg.packageVersion == null
  }

  def "findTargetPackage: stage execution instance of Pipeline with no trigger"() {
    given:
    Stage quipStage = new Stage<>()
    Pipeline pipeline = new Pipeline()
    pipeline.context << [buildInfo: [artifacts: [[fileName: "api_1.1.1-h01.sha123_all.deb"]]]]
    quipStage.execution = pipeline
    quipStage.context = [package: 'api']

    PackageType packageType = DEB
    ObjectMapper objectMapper = new ObjectMapper()
    PackageInfo packageInfo = new PackageInfo(quipStage, packageType.packageType, packageType.versionDelimiter, true, true, objectMapper)

    when:
    Map targetPkg = packageInfo.findTargetPackage(false)

    then:
    targetPkg.packageVersion == "1.1.1-h01.sha123"
  }

  @Unroll
  def "findTargetPackage: matched packages are always allowed"() {
    given:
    Stage quipStage = new Stage<>()
    Pipeline pipeline = new Pipeline()
    pipeline.context << [buildInfo: [artifacts: [[fileName: "api_1.1.1-h01.sha123_all.deb"]]]]
    quipStage.execution = pipeline
    quipStage.context = ['package': "api"]

    PackageType packageType = DEB
    ObjectMapper objectMapper = new ObjectMapper()
    PackageInfo packageInfo = new PackageInfo(quipStage, packageType.packageType, packageType.versionDelimiter, true, true, objectMapper)

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
    Stage quipStage = new Stage<>()
    Pipeline pipeline = new Pipeline()
    pipeline.context << [buildInfo: [artifacts: [[fileName: "api_1.1.1-h01.sha123_all.deb"]]]]
    quipStage.execution = pipeline
    quipStage.context = ['package': "another_package"]

    PackageType packageType = DEB
    ObjectMapper objectMapper = new ObjectMapper()
    PackageInfo packageInfo = new PackageInfo(quipStage, packageType.packageType, packageType.versionDelimiter, true, true, objectMapper)

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
    false                           || true              || "Unable to find deployable artifact starting with [another_package_] and ending with .deb in [[fileName:api_1.1.1-h01.sha123_all.deb]] and null. Make sure your deb package file name complies with the naming convention: name_version-release_arch."
  }

  def "findTargetPackage: stage execution instance of Pipeline with trigger and no buildInfo"() {
    given:
    Stage quipStage = new Stage<>()
    Pipeline pipeline = new Pipeline()
    pipeline.trigger << [buildInfo: [artifacts: [[fileName: "api_2.2.2-h02.sha321_all.deb"]]]]
    quipStage.execution = pipeline
    quipStage.context = [package: 'api']

    PackageType packageType = DEB
    ObjectMapper objectMapper = new ObjectMapper()
    PackageInfo packageInfo = new PackageInfo(quipStage, packageType.packageType, packageType.versionDelimiter, true, true, objectMapper)

    when:
    Map targetPkg = packageInfo.findTargetPackage(false)

    then:
    targetPkg.packageVersion == "2.2.2-h02.sha321"
  }

  def "Raise an exception if allowMissingPackageInstallation is false and there's no match"() {
    given:
    Stage bakeStage = new Stage<>()
    PackageType packageType = DEB
    boolean extractBuildDetails = false
    PackageInfo packageInfo = new PackageInfo(bakeStage,
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
    exception.message == "Unable to find deployable artifact starting with [foo_, bar_] and ending with .deb in [] and [[fileName:test-package_1.0.0.deb]]. Make sure your deb package file name complies with the naming convention: name_version-release_arch."
  }

  @Unroll
  def "getArtifactSourceBuildInfo: get buildInfo from nearest trigger with artifact"() {
    given:
    Stage stage = new Stage<>(context: [package: "package"])
    PackageInfo packageInfo = new PackageInfo(stage, null, null, true, true, null)

    when:
    Map artifactSourceBuildInfo = packageInfo.getArtifactSourceBuildInfo(trigger)

    then:
    buildInfo == artifactSourceBuildInfo

    where:
    trigger     || buildInfo
    [buildInfo: [
      something: "else"
    ]]          || null
    [parentExecution: [
      trigger: [
        buildInfo: [
          artifacts: [
            [fileName: "api_1.1.1-h01.sha123_all.deb"]
          ]]]]] || [artifacts: [[fileName: "api_1.1.1-h01.sha123_all.deb"]]]
    [buildInfo: [
      artifacts: [
        [fileName: "api_1.1.1-h01.sha123_all.deb"]
      ]]]       || [artifacts: [[fileName: "api_1.1.1-h01.sha123_all.deb"]]]
    [
      buildInfo      : [
        artifacts: [
          [fileName: "first_1.1.1-h01.sha123_all.deb"]
        ]],
      parentExecution: [
        trigger: [
          buildInfo: [
            artifacts: [
              [fileName: "api_1.1.1-h01.sha123_all.deb"]
            ]]]]
    ]           || [artifacts: [[fileName: "first_1.1.1-h01.sha123_all.deb"]]]
  }

  @Unroll
  def "findTargetPackage: get packageVersion from trigger and parentExecution.trigger"() {
    given:
    Stage quipStage = new Stage<>()
    Pipeline pipeline = new Pipeline()
    pipeline.trigger << trigger
    quipStage.execution = pipeline
    quipStage.context = ['package': "api"]

    PackageType packageType = DEB
    ObjectMapper objectMapper = new ObjectMapper()
    PackageInfo packageInfo = new PackageInfo(quipStage, packageType.packageType, packageType.versionDelimiter, true, true, objectMapper)

    when:
    Map targetPkg = packageInfo.findTargetPackage(true)

    then:
    targetPkg.packageVersion == packageVersion

    where:
    trigger     || packageVersion
    [parentExecution: [
      trigger: [
        buildInfo: [
          artifacts: [
            [fileName: "api_1.1.1-h01.sha123_all.deb"]
          ]]]]] || "1.1.1-h01.sha123"
    [buildInfo: [
      artifacts: [
        [fileName: "api_1.1.1-h01.sha123_all.deb"]
      ]]]       || "1.1.1-h01.sha123"
  }
}
