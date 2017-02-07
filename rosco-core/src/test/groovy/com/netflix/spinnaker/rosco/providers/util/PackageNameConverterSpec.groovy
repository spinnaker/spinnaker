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

class PackageNameConverterSpec extends Specification implements TestDefaults {
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
                                                                                              release: "h12.170cdbd")
      "nflx-djangobase-enhanced_0.1-3"               | new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced",
                                                                                              version: "0.1",
                                                                                              release: "3")
      "nflx-djangobase-enhanced_0.1-h12.170cdbd_all" | new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced",
                                                                                              version: "0.1",
                                                                                              release: "h12.170cdbd",
                                                                                              arch: "all")
      "nflx-djangobase-enhanced_0.1-3_all"           | new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced",
                                                                                              version: "0.1",
                                                                                              release: "3",
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
      "billinggateway-1.0-h2385.e0a09ce.all"         | new PackageNameConverter.OsPackageName(name: "billinggateway",
                                                                                              version: "1.0",
                                                                                              release: "h2385.e0a09ce",
                                                                                              arch: "all")
      "nflx-djangobase-enhanced-0.1-h12.170cdbd.all" | new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced",
                                                                                              version: "0.1",
                                                                                              release: "h12.170cdbd",
                                                                                              arch: "all")
      "sf-lucifer-0.0.10-1.noarch"                   | new PackageNameConverter.OsPackageName(name: "sf-lucifer",
                                                                                              version: "0.0.10",
                                                                                              release: "1",
                                                                                              arch: "noarch")
  }

  @Unroll
  void "nupkg package names are properly parsed"() {
    when:
      def osPackageName = PackageNameConverter.parseNupkgPackageName(packageName)

    then:
      osPackageName == expectedOsPackageName

    where:
      packageName                                    | expectedOsPackageName
      null                                           | new PackageNameConverter.OsPackageName()
      ""                                             | new PackageNameConverter.OsPackageName()
      "billinggateway.1.0.1"                         | new PackageNameConverter.OsPackageName(name: "billinggateway",
                                                                                              version: "1.0.1",
                                                                                              release: null,
                                                                                              arch: null)
      "nflx-djangobase-enhanced.1.0.1-rc1"           | new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced",
                                                                                              version: "1.0.1",
                                                                                              release: "rc1",
                                                                                              arch: null)
      "sf-lucifer.en-US.0.0.10-1"                    | new PackageNameConverter.OsPackageName(name: "sf-lucifer.en-US",
                                                                                              version: "0.0.10",
                                                                                              release: "1",
                                                                                              arch: null)
      "microsoft.aspnet.mvc"                         | new PackageNameConverter.OsPackageName(name: "microsoft.aspnet.mvc",
                                                                                              version: null,
                                                                                              release: null,
                                                                                              arch: null)
      "microsoft.aspnet.mvc.6"                       | new PackageNameConverter.OsPackageName(name: "microsoft.aspnet.mvc",
                                                                                              version: "6",
                                                                                              release: null,
                                                                                              arch: null)
      "microsoft.aspnet.mvc.6-rc1-final"             | new PackageNameConverter.OsPackageName(name: "microsoft.aspnet.mvc",
                                                                                              version: "6",
                                                                                              release: "rc1-final",
                                                                                              arch: null)
      "microsoft.aspnet.mvc.6.0.0+sf23sdf"           | new PackageNameConverter.OsPackageName(name: "microsoft.aspnet.mvc",
                                                                                              version: "6.0.0",
                                                                                              release: "+sf23sdf",
                                                                                              arch: null)
      "microsoft.aspnet.mvc.6.0.0-rc1-final+sf23sdf" | new PackageNameConverter.OsPackageName(name: "microsoft.aspnet.mvc",
                                                                                              version: "6.0.0",
                                                                                              release: "rc1-final+sf23sdf",
                                                                                              arch: null)
      "microsoft-aspnet-mvc"                         | new PackageNameConverter.OsPackageName(name: "microsoft-aspnet-mvc",
                                                                                              version: null,
                                                                                              release: null,
                                                                                              arch: null)
      "microsoft-aspnet-mvc.6"                       | new PackageNameConverter.OsPackageName(name: "microsoft-aspnet-mvc",
                                                                                              version: "6",
                                                                                              release: null,
                                                                                              arch: null)
      "microsoft-aspnet-mvc.6-rc1-final"             | new PackageNameConverter.OsPackageName(name: "microsoft-aspnet-mvc",
                                                                                              version: "6",
                                                                                              release: "rc1-final",
                                                                                              arch: null)
      "microsoft-aspnet-mvc.6.0.0+sf23sdf"           | new PackageNameConverter.OsPackageName(name: "microsoft-aspnet-mvc",
                                                                                              version: "6.0.0",
                                                                                              release: "+sf23sdf",
                                                                                              arch: null)
      "microsoft-aspnet-mvc.6.0.0-rc1-final+sf23sdf" | new PackageNameConverter.OsPackageName(name: "microsoft-aspnet-mvc",
                                                                                              version: "6.0.0",
                                                                                              release: "rc1-final+sf23sdf",
                                                                                              arch: null)

  }

  void "package type is respected when building app version string"() {
    setup:
      def debPackageName = "nflx-djangobase-enhanced_0.1-h12.170cdbd_all"
      def rpmPackageName = "nflx-djangobase-enhanced-0.1-h12.170cdbd.all"
      def appVersionStr = "nflx-djangobase-enhanced-0.1-h12.170cdbd"

    when:
      def parsedDebPackageName = PackageNameConverter.parseDebPackageName(debPackageName)
      def parsedRpmPackageName = PackageNameConverter.parseRpmPackageName(rpmPackageName)
      def appVersionStrFromDebPackageName = PackageNameConverter.buildAppVersionStr(
        new BakeRequest(base_os: "ubuntu",
                        build_number: "12",
                        commit_hash: "170cdbd"),
        parsedDebPackageName,
        DEB_PACKAGE_TYPE)
      def appVersionStrFromRpmPackageName = PackageNameConverter.buildAppVersionStr(
        new BakeRequest(base_os: "centos",
                        build_number: "12",
                        commit_hash: "170cdbd"),
        parsedRpmPackageName,
        RPM_PACKAGE_TYPE)

    then:
      parsedDebPackageName == parsedRpmPackageName
      appVersionStrFromDebPackageName == appVersionStr
      appVersionStrFromRpmPackageName == appVersionStr
  }

  void "if job and build_number are specified, app version string includes them"() {
    setup:
      def debPackageName = "nflx-djangobase-enhanced_0.1-h123.170cdbd_all"
      def appVersionStr = "nflx-djangobase-enhanced-0.1-h123.170cdbd/some-job-name/123"
      def packageType = DEB_PACKAGE_TYPE
      def bakeRequest = new BakeRequest(base_os: "ubuntu",
                                        job: "some-job-name",
                                        build_number: "123",
                                        commit_hash: "170cdbd")

    when:
      def parsedDebPackageName = PackageNameConverter.buildOsPackageName(packageType, debPackageName)
      def appVersionStrFromDebPackageName = PackageNameConverter.buildAppVersionStr(bakeRequest, parsedDebPackageName, packageType)

    then:
      appVersionStrFromDebPackageName == appVersionStr
  }

  void "if job name contains slashes, they are replaced by hyphen"() {
    setup:
      def debPackageName = "nflx-djangobase-enhanced_0.1-h123.170cdbd_all"
      def appVersionStr = "nflx-djangobase-enhanced-0.1-h123.170cdbd/compose-job-name/123"
      def packageType = DEB_PACKAGE_TYPE
      def bakeRequest = new BakeRequest(base_os: "ubuntu", job: "compose/job/name", build_number: "123", commit_hash: "170cdbd")

    when:
      def parsedDebPackageName = PackageNameConverter.buildOsPackageName(packageType, debPackageName)
      def appVersionStrFromDebPackageName = PackageNameConverter.buildAppVersionStr(bakeRequest, parsedDebPackageName, packageType)

    then:
      appVersionStrFromDebPackageName == appVersionStr
  }

  void "if job is missing, app version string leaves off both job and build_number"() {
    setup:
      def debPackageName = "nflx-djangobase-enhanced_0.1-h12.170cdbd_all"
      def packageType = DEB_PACKAGE_TYPE
      def appVersionStr = "nflx-djangobase-enhanced-0.1-h12.170cdbd"

    when:
      def bakeRequest = new BakeRequest(base_os: "ubuntu",
                                        build_number: "12",
                                        commit_hash: "170cdbd")
      def parsedDebPackageName = PackageNameConverter.buildOsPackageName(packageType, debPackageName)
      def appVersionStrFromDebPackageName = PackageNameConverter.buildAppVersionStr(bakeRequest, parsedDebPackageName, packageType)

    then:
      appVersionStrFromDebPackageName == appVersionStr
  }

  void "if package_name is empty we should have an empty list of OsPackageNames"() {
    setup:
      def debPackageNames = []
      def packageType = DEB_PACKAGE_TYPE

    when:
      def parsedDebPackageNames = PackageNameConverter.buildOsPackageNames(packageType, debPackageNames)

    then:
      parsedDebPackageNames.empty
  }

}
