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

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.api.BakeRequest.PackageType
import groovy.transform.EqualsAndHashCode
import groovy.util.logging.Slf4j

@Slf4j
class PackageNameConverter {

  @EqualsAndHashCode
  static class OsPackageName {
    String name
    String version
    String release
    String arch

    public String qualifiedPackageName(PackageType packageType) {
      if (version && release) {
        "${name}${packageType.getVersionDelimiter()}${version}-${release}"
      } else if (version) {
        "${name}${packageType.getVersionDelimiter()}${version}"
      } else {
        return null
      }
    }
  }

  // Naming-convention for debs is name_version-release_arch.
  // For example: nflx-djangobase-enhanced_0.1-h12.170cdbd_all
  public static OsPackageName parseDebPackageName(String fullyQualifiedPackageName) {
    OsPackageName osPackageName = new OsPackageName()
    if (!fullyQualifiedPackageName) return osPackageName

    osPackageName.with {
      name = fullyQualifiedPackageName

      List<String> parts = fullyQualifiedPackageName?.tokenize("_")

      if (parts) {
        if (parts.size() > 1) {
          List<String> versionReleaseParts = parts[1].tokenize("-")

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

    osPackageName
  }

  // Naming-convention for rpms is name-version-release.arch.
  // For example: nflx-djangobase-enhanced-0.1-h12.170cdbd.all
  public static OsPackageName parseRpmPackageName(String fullyQualifiedPackageName) {
    OsPackageName osPackageName = new OsPackageName()
    if (!fullyQualifiedPackageName) return osPackageName

    osPackageName.with {
      name = fullyQualifiedPackageName

      List<String> nameParts = fullyQualifiedPackageName.tokenize(".")
      int numberOfNameParts = nameParts.size()

      if (numberOfNameParts >= 2) {
        arch = nameParts.drop(numberOfNameParts - 1).join("")
        fullyQualifiedPackageName = nameParts.take(numberOfNameParts - 1).join(".")
      }

      List<String> parts = fullyQualifiedPackageName.tokenize("-")

      if (parts.size() >= 3) {
        release = parts.pop()
        version = parts.pop()
        name = parts.join("-")
      }
    }

    osPackageName
  }

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
  public static OsPackageName parseNupkgPackageName(String fullyQualifiedPackageName) {
    OsPackageName osPackageName = new OsPackageName()
    if (!fullyQualifiedPackageName) return osPackageName

    fullyQualifiedPackageName = fullyQualifiedPackageName.replaceFirst('.nupkg', '')

    osPackageName.with {
      name = fullyQualifiedPackageName

      List<String> parts = fullyQualifiedPackageName.tokenize(".")

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
          } else if (version.contains("+")) {
            (version, release) = version.split("\\+", 2)
            release = '+' + release
          }

        } else {

          def metaDataIndex = parts.findIndexOf { val -> val =~ /\+/ }

          if (metaDataIndex > -1) {
            (name, release) = parts[metaDataIndex].split('\\+')
            release = "+" + release
            name = parts.subList(0, metaDataIndex).join('.') + "." + name
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
          name = parts.join(".")
        }
      }
    }

    osPackageName
  }

  public static OsPackageName buildOsPackageName(BakeRequest.PackageType packageType, String packageName) {
    switch (packageType) {
      case BakeRequest.PackageType.DEB:
        return PackageNameConverter.parseDebPackageName(packageName)
      case BakeRequest.PackageType.RPM:
        return PackageNameConverter.parseRpmPackageName(packageName)
      case BakeRequest.PackageType.NUPKG:
        return PackageNameConverter.parseNupkgPackageName(packageName)
      default:
        throw new IllegalArgumentException("Unrecognized packageType '$packageType'.")
    }
  }

  public static List<OsPackageName> buildOsPackageNames(BakeRequest.PackageType packageType, List<String> packageNames) {
    packageNames.collect{ packageName ->
      buildOsPackageName(packageType, packageName)
    }.findAll{it.name}
  }

  public static String buildAppVersionStr(BakeRequest bakeRequest, OsPackageName osPackageName, BakeRequest.PackageType packageType) {
    // As per source of AppVersion, these are valid appversion tags:
    //   subscriberha-1.0.0-h150
    //   subscriberha-1.0.0-h150.586499
    //   subscriberha-1.0.0-h150.586499/WE-WAPP-subscriberha/150
    String appVersion = osPackageName.name

    def versionSeparator = packageType == BakeRequest.PackageType.NUPKG ? "." : "-"
    def buildNumberSeparator = packageType == BakeRequest.PackageType.NUPKG ? "-" : "-h"
    def commitHashSeparator = packageType == BakeRequest.PackageType.NUPKG ? "+" : "."

    osPackageName.with {
      if (version) {
        appVersion += "$versionSeparator$version"

        if (bakeRequest.build_number) {
          appVersion += "$buildNumberSeparator$bakeRequest.build_number"

          if (bakeRequest.commit_hash) {
            appVersion += "$commitHashSeparator$bakeRequest.commit_hash"
          }

          if (bakeRequest.job) {
            // Travis job name and Jenkins job name with folder may contain slashes in the job name
            // that make AppVersion.parseName fail. Replace all slashes in the job name with hyphens.
            def job = bakeRequest.job.replaceAll("/", "-")
            appVersion += "/$job/$bakeRequest.build_number"
          }
        }
      }
    }

    if (!AppVersion.parseName(appVersion)) {
      log.debug("AppVersion.parseName() was unable to parse appVersionStr =$appVersion.")
      return null
    }

    appVersion
  }
}
