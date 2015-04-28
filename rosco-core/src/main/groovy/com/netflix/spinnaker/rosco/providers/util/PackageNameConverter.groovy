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

import com.netflix.spinnaker.rosco.api.BakeRequest
import groovy.transform.EqualsAndHashCode

class PackageNameConverter {

  @EqualsAndHashCode
  static class OsPackageName {
    String name
    String version
    String release
    String buildNumber
    String commit
    String arch
  }

  // Naming-convention for debs is name_version-release_arch.
  // For example: nflx-djangobase-enhanced_0.1-h12.170cdbd_all
  public static OsPackageName parseDebPackageName(String fullyQualifiedPackageName) {
    OsPackageName osPackageName = new OsPackageName()

    osPackageName.with {
      List<String> parts = fullyQualifiedPackageName?.tokenize("_")

      if (parts) {
        name = parts[0]

        if (parts.size > 1) {
          List<String> versionReleaseParts = parts[1].tokenize("-")

          if (versionReleaseParts) {
            version = versionReleaseParts[0]

            if (versionReleaseParts.size > 1) {
              release = versionReleaseParts[1]

              // Naming-convention for release is buildNumber.commit.
              // For example: h12.170cdbd
              List<String> releaseParts = release.tokenize(".")

              if (releaseParts.size == 2) {
                buildNumber = releaseParts[0]
                commit = releaseParts[1]
              }
            }
          }

          if (parts.size > 2) {
            arch = parts[2]
          }
        }
      }
    }

    osPackageName
  }

  // Naming-convention for rpms is name-version-release-arch.
  // For example: nflx-djangobase-enhanced-0.1-h12.170cdbd-all
  public static OsPackageName parseRpmPackageName(String fullyQualifiedPackageName) {
    OsPackageName osPackageName = new OsPackageName()

    osPackageName.with {
      List<String> parts = fullyQualifiedPackageName?.tokenize("-")

      if (parts?.size >= 4) {
        arch = parts.pop()
        release = parts.pop()
        version = parts.pop()
        name = parts.join("-")

        // Naming-convention for release is buildNumber.commit.
        // For example: h12.170cdbd
        List<String> releaseParts = release.tokenize(".")

        if (releaseParts.size == 2) {
          buildNumber = releaseParts[0]
          commit = releaseParts[1]
        }
      }
    }

    osPackageName
  }

  public static String buildAppVersionStr(BakeRequest bakeRequest, String packageName) {
    BakeRequest.PackageType packageType = bakeRequest.base_os.packageType
    OsPackageName osPackageName

    if (packageType == BakeRequest.PackageType.DEB) {
      osPackageName = PackageNameConverter.parseDebPackageName(packageName)
    } else if (packageType == BakeRequest.PackageType.RPM) {
      osPackageName = PackageNameConverter.parseRpmPackageName(packageName)
    } else {
      throw new IllegalArgumentException("Unrecognized packageType '$packageType'.")
    }

    String appVersion = osPackageName.name

    osPackageName.with {
      if (version) {
        appVersion += "-$version"

        if (bakeRequest.build_number) {
          appVersion += "-h$bakeRequest.build_number"

          if (commit) {
            appVersion += ".$commit"
          }

          if (bakeRequest.job && bakeRequest.build_number) {
            appVersion += "/$bakeRequest.job/$bakeRequest.build_number"
          }
        }
      }
    }

    appVersion
  }

}
