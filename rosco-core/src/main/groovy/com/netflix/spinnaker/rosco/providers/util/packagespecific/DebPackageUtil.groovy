package com.netflix.spinnaker.rosco.providers.util.packagespecific

import com.netflix.spinnaker.rosco.providers.util.PackageUtil

import static com.netflix.spinnaker.rosco.providers.util.PackageNameConverter.OsPackageName

class DebPackageUtil implements PackageUtil {


  @Override
  String getPackageType() {
    return 'deb'
  }

  @Override
  String getPackageManagerVersionSeparator() {
    return '='
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
    // Naming-convention for debs is name_version-release_arch.
    // For example: nflx-djangobase-enhanced_0.1-h12.170cdbd_all

    OsPackageName osPackageName = new OsPackageName()
    if (!fullyQualifiedPackageName) return osPackageName

    osPackageName.with {
      name = fullyQualifiedPackageName

      List<String> parts = fullyQualifiedPackageName?.tokenize('_')

      if (parts) {
        if (parts.size() > 1) {
          List<String> versionReleaseParts = parts[1].tokenize('-')

          if (versionReleaseParts) {
            version = versionReleaseParts[0]
            name = parts[0]

            if (versionReleaseParts.size() > 1) {
              release = versionReleaseParts[1]
            }
          }

          if (parts.size() > 2) {
            arch = parts[2]
          }
        }
      }
    }

    return osPackageName
  }
}
