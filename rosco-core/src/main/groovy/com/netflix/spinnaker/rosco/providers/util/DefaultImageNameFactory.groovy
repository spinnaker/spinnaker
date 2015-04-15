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
import org.springframework.beans.factory.annotation.Autowired

import java.time.Clock

/**
 * Placeholder implementation of ImageNameFactory. Considers only package_name, a timestamp, and base_os.
 */
public class DefaultImageNameFactory implements ImageNameFactory {

  @Autowired
  Clock clock

  @Override
  def deriveImageNameAndAppVersion(BakeRequest bakeRequest) {
    // TODO(duftler): This is a placeholder. Need to properly support naming conventions.
    def timestamp = clock.millis()

    List<String> packageNameList = bakeRequest?.package_name?.tokenize(" ")
    def firstPackageName
    String appVersionStr
    AppVersion appVersion

    if (packageNameList) {
      // For now, we only take into account the first package name when generating the appversion tag.
      firstPackageName = packageNameList[0]

      // TODO(duftler): Append '/$job-name/$build-number' to appVersionStr once those properties are included
      // in BakeRequest.
      appVersionStr = PackageNameConverter.buildAppVersionStr(bakeRequest.base_os.packageType, firstPackageName)
      appVersion = AppVersion.parseName(appVersionStr)

      if (appVersion) {
        // TODO(duftler): Fix this. This is a temporary hack.
        packageNameList[0] = appVersion.packageName
      } else {
        // If appVersionStr could not be parsed to create AppVersion, clear it.
        appVersionStr = null
      }
    }

    // If we were able to generate the appversion tag from the original package name, we will need to
    // replace that original fully-qualified package name with the unqualified package name before using
    // it in the target image name.
    def baseImagePackageName = appVersion ? appVersion.packageName : firstPackageName
    def imageName = baseImagePackageName ? "$baseImagePackageName-" : ""

    // TODO(duftler): Get architecture from OsPackageName.
    imageName += "all-$timestamp-$bakeRequest.base_os"

    def packagesParameter

    if (appVersion) {
      // TODO(duftler): Remove this when the 'packageNameList[0] = appVersion.packageName' hack is removed.
      packagesParameter = packageNameList.join(" ")
    } else {
      packagesParameter = bakeRequest.package_name
    }

    [imageName, appVersionStr, packagesParameter]
  }

}
