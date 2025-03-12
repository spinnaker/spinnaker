/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.rosco.providers.util

import spock.lang.Specification

class PackerManifestSpec extends Specification implements TestDefaults {
  void "getLastBuild"() {
    setup:
      def firstBuild = new PackerManifest.PackerBuild(packerRunUuid: UUID.randomUUID().toString())
      def secondBuild = new PackerManifest.PackerBuild(packerRunUuid: UUID.randomUUID().toString())
      PackerManifest manifest

    when:
      manifest = new PackerManifest(builds: [firstBuild, secondBuild], lastRunUuid: firstBuild.getPackerRunUuid())
    then:
      manifest.getLastBuild() == firstBuild

    when:
      manifest = new PackerManifest(builds: [firstBuild, secondBuild], lastRunUuid: secondBuild.getPackerRunUuid())
    then:
      manifest.getLastBuild() == secondBuild

    when:
      manifest = new PackerManifest(builds: [firstBuild, secondBuild], lastRunUuid: UUID.randomUUID().toString())
      manifest.getLastBuild()
    then:
      thrown(IllegalStateException)

    when:
      manifest = new PackerManifest(builds: [], lastRunUuid: UUID.randomUUID().toString())
      manifest.getLastBuild()
    then:
      thrown(IllegalStateException)
  }
}
