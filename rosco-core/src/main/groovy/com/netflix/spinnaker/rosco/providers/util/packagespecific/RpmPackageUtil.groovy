package com.netflix.spinnaker.rosco.providers.util.packagespecific

import com.netflix.spinnaker.rosco.providers.util.PackageUtil

import static com.netflix.spinnaker.rosco.providers.util.PackageNameConverter.OsPackageName

class RpmPackageUtil implements PackageUtil {

  @Override
  String getPackageType() {
    return 'rpm'
  }

  @Override
  String getPackageManagerVersionSeparator() {
    return '-'
  }

  @Override
  String getVersionSeparator() {
    return '-'
  }

  @Override
  String getBuildNumberSeparator() {
    return '-h'
  }

  @Override
  String getCommitHashSeparator() {
    return '.'
  }

  @Override
  OsPackageName parsePackageName(String fullyQualifiedPackageName) {
    // Naming-convention for rpms is name-version-release.arch.
    // For example: nflx-djangobase-enhanced-0.1-h12.170cdbd.all
    OsPackageName osPackageName = new OsPackageName()
    if (!fullyQualifiedPackageName) return osPackageName

    osPackageName.with {
      name = fullyQualifiedPackageName

      List<String> nameParts = fullyQualifiedPackageName.tokenize('.')
      int numberOfNameParts = nameParts.size()

      if (numberOfNameParts >= 2) {
        arch = nameParts.drop(numberOfNameParts - 1).join('')
        fullyQualifiedPackageName = nameParts.take(numberOfNameParts - 1).join('.')
      }

      List<String> parts = fullyQualifiedPackageName.tokenize('-')

      if (parts.size() >= 3) {
        release = parts.pop()
        version = parts.pop()
        name = parts.join('-')
      }
    }

    return osPackageName
  }
}
