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
import com.netflix.spinnaker.rosco.api.BakeRequest.PackageType
import groovy.util.logging.Slf4j

import java.time.Clock

  /**
   * Default implementation of ImageNameFactory relies on the structure of first package_name,
   * a timestamp, and the base_os. For more fine grained conventions, extend this class, override the required
   * methods and alter your provider specific BakeHandlers getImageNameFactory method to reflect the change.
   */

@Slf4j
public class ImageNameFactory {

  Clock clock = Clock.systemUTC()

  /**
   * Attempts to produce an appVersionStr from the first packageName; to be used for tagging the newly-baked image
   */
  def buildAppVersionStr(BakeRequest bakeRequest, List<PackageNameConverter.OsPackageName> osPackageNames, BakeRequest.PackageType packageType) {
    String appVersionStr = null

    if (osPackageNames) {
      appVersionStr = PackageNameConverter.buildAppVersionStr(bakeRequest, osPackageNames.first(), packageType)
    }

    return appVersionStr
  }

  /**
   * Produces an imageName either from the BakeRequest.ami_name or the first package to be installed.
   * This is to be used for naming the image being baked. Note that this function is not required to
   * return the same image name on multiple invocations with the same bake request
   */
  def buildImageName(BakeRequest bakeRequest, List<PackageNameConverter.OsPackageName> osPackageNames) {
    String timestamp = clock.millis()
    String baseImageName = osPackageNames ? osPackageNames.first()?.name : ""
    String baseImageArch = osPackageNames ? osPackageNames.first()?.arch : "all"

    String baseName = bakeRequest.ami_name ?: baseImageName
    String arch = baseImageArch ?: "all"
    String release = bakeRequest.ami_suffix ?: timestamp

    [baseName, arch, release, bakeRequest.base_os].findAll{it}.join("-")
  }

  /**
   * Returns packagesParameter; the updated list of packages to be used for overriding the passed package list
   */
  def buildPackagesParameter(PackageType packageType, List<PackageNameConverter.OsPackageName> osPackageNames) {
    osPackageNames.collect { osPackageName ->
      osPackageName.qualifiedPackageName(packageType) ?: osPackageName.name
    }.join(" ")
  }
}
