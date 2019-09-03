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
import com.netflix.spinnaker.keel.clouddriver.model.Image
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.NamedImageComparator
import com.netflix.spinnaker.keel.clouddriver.model.appVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ImageService(
  private val cloudDriverService: CloudDriverService
) {
  val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  suspend fun getLatestImage(artifactName: String, account: String): Image? {
    return cloudDriverService.namedImages(DEFAULT_SERVICE_ACCOUNT, "$artifactName-", account)
      .sortedWith(NamedImageComparator)
      .lastOrNull {
        AppVersion.parseName(it.appVersion).packageName == artifactName
      }
      ?.let { namedImage ->
        namedImage
          .tagsByImageId
          .values
          .firstOrNull { it?.containsKey("base_ami_version") ?: false && it?.containsKey("appversion") ?: false }
          .let { tags ->
            if (tags == null) {
              log.debug("No images found for {}", artifactName)
              null
            } else {
              val image = Image(
                tags.getValue("base_ami_version")!!,
                tags.getValue("appversion")!!.substringBefore('/'),
                namedImage.amis.keys
              )
              log.debug("Latest image for {} is {}", artifactName, image)
              image
            }
          }
      }
  }

  /**
   * Get the latest named image for a package.
   *
   * @param region if supplied the latest image in this region is returned, if `null` the latest
   * image regardless of region.
   */
  suspend fun getLatestNamedImage(packageName: String, account: String, region: String? = null): NamedImage? =
    cloudDriverService.namedImages(DEFAULT_SERVICE_ACCOUNT, "$packageName-", account, region)
      .sortedWith(NamedImageComparator)
      .lastOrNull {
        AppVersion.parseName(it.appVersion).packageName == packageName
      }

  suspend fun getNamedImageFromJenkinsInfo(packageName: String, account: String, buildHost: String, buildName: String, buildNumber: String): NamedImage? {
    return cloudDriverService.namedImages(DEFAULT_SERVICE_ACCOUNT, "$packageName-", account)
      .sortedWith(NamedImageComparator)
      .filter {
        AppVersion.parseName(it.appVersion).packageName == packageName
      }
      .lastOrNull { namedImage ->
        val allTags = getAllTags(namedImage)
        amiMatches(allTags, buildHost, buildName, buildNumber)
      }
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
}
