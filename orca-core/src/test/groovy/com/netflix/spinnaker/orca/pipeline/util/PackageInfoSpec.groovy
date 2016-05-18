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
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification
import spock.lang.Unroll

class PackageInfoSpec extends Specification {

  @Autowired
  ObjectMapper mapper

  @Unroll
  def "All the matching packages get replaced with the build ones, while others just pass-through"() {
    given:
      Stage bakeStage = new PipelineStage()
      PackageType packageType = PackageType.DEB
      boolean extractBuildDetails = false
      PackageInfo packageInfo = new PackageInfo(bakeStage,
        packageType.packageType,
        packageType.versionDelimiter,
        extractBuildDetails,
        false,
        mapper)

      Map trigger = ["buildInfo": ["artifacts": filename]]
      Map buildInfo = ["artifacts": []]
      Map request = ["package": requestPackage, "allowMissingPackageInstallation" : true]

    when:
      Map requestMap = packageInfo.createAugmentedRequest(trigger, buildInfo, request)

    then:
      requestMap.package == result

    where:
      filename                                    | requestPackage                                 | result
      [["fileName": "test-package_1.0.0.deb"]]    | "test-package"                                 | "test-package_1.0.0"
      [["fileName": "test-package_1.0.0.deb"]]    | "another-package"                              | "another-package"
      [["fileName": "test-package_1.0.0.deb"]]    | "another-package test-package"                 | "another-package test-package_1.0.0"
      [["fileName": "test-package_1.0.0.deb"]]    | "ssh://git@stash.corp.netflix.com:7999/uiplatform/nodequark.git?v1.0.51"| "ssh://git@stash.corp.netflix.com:7999/uiplatform/nodequark.git?v1.0.51"
      [["fileName": "first-package_1.0.1.deb"],
       ["fileName": "second-package_2.3.42.deb"]] | "first-package another-package second-package" | "first-package_1.0.1 another-package second-package_2.3.42"

  }

  def "findTargetPackage: stage execution instance of Pipeline with no trigger"() {
    given:
    Stage quipStage = new PipelineStage()
    Pipeline pipeline = new Pipeline()
    pipeline.context << [buildInfo: [artifacts: [[fileName: "api_1.1.1-h01.sha123_all.deb"]]]]
    quipStage.execution = pipeline


    PackageType packageType = PackageType.DEB
    ObjectMapper objectMapper = new ObjectMapper()
    PackageInfo packageInfo = new PackageInfo(quipStage, packageType.packageType, packageType.versionDelimiter, true, true, objectMapper )

    when:
    Map targetPkg = packageInfo.findTargetPackage()

    then:
    targetPkg.packageVersion == "1.1.1-h01.sha123"
  }

  def "findTargetPackage: stage execution instance of Pipeline with trigger and no buildInfo"() {
    given:
    Stage quipStage = new PipelineStage()
    Pipeline pipeline = new Pipeline()
    pipeline.trigger << [buildInfo: [artifacts: [[fileName: "api_2.2.2-h02.sha321_all.deb"]]]]
    quipStage.execution = pipeline


    PackageType packageType = PackageType.DEB
    ObjectMapper objectMapper = new ObjectMapper()
    PackageInfo packageInfo = new PackageInfo(quipStage, packageType.packageType, packageType.versionDelimiter, true, true, objectMapper )

    when:
    Map targetPkg = packageInfo.findTargetPackage()

    then:
    targetPkg.packageVersion == "2.2.2-h02.sha321"
  }

  def "Raise an exception if allowMissingPackageInstallation is false and there's no match"() {
    given:
      Stage bakeStage = new PipelineStage()
      PackageType packageType = PackageType.DEB
      boolean extractBuildDetails = false
      PackageInfo packageInfo = new PackageInfo(bakeStage,
        packageType.packageType,
        packageType.versionDelimiter,
        extractBuildDetails,
        false,
        mapper)

      Map trigger = ["buildInfo": ["artifacts": [["fileName": "test-package_1.0.0.deb"]]]]
      Map buildInfo = ["artifacts": []]
      Map request = ["package": "foo bar", "allowMissingPackageInstallation" : false]

    when:
      packageInfo.createAugmentedRequest(trigger, buildInfo, request)

    then:
      def exception = thrown(IllegalStateException)
      exception.message == "Unable to find deployable artifact starting with [foo_, bar_] and ending with .deb in [] and [[fileName:test-package_1.0.0.deb]]. Make sure your deb package file name complies with the naming convention: name_version-release_arch."
  }
}
