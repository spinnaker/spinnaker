package com.netflix.spinnaker.rosco.providers.util.packagespecific

import com.netflix.spinnaker.rosco.providers.util.PackageNameConverter
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import spock.lang.Specification
import spock.lang.Unroll

class DebPackageUtilSpec extends Specification implements TestDefaults {
  @Unroll
  void "deb package names are properly parsed"() {
    when:
      def osPackageName = new DebPackageUtil().parsePackageName(packageName)

    then:
      osPackageName == expectedOsPackageName

    where:
      packageName                                    || expectedOsPackageName
      null                                           || new PackageNameConverter.OsPackageName()
      ""                                             || new PackageNameConverter.OsPackageName()
      "nflx-djangobase-enhanced"                     || new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced")
      "nflx-djangobase-enhanced_0.1"                 || new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced",
                                                                                               version: "0.1")
      "nflx-djangobase-enhanced_0.1-h12.170cdbd"     || new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced",
                                                                                               version: "0.1",
                                                                                               release: "h12.170cdbd")
      "nflx-djangobase-enhanced_0.1-3"               || new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced",
                                                                                               version: "0.1",
                                                                                               release: "3")
      "nflx-djangobase-enhanced_0.1-h12.170cdbd_all" || new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced",
                                                                                               version: "0.1",
                                                                                               release: "h12.170cdbd",
                                                                                               arch: "all")
      "nflx-djangobase-enhanced_0.1-3_all"           || new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced",
                                                                                               version: "0.1",
                                                                                               release: "3",
                                                                                               arch: "all")
  }
}
