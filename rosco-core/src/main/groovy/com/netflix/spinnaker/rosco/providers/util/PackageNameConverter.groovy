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
      if (version) {
        "${name}${packageType.util.packageManagerVersionSeparator}${version}${release ? "-$release" : ""}"
      } else {
        return null
      }
    }
  }

  public static OsPackageName buildOsPackageName(BakeRequest.PackageType packageType, String packageName) {
    packageType.util.parsePackageName(packageName)
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

    osPackageName.with {
      if (version) {
        appVersion += "$packageType.util.versionSeparator$version"

        if (bakeRequest.build_number) {
          appVersion += "$packageType.util.buildNumberSeparator$bakeRequest.build_number"

          if (bakeRequest.commit_hash) {
            appVersion += "$packageType.util.commitHashSeparator$bakeRequest.commit_hash"
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
      log.debug("AppVersion.parseName() was unable to parse appVersionStr =$appVersion " +
        "(executionId: $bakeRequest.spinnaker_execution_id)")
      return null
    }

    appVersion
  }
}
