package com.netflix.spinnaker.rosco.providers.util

import com.netflix.spinnaker.rosco.api.BakeRequest

trait TestDefaults {

  static final String PACKAGES_NAME = "kato nflx-djangobase-enhanced_0.1-h12.170cdbd_all mongodb"
  static final String DEBIAN_REPOSITORY = "http://some-debian-repository"
  static final String YUM_REPOSITORY = "http://some-yum-repository"
  static final BakeRequest.PackageType DEB_PACKAGE_TYPE = BakeRequest.PackageType.DEB
  static final BakeRequest.PackageType RPM_PACKAGE_TYPE = BakeRequest.PackageType.RPM

  def parseDebOsPackageNames(String packages) {
    PackageNameConverter.buildOsPackageNames(DEB_PACKAGE_TYPE, packages.tokenize(" "))
  }

  def parseRpmOsPackageNames(String packages) {
    PackageNameConverter.buildOsPackageNames(RPM_PACKAGE_TYPE, packages.tokenize(" "))
  }
}
