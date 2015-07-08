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
  PackageInfo info = new PackageInfo(Mock(Stage), 'deb', '_', false, true, Mock(ObjectMapper))

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
    info.createAugmentedRequest(trigger, buildInfo, request).package == request.package
  }

  void "should resolve the correct package"() {
    Map request = ['package': 'dos']
    expect:
    info.createAugmentedRequest(trigger, buildInfo, request).package == 'dos_1.0-h2'
  }

}
