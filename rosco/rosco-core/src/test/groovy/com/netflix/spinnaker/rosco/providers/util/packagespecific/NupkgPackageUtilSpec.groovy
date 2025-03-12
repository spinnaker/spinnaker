package com.netflix.spinnaker.rosco.providers.util.packagespecific

import com.netflix.spinnaker.rosco.providers.util.PackageNameConverter
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import spock.lang.Specification
import spock.lang.Unroll

class NupkgPackageUtilSpec extends Specification implements TestDefaults {
  @Unroll
  void "nupkg package names are properly parsed"() {
    when:
      def osPackageName = new NupkgPackageUtil().parsePackageName(packageName)

    then:
      osPackageName == expectedOsPackageName

    where:
      packageName                                    || expectedOsPackageName
      null                                           || new PackageNameConverter.OsPackageName()
      ""                                             || new PackageNameConverter.OsPackageName()
      "billinggateway.1.0.1"                         || new PackageNameConverter.OsPackageName(name: "billinggateway",
                                                                                               version: "1.0.1",
                                                                                               release: null,
                                                                                               arch: null)
      "nflx-djangobase-enhanced.1.0.1-rc1"           || new PackageNameConverter.OsPackageName(name: "nflx-djangobase-enhanced",
                                                                                               version: "1.0.1",
                                                                                               release: "rc1",
                                                                                               arch: null)
      "sf-lucifer.en-US.0.0.10-1"                    || new PackageNameConverter.OsPackageName(name: "sf-lucifer.en-US",
                                                                                               version: "0.0.10",
                                                                                               release: "1",
                                                                                               arch: null)
      "microsoft.aspnet.mvc"                         || new PackageNameConverter.OsPackageName(name: "microsoft.aspnet.mvc",
                                                                                               version: null,
                                                                                               release: null,
                                                                                               arch: null)
      "microsoft.aspnet.mvc.6"                       || new PackageNameConverter.OsPackageName(name: "microsoft.aspnet.mvc",
                                                                                               version: "6",
                                                                                               release: null,
                                                                                               arch: null)
      "microsoft.aspnet.mvc.6-rc1-final"             || new PackageNameConverter.OsPackageName(name: "microsoft.aspnet.mvc",
                                                                                               version: "6",
                                                                                               release: "rc1-final",
                                                                                               arch: null)
      "microsoft.aspnet.mvc.6.0.0+sf23sdf"           || new PackageNameConverter.OsPackageName(name: "microsoft.aspnet.mvc",
                                                                                               version: "6.0.0",
                                                                                               release: "+sf23sdf",
                                                                                               arch: null)
      "microsoft.aspnet.mvc.6.0.0-rc1-final+sf23sdf" || new PackageNameConverter.OsPackageName(name: "microsoft.aspnet.mvc",
                                                                                               version: "6.0.0",
                                                                                               release: "rc1-final+sf23sdf",
                                                                                               arch: null)
      "microsoft-aspnet-mvc"                         || new PackageNameConverter.OsPackageName(name: "microsoft-aspnet-mvc",
                                                                                               version: null,
                                                                                               release: null,
                                                                                               arch: null)
      "microsoft-aspnet-mvc.6"                       || new PackageNameConverter.OsPackageName(name: "microsoft-aspnet-mvc",
                                                                                               version: "6",
                                                                                               release: null,
                                                                                               arch: null)
      "microsoft-aspnet-mvc.6-rc1-final"             || new PackageNameConverter.OsPackageName(name: "microsoft-aspnet-mvc",
                                                                                               version: "6",
                                                                                               release: "rc1-final",
                                                                                               arch: null)
      "microsoft-aspnet-mvc.6.0.0+sf23sdf"           || new PackageNameConverter.OsPackageName(name: "microsoft-aspnet-mvc",
                                                                                               version: "6.0.0",
                                                                                               release: "+sf23sdf",
                                                                                               arch: null)
      "microsoft-aspnet-mvc.6.0.0-rc1-final+sf23sdf" || new PackageNameConverter.OsPackageName(name: "microsoft-aspnet-mvc",
                                                                                               version: "6.0.0",
                                                                                               release: "rc1-final+sf23sdf",
                                                                                               arch: null)

  }
}
