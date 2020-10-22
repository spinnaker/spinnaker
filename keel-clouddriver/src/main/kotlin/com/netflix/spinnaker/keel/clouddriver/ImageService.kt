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
import com.netflix.spinnaker.keel.caffeine.CacheFactory
import com.netflix.spinnaker.keel.clouddriver.model.Image
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.NamedImageComparator
import com.netflix.spinnaker.keel.clouddriver.model.appVersion
import com.netflix.spinnaker.keel.clouddriver.model.creationDate
import com.netflix.spinnaker.keel.clouddriver.model.hasAppVersion
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.filterNotNullValues
import com.netflix.spinnaker.keel.parseAppVersion
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture.completedFuture

class ImageService(
  private val cloudDriverService: CloudDriverService,
  cacheFactory: CacheFactory
) {
  val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  suspend fun getLatestImage(artifactName: String, account: String): Image? =
    cloudDriverService.namedImages(DEFAULT_SERVICE_ACCOUNT, artifactName, account)
      .filter { it.hasAppVersion }
      .sortedWith(NamedImageComparator)
      .firstOrNull {
        // TODO: Frigga and Rocket version parsing are not aligned. We should consolidate.
        it.appVersion.parseAppVersion().packageName == artifactName
      }?.toImage(artifactName)

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

  private data class NamedImageCacheKey(
    val appVersion: AppVersion,
    val account: String,
    val region: String
  )

  private val namedImageCache = cacheFactory
    .asyncLoadingCache<NamedImageCacheKey, NamedImage>("namedImages") { (appVersion, account, region) ->
      log.debug("Searching for baked image for {} in {}", appVersion.toImageName(), region)
      cloudDriverService.namedImages(
        user = DEFAULT_SERVICE_ACCOUNT,
        imageName = appVersion.toImageName().replace("~", "_"),
        account = account
      )
        // only consider images with tags and app version set properly
        .asSequence()
        .filter { image ->
          tagsExistForAllAmis(image.tagsByImageId) && image.hasAppVersion
        }
        // filter to images with matching app version
        .filter { image ->
          // TODO: Frigga and Rocket version parsing are not aligned. We should consolidate.
          image.appVersion.parseAppVersion().run {
            packageName == appVersion.packageName && version == appVersion.version && commit == appVersion.commit
          }
        }
        // filter to images in the correct account and the desired region
        .filter { image ->
          image.accounts.contains(account) && image.amis.containsKey(region)
        }
        // reduce to the newest images required to support all regions we want
        .sortedByDescending { it.creationDate }
        .firstOrNull()
    }

  /**
   * Find the latest properly tagged image in [account] and [region].
   *
   * As a side effect this method will prime the cache for any additional regions where the image is
   * available.
   */
  suspend fun getLatestNamedImage(
    appVersion: AppVersion,
    account: String,
    region: String
  ): NamedImage? =
    namedImageCache
      .get(NamedImageCacheKey(appVersion, account, region))
      .await()
      // prime the cache if the image is also in other regions
      ?.also { image ->
        (image.amis.keys - region).forEach { otherRegion ->
          namedImageCache.put(NamedImageCacheKey(appVersion, account, otherRegion), completedFuture(image))
        }
      }

  suspend fun getNamedImageFromJenkinsInfo(packageName: String, account: String, buildHost: String, buildName: String, buildNumber: String): NamedImage? =
    cloudDriverService.namedImages(DEFAULT_SERVICE_ACCOUNT, packageName, account)
      .filter { it.hasAppVersion }
      .sortedWith(NamedImageComparator)
      .filter {
        // TODO: Frigga and Rocket version parsing are not aligned. We should consolidate.
        it.appVersion.parseAppVersion().packageName == packageName
      }
      .firstOrNull { namedImage ->
        val allTags = getAllTags(namedImage)
        amiMatches(allTags, buildHost, buildName, buildNumber)
      }

  suspend fun findBaseAmiVersion(baseImageName: String): String {
    return cloudDriverService.namedImages(DEFAULT_SERVICE_ACCOUNT, baseImageName, "test")
      .lastOrNull()
      ?.let { namedImage ->
        namedImage
          .tagsByImageId
          .values
          .filterNotNull()
          .find { it.containsKey("base_ami_version") }
          ?.getValue("base_ami_version")
      } ?: throw BaseAmiNotFound(baseImageName)
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


/**
 * Find the latest properly tagged images in [account] for each of [regions] using
 * [ImageService.getLatestNamedImage] in parallel for each region.
 *
 * In many cases all the values in the resulting map will be the same [NamedImage] instance, but
 * this may not be the case if images were baked separately in each region.
 *
 * The resulting map will contain no entry for regions where an image is not found. The calling
 * code must check this if it requires all regions to be present.
 */
suspend fun ImageService.getLatestNamedImages(
  appVersion: AppVersion,
  account: String,
  regions: Collection<String>
): Map<String, NamedImage> = coroutineScope {
  regions.associateWith { region ->
    async {
      getLatestNamedImage(
        appVersion = appVersion,
        account = account,
        region = region
      )
    }
  }
    .mapValues { (_, it) -> it.await() }
    .filterNotNullValues()
}

private fun AppVersion.toImageName() = "$packageName-$version-h$buildNumber.$commit"

class BaseAmiNotFound(baseImage: String) :
  IntegrationException("Could not find a base AMI for base image $baseImage")
