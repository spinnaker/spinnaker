package com.netflix.spinnaker.rosco.providers.util

import static com.netflix.spinnaker.rosco.providers.util.PackageNameConverter.OsPackageName

interface PackageUtil {

    String getPackageType()
    String getPackageManagerVersionSeparator()
    String getVersionSeparator()
    String getBuildNumberSeparator()
    String getCommitHashSeparator()
    OsPackageName parsePackageName(String fullyQualifiedPackageName)
}