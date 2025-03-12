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

class PackageNameConverterSpec extends Specification implements TestDefaults {

  void "package type is respected when building app version string"() {
    setup:
      def debPackageName = "nflx-djangobase-enhanced_0.1-h12.170cdbd_all"
      def rpmPackageName = "nflx-djangobase-enhanced-0.1-h12.170cdbd.all"
      def appVersionStr = "nflx-djangobase-enhanced-0.1-h12.170cdbd"

    when:
      def parsedDebPackageName = PackageNameConverter.buildOsPackageName(DEB_PACKAGE_TYPE, debPackageName)
      def parsedRpmPackageName = PackageNameConverter.buildOsPackageName(RPM_PACKAGE_TYPE, rpmPackageName)
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
