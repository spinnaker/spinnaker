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

package com.netflix.spinnaker.orca.batch.pipeline.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.PackageInfo
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class PackageInfoSpec extends Specification {

  @Subject
  def stage = new Stage<>(context: [package: "package"])
  PackageInfo info = new PackageInfo(stage, 'deb', '_', true, true, Mock(ObjectMapper))

  Map buildInfo = [
    artifacts: [
      [fileName: 'uno_1.0-h1.deb']
    ]
  ]

  Map trigger = [
    buildInfo: [
      artifacts: [
        [
          fileName: 'dos_1.0-h2.deb'
        ]
      ]
    ]

  ]

  @Unroll
  void "should validate urls correctly"() {
    expect:
    info.isUrl(pattern) == expectedResult

    where:
    pattern                                         || expectedResult
    'something'                                     || false
    'http:/a'                                       || false
    'blinkyblahssh:/'                               || false
    'http://www.m.com/artifact.git'                 || true
    'https://www.m.com/artifact.git'                || true
    'ssh://www.m.com/artifact.git'                  || true
    'ssh://www.m.com/artifact.git?jafsdklsdfaj9adf' || true
  }

  void "should skip package validation if a url is provided as package"() {
    Map request = ['package': 'http://www.m.com']
    expect:
    info.createAugmentedRequest(trigger, buildInfo, request, false).package == request.package
  }

  void "should resolve the correct package"() {
    Map request = ['package': 'dos']
    expect:
    info.createAugmentedRequest(trigger, buildInfo, request, false).package == 'dos_1.0-h2'
  }



  @Unroll
  void "should throw an exception when a trigger has no artifacts"() {
    when:
    Map request = ['package': 'dos']
    info.createAugmentedRequest(providedTrigger, null, request, false)

    then:
    def exception = thrown(IllegalStateException)
    exception.message == "Jenkins job detected but no artifacts found, please archive the packages in your job and try again."

    where:
    buildInfo       | providedTrigger
    null            | [buildInfo: [buildNumber: 6]]
    null            | [buildInfo: [buildNumber: 6, artifacts: []]]
    [artifacts: []] | [buildInfo: [buildNumber: 6]]
    null            | [parentExecution: [trigger: [buildInfo: [buildNumber: 6]]]]
    null            | [buildInfo: [buildNumber: 6], parentExecution: [trigger: [buildInfo: [buildNumber: 6]]]]
  }

}
