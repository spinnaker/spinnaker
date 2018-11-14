/*
 * Copyright 2016 Google, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Image
import com.google.api.services.compute.model.ImageList
import com.netflix.servo.util.VisibleForTesting
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.googlecommon.batch.GoogleBatchRequest
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.IMAGES

@Slf4j
class GoogleImageCachingAgent extends AbstractGoogleCachingAgent {

  final Set<AgentDataType> providedDataTypes = [
      AUTHORITATIVE.forType(IMAGES.ns)
  ] as Set

  String agentType = "$accountName/$GoogleImageCachingAgent.simpleName"

  List<String> imageProjects
  List<String> baseImageProjects

  @VisibleForTesting
  GoogleImageCachingAgent() {}

  GoogleImageCachingAgent(String clouddriverUserAgentApplicationName,
                          GoogleNamedAccountCredentials credentials,
                          ObjectMapper objectMapper,
                          Registry registry,
                          List<String> imageProjects,
                          List<String> baseImageProjects) {
    super(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper,
          registry)
    this.imageProjects = imageProjects
    this.baseImageProjects = baseImageProjects
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<Image> imageList = loadImages()
    buildCacheResult(providerCache, imageList)
  }

  List<Image> loadImages() {
    List<Image> imageList = []
    List<String> allImageProjects = [project] + imageProjects + baseImageProjects - null

    // We want predictable iteration order that matches the order of insertion.
    LinkedHashMap<String, String> imageProjectToNextPageTokenMap = new LinkedHashMap<>()

    // This will ensure that each image project is queried.
    allImageProjects.each { imageProjectToNextPageTokenMap[it] = null }

    while (imageProjectToNextPageTokenMap) {
      GoogleBatchRequest imageListBatch = buildGoogleBatchRequest()
      AllImagesCallback<ImageList> imageListCallback =
        new AllImagesCallback(imageProjectToNextPageTokenMap: imageProjectToNextPageTokenMap, imageList: imageList)

      imageProjectToNextPageTokenMap.each { imageProject, pageToken ->
        Compute.Images.List imagesList = compute.images().list(imageProject)

        if (pageToken) {
          imagesList = imagesList.setPageToken(pageToken)
        }

        imageListBatch.queue(imagesList, imageListCallback)
      }

      executeIfRequestsAreQueued(imageListBatch, "ImageCaching.image")
    }

    return imageList
  }

  private CacheResult buildCacheResult(ProviderCache _, List<Image> imageList) {
    log.info("Describing items in ${agentType}")

    def cacheResultBuilder = new CacheResultBuilder()

    imageList.each { Image image ->
      def imageKey = Keys.getImageKey(accountName, image.getName())

      cacheResultBuilder.namespace(IMAGES.ns).keep(imageKey).with {
        attributes.image = image
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(IMAGES.ns).keepSize()} items in ${agentType}")

    cacheResultBuilder.build()
  }

  static List<Image> filterDeprecatedImages(ImageList imageListResult) {
    imageListResult?.items?.findAll { Image image ->
      !image.deprecated?.state
    } ?: []
  }

  class AllImagesCallback<ImageList> extends JsonBatchCallback<ImageList> implements FailureLogger {

    LinkedHashMap<String, String> imageProjectToNextPageTokenMap
    List<Image> imageList

    @Override
    void onSuccess(ImageList imageListResult, HttpHeaders responseHeaders) throws IOException {
      def nonDeprecatedImages = filterDeprecatedImages(imageListResult)
      if (nonDeprecatedImages) {
        imageList.addAll(nonDeprecatedImages)
      }

      def imageProject = GCEUtil.deriveProjectId(imageListResult.getSelfLink())

      if (imageListResult.nextPageToken) {
        imageProjectToNextPageTokenMap[imageProject] = imageListResult.nextPageToken
      } else {
        imageProjectToNextPageTokenMap.remove(imageProject)
      }
    }
  }
}
