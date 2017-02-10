package com.netflix.spinnaker.rosco.providers.util.packagespecific

import com.netflix.spinnaker.rosco.providers.util.PackageNameConverter
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import spock.lang.Specification
import spock.lang.Unroll

class RpmPackageUtilSpec extends Specification implements TestDefaults {
  @Unroll
  void "rpm package names are properly parsed"() {
    when:
      def osPackageName = new RpmPackageUtil().parsePackageName(packageName)

    then:
      osPackageName == expectedOsPackageName

    where:
      packageName                                    || expectedOsPackageName
      null                                           || new PackageNameConverter.OsPackageName()
      ""                                             || new PackageNameConverter.OsPackageName()
      "billinggateway-1.0-h2385.e0a09ce.all"         || new PackageNameConverter.OsPackageName(name: "billinggateway",
                                                                                              version: "1.0",
                                                                                              release: "h2385.e0a09ce",
                                                                                              arch: "all")
      "nflx-djangobase-enhanced-0.1-h12.170cdbd.all" || new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced",
                                                                                              version: "0.1",
                                                                                              release: "h12.170cdbd",
                                                                                              arch: "all")
      "sf-lucifer-0.0.10-1.noarch"                   || new PackageNameConverter.OsPackageName(name: "sf-lucifer",
                                                                                              version: "0.0.10",
                                                                                              release: "1",
                                                                                              arch: "noarch")
  }
}
