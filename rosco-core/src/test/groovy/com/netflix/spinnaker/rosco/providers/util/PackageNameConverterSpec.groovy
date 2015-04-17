/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.rosco.providers.util

import com.netflix.spinnaker.rosco.api.BakeRequest
import spock.lang.Specification
import spock.lang.Unroll

class PackageNameConverterSpec extends Specification {
  @Unroll
  void "deb package names are properly parsed"() {
    when:
      def osPackageName = PackageNameConverter.parseDebPackageName(packageName)

    then:
      osPackageName == expectedOsPackageName

    where:
      packageName                                    | expectedOsPackageName
      null                                           | new PackageNameConverter.OsPackageName()
      ""                                             | new PackageNameConverter.OsPackageName()
      "nflx-djangobase-enhanced"                     | new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced")
      "nflx-djangobase-enhanced_0.1"                 | new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced",
                                                                                              version: "0.1")
      "nflx-djangobase-enhanced_0.1-h12.170cdbd"     | new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced",
                                                                                              version: "0.1",
                                                                                              release: "h12.170cdbd",
                                                                                              buildNumber: "h12",
                                                                                              commit: "170cdbd")
      "nflx-djangobase-enhanced_0.1-h12.170cdbd_all" | new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced",
                                                                                              version: "0.1",
                                                                                              release: "h12.170cdbd",
                                                                                              buildNumber: "h12",
                                                                                              commit: "170cdbd",
                                                                                              arch: "all")
  }

  @Unroll
  void "rpm package names are properly parsed"() {
    when:
      def osPackageName = PackageNameConverter.parseRpmPackageName(packageName)

    then:
      osPackageName == expectedOsPackageName

    where:
      packageName                                    | expectedOsPackageName
      null                                           | new PackageNameConverter.OsPackageName()
      ""                                             | new PackageNameConverter.OsPackageName()
      "billinggateway-1.0-h2385.e0a09ce-all"         | new PackageNameConverter.OsPackageName(name: "billinggateway",
                                                                                              version: "1.0",
                                                                                              release: "h2385.e0a09ce",
                                                                                              buildNumber: "h2385",
                                                                                              commit: "e0a09ce",
                                                                                              arch: "all")
      "nflx-djangobase-enhanced-0.1-h12.170cdbd-all" | new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced",
                                                                                              version: "0.1",
                                                                                              release: "h12.170cdbd",
                                                                                              buildNumber: "h12",
                                                                                              commit: "170cdbd",
                                                                                              arch: "all")
  }

  void "package type is respected when building app version string"() {
    setup:
      def debPackageName = "nflx-djangobase-enhanced_0.1-h12.170cdbd_all"
      def rpmPackageName = "nflx-djangobase-enhanced-0.1-h12.170cdbd-all"
      def appVersionStr = "nflx-djangobase-enhanced-0.1-170cdbd.h12"

    when:
      def parsedDebPackageName = PackageNameConverter.parseDebPackageName(debPackageName)
      def parsedRpmPackageName = PackageNameConverter.parseRpmPackageName(rpmPackageName)
      def appVersionStrFromDebPackageName =
        PackageNameConverter.buildAppVersionStr(new BakeRequest(base_os: BakeRequest.OperatingSystem.ubuntu), debPackageName)
      def appVersionStrFromRpmPackageName =
        PackageNameConverter.buildAppVersionStr(new BakeRequest(base_os: BakeRequest.OperatingSystem.centos), rpmPackageName)

    then:
      parsedDebPackageName == parsedRpmPackageName
      appVersionStrFromDebPackageName == appVersionStr
      appVersionStrFromRpmPackageName == appVersionStr
  }

  void "if job and build_number are specified, app version string includes them"() {
    setup:
      def debPackageName = "nflx-djangobase-enhanced_0.1-h12.170cdbd_all"
      def appVersionStr = "nflx-djangobase-enhanced-0.1-170cdbd.h12/some-job-name/123"
      def bakeRequest = new BakeRequest(base_os: BakeRequest.OperatingSystem.ubuntu,
                                        job: "some-job-name",
                                        build_number: "123")

    when:
      def appVersionStrFromDebPackageName = PackageNameConverter.buildAppVersionStr(bakeRequest, debPackageName)

    then:
      appVersionStrFromDebPackageName == appVersionStr
  }

  void "if either job or build_number are missing, app version string leaves off both job and build_number"() {
    setup:
      def debPackageName = "nflx-djangobase-enhanced_0.1-h12.170cdbd_all"
      def appVersionStr = "nflx-djangobase-enhanced-0.1-170cdbd.h12"

    when:
      def bakeRequest = new BakeRequest(base_os: BakeRequest.OperatingSystem.ubuntu, job: "some-job-name")
      def appVersionStrFromDebPackageName = PackageNameConverter.buildAppVersionStr(bakeRequest, debPackageName)

    then:
      appVersionStrFromDebPackageName == appVersionStr

    when:
      bakeRequest = new BakeRequest(base_os: BakeRequest.OperatingSystem.ubuntu, build_number: "123")
      appVersionStrFromDebPackageName = PackageNameConverter.buildAppVersionStr(bakeRequest, debPackageName)

    then:
      appVersionStrFromDebPackageName == appVersionStr
  }

}
