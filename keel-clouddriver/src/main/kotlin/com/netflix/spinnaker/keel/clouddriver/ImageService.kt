/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.keel.clouddriver

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.keel.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.clouddriver.model.Image
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.NamedImageComparator
import com.netflix.spinnaker.keel.clouddriver.model.appVersion
import com.netflix.spinnaker.keel.clouddriver.model.hasAppVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ImageService(
  private val cloudDriverService: CloudDriverService
) {
  val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  suspend fun getLatestImage(artifactName: String, account: String): Image? =
    getLatestNamedImage(artifactName, account)?.toImage(artifactName)

  /**
   * If possible, return the latest image that's present in all regions and has tags.
   * If that doesn't exist, just return the latest image.
   */
  suspend fun getLatestImageWithAllRegions(artifactName: String, account: String, regions: List<String>): Image? =
    getLatestNamedImageWithAllRegions(artifactName, account, regions)?.toImage(artifactName)
      ?: getLatestImage(artifactName, account)

  private fun NamedImage.toImage(artifactName: String): Image? =
    tagsByImageId
      .values
      .firstOrNull { it?.containsKey("base_ami_version") ?: false && it?.containsKey("appversion") ?: false }
      .let { tags ->
        return if (tags == null) {
          log.debug("No images found for {}", artifactName)
          null
        } else {
          val image = Image(
            tags.getValue("base_ami_version")!!,
            tags.getValue("appversion")!!.substringBefore('/'),
            amis.keys
          )
          log.debug("Latest image for {} is {}", artifactName, image)
          image
        }
      }

  /**
   * Get the latest named image for a package.
   *
   * @param region if supplied the latest image in this region is returned, if `null` the latest
   * image regardless of region.
   */
  suspend fun getLatestNamedImage(packageName: String, account: String, region: String? = null): NamedImage? =
    cloudDriverService.namedImages(DEFAULT_SERVICE_ACCOUNT, packageName, account, region)
      .filter { it.hasAppVersion }
      .sortedWith(NamedImageComparator)
      .firstOrNull {
        AppVersion.parseName(it.appVersion).packageName == packageName
      }

  /**
   * Get a specific image for an app version.
   *
   * @param region if supplied the latest image in this region is returned, if `null` the latest
   * image regardless of region.
   */
  suspend fun getLatestNamedImage(appVersion: AppVersion, account: String, region: String? = null): NamedImage? =
    cloudDriverService.namedImages(
      user = DEFAULT_SERVICE_ACCOUNT,
      imageName = appVersion.toImageName().replace("~", "_"),
      account = account,
      region = region
    )
      .filter { it.hasAppVersion }
      .sortedWith(NamedImageComparator)
      .firstOrNull {
        AppVersion.parseName(it.appVersion).run {
          packageName == appVersion.packageName && version == appVersion.version && commit == appVersion.commit
        }
      }

  /**
   * Returns the latest image that is present in all regions.
   * Each ami must have tags.
   */
  suspend fun getLatestNamedImageWithAllRegionsForAppVersion(appVersion: AppVersion, account: String, regions: List<String>): NamedImage? =
    cloudDriverService.namedImages(
      user = DEFAULT_SERVICE_ACCOUNT,
      imageName = appVersion.toImageName().replace("~", "_"),
      account = account
    )
      .filter { it.hasAppVersion }
      .sortedWith(NamedImageComparator)
      .find { namedImage ->
        val curAppVersion = AppVersion.parseName(namedImage.appVersion)
        curAppVersion.packageName == appVersion.packageName &&
          curAppVersion.version == appVersion.version &&
          curAppVersion.commit == appVersion.commit &&
          namedImage.accounts.contains(account) &&
          namedImage.amis.keys.containsAll(regions) &&
          tagsExistForAllAmis(namedImage.tagsByImageId)
      }

  /**
   * Returns the latest image that is present in all regions.
   * Each ami must have tags.
   */
  suspend fun getLatestNamedImageWithAllRegions(packageName: String, account: String, regions: List<String>): NamedImage? {
    val images = cloudDriverService.namedImages(
      user = DEFAULT_SERVICE_ACCOUNT,
      imageName = packageName,
      account = account
    )

    val filteredImages = images
      .filter { it.hasAppVersion }
      .sortedWith(NamedImageComparator)

    val eliminatedImages = mutableMapOf<String, String>()
    val image = filteredImages
      .find {
        val errors = mutableListOf<String>()
        val curAppVersion = AppVersion.parseName(it.appVersion)
        if (curAppVersion.packageName != packageName) {
          errors.add("[package name ${curAppVersion.packageName} does not match required package]")
        }
        if (!it.accounts.contains(account)) {
          errors.add("[image is only in accounts ${it.accounts}]")
        }
        if (!it.amis.keys.containsAll(regions)) {
          errors.add("[image is only in regions ${it.amis.keys}]")
        }
        if (!tagsExistForAllAmis(it.tagsByImageId)) {
          errors.add("[image does not have tags for all regions: existing tags ${it.tagsByImageId}]")
        }
        if (errors.isEmpty()) {
          true
        } else {
          eliminatedImages[it.imageName] = errors.joinToString(",")
          false
        }
      }
    log.debug(
      "Finding latest qualifying named image for $packageName in account $account and regions $regions:\n " +
        "selected image=${image?.imageName}\n " +
        "rejected images=${eliminatedImages.map { it.key + ": " + it.value + "\n" }.joinToString("")}")

    return image
  }

  suspend fun getNamedImageFromJenkinsInfo(packageName: String, account: String, buildHost: String, buildName: String, buildNumber: String): NamedImage? =
    cloudDriverService.namedImages(DEFAULT_SERVICE_ACCOUNT, packageName, account)
      .filter { it.hasAppVersion }
      .sortedWith(NamedImageComparator)
      .filter {
        AppVersion.parseName(it.appVersion).packageName == packageName
      }
      .firstOrNull { namedImage ->
        val allTags = getAllTags(namedImage)
        amiMatches(allTags, buildHost, buildName, buildNumber)
      }

  private fun getAllTags(image: NamedImage): Map<String, String> {
    val allTags = HashMap<String, String>()
    image.tagsByImageId.forEach { (_, tags) ->
      tags?.forEach { k, v ->
        if (v != null) {
          allTags[k] = v
        }
      }
    }
    return allTags
  }

  private fun amiMatches(tags: Map<String, String>, buildHost: String, buildName: String, buildNumber: String): Boolean {
    if (!tags.containsKey("build_host") || !tags.containsKey("appversion") || tags["build_host"] != buildHost) {
      return false
    }

    val appversion = tags["appversion"]?.split("/") ?: error("appversion tag is missing")

    if (appversion.size != 3) {
      return false
    }

    if (appversion[1] != buildName || appversion[2] != buildNumber) {
      return false
    }
    return true
  }

  private fun tagsExistForAllAmis(tagsByImageId: Map<String, Map<String, String?>?>): Boolean {
    tagsByImageId.keys.forEach { key ->
      val tags = tagsByImageId[key]
      if (tags == null || tags.isEmpty()) {
        return false
      }
    }
    return true
  }
}

private fun AppVersion.toImageName() = "$packageName-$version-h$buildNumber.$commit"
