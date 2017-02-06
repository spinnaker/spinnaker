package com.netflix.spinnaker.rosco.providers.util

import com.netflix.spinnaker.rosco.api.BakeRequest

trait TestDefaults {

  static final String PACKAGES_NAME = "kato nflx-djangobase-enhanced_0.1-h12.170cdbd_all mongodb"
  static final String DEBIAN_REPOSITORY = "http://some-debian-repository"
  static final String YUM_REPOSITORY = "http://some-yum-repository"
  static final BakeRequest.PackageType DEB_PACKAGE_TYPE = BakeRequest.PackageType.DEB
  static final BakeRequest.PackageType RPM_PACKAGE_TYPE = BakeRequest.PackageType.RPM
  static final String SOME_MILLISECONDS = "1470391070464"
  static final String SOME_UUID = "55c25239-4de5-4f7a-b664-6070a1389680"
  static final String SOME_BUILD_INFO_URL = "http://some-build-server:8080/repogroup/repo/builds/320282"
  static final String SOME_COMMIT_HASH = "170cdbd"
  static final String SOME_DOCKER_TAG = "latest"

  def parseDebOsPackageNames(String packages) {
    PackageNameConverter.buildOsPackageNames(DEB_PACKAGE_TYPE, packages.tokenize(" "))
  }

  def parseRpmOsPackageNames(String packages) {
    PackageNameConverter.buildOsPackageNames(RPM_PACKAGE_TYPE, packages.tokenize(" "))
  }
}
