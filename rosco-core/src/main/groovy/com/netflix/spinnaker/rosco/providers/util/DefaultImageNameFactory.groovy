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
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

import java.time.Clock

/**
 * Placeholder implementation of ImageNameFactory. Considers only package_name, a timestamp, and base_os.
 */
@Slf4j
public class DefaultImageNameFactory implements ImageNameFactory {

  @Autowired
  Clock clock

  @Override
  def deriveImageNameAndAppVersion(BakeRequest bakeRequest) {
    // TODO(duftler): This is a placeholder. Need to properly support naming conventions.
    def timestamp = clock.millis()

    List<String> packageNameList = bakeRequest?.package_name?.tokenize(" ")
    def firstPackageName
    PackageNameConverter.OsPackageName osPackageName
    String appVersionStr
    AppVersion appVersion

    if (packageNameList) {
      // For now, we only take into account the first package name when generating the appversion tag.
      firstPackageName = packageNameList[0]

      // Passing in firstPackageName here to avoid tokenizing the package name list twice.
      osPackageName = PackageNameConverter.buildOsPackageName(bakeRequest, firstPackageName)
      appVersionStr = PackageNameConverter.buildAppVersionStr(bakeRequest, osPackageName)
      appVersion = AppVersion.parseName(appVersionStr)

      if (!appVersion) {
        if (appVersionStr) {
          log.debug("AppVersion.parseName() was unable to parse appVersionStr=$appVersionStr.")
        }

        // If appVersionStr could not be parsed to create AppVersion, clear it.
        appVersionStr = null
      }

      if (osPackageName?.name) {
        packageNameList[0] = osPackageName.name

        // If a version/release was specified, we need to include that when installing the package.
        if (osPackageName?.version && osPackageName?.release) {
          packageNameList[0] += "=$osPackageName.version-$osPackageName.release"
        }
      }
    }

    // We need to replace the original fully-qualified package name with the unqualified package name before using
    // it in the target image name.
    def baseImagePackageName = osPackageName?.name ?: firstPackageName
    def imageName = baseImagePackageName ? "$baseImagePackageName-" : ""

    // TODO(duftler): Get architecture from OsPackageName.
    imageName += "all-$timestamp-$bakeRequest.base_os"

    def packagesParameter = packageNameList.join(" ")

    [imageName, appVersionStr, packagesParameter]
  }

}
