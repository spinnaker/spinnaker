package com.netflix.spinnaker.rosco.providers.util.packagespecific

import com.netflix.spinnaker.rosco.providers.util.PackageUtil

import static com.netflix.spinnaker.rosco.providers.util.PackageNameConverter.OsPackageName

class NupkgPackageUtil implements PackageUtil {

  @Override
  String getPackageType() {
    return 'nupkg'
  }

  @Override
  String getPackageManagerVersionSeparator() {
    return '.'
  }

  @Override
  String getVersionSeparator() {
    return '.'
  }

  @Override
  String getBuildNumberSeparator() {
    return '-'
  }

  @Override
  String getCommitHashSeparator() {
    return '+'
  }

  @Override
  OsPackageName parsePackageName(String fullyQualifiedPackageName) {
    // Nuget supports SemVer 1.0 for package versioning standards.
    // Therefore, the naming convention for nuget packages is
    // {package-name}[.{language}].{major}.{minor}.{patch/build}[-{version}][.{revision}][+{metadata}]
    // For example: ContosoUtilities.ja-JP.1.0.0-rc1.nupkg
    //              notepadplusplus.7.3.nupkg
    //              autohotkey.1.1.24.04.nupkg
    //              microsoft-aspnet-mvc.6.0.0-rc1-final.nupkg
    //              microsoft-aspnet-mvc.de.3.0.50813.1.nupkg
    //              microsoft.aspnet.mvc.6.0.0-rc1-final.nupkg
    //              microsoft.aspnet.mvc.de.3.0.50813.1.nupkg

    OsPackageName osPackageName = new OsPackageName()
    if (!fullyQualifiedPackageName) return osPackageName

    fullyQualifiedPackageName = fullyQualifiedPackageName.replaceFirst('.nupkg', '')

    osPackageName.with {
      name = fullyQualifiedPackageName

      List<String> parts = fullyQualifiedPackageName.tokenize('.')

      if (parts.size() > 2) {
        def versionStart = 0

        for (def i = 0; i < parts.size(); i++) {
          if (i > 0 && parts[i].isInteger()) {
            versionStart = i
            break
          }
        }

        if (versionStart < 1) {
          for (def i = 0; i < parts.size(); i++) {
            if (i > 0 && parts[i].contains('-') && parts[i][0].isInteger()) {
              versionStart = i
              break
            }
          }
        }

        if (versionStart > 0) {

          name = parts.subList(0, versionStart).join('.')
          version = parts.subList(versionStart, parts.size()).join('.')

          if (version.contains('-')) {
            (version, release) = version.split('-', 2)
          } else if (version.contains('+')) {
            (version, release) = version.split(/\+/, 2)
            release = '+' + release
          }

        } else {

          def metaDataIndex = parts.findIndexOf { val -> val =~ /\+/ }

          if (metaDataIndex > -1) {
            (name, release) = parts[metaDataIndex].split(/\+/)
            release = "+" + release
            name = "${parts.subList(0, metaDataIndex).join('.')}.$name"
          } else {
            name = parts.join('.')
          }
        }

      } else if (parts.size() == 2) {

        if (parts[1].isInteger()) {
          name = parts[0]
          version = parts[1]
        } else if (parts[1][0].isInteger() && parts[1].contains('-')) {

          name = parts[0]

          def versionParts = parts[1].split('-', 2)
          version = versionParts[0]
          release = versionParts[1]

        } else {
          name = parts.join('.')
        }
      }
    }

    return osPackageName
  }
}
