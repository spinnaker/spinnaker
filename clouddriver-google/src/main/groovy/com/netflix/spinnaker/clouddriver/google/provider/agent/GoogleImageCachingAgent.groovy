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
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.model.Image
import com.google.api.services.compute.model.ImageList
import com.netflix.servo.util.VisibleForTesting
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
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

  GoogleImageCachingAgent(String googleApplicationName,
                          GoogleNamedAccountCredentials credentials,
                          ObjectMapper objectMapper,
                          List<String> imageProjects,
                          List<String> baseImageProjects) {
    super(googleApplicationName,
          credentials,
          objectMapper)
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

    BatchRequest imageRequest = buildBatchRequest()

    compute.images().list(project).queue(imageRequest, new AllImagesCallback(imageList: imageList))
    imageProjects.each {
      compute.images().list(it).queue(imageRequest, new AllImagesCallback(imageList: imageList))
    }
    baseImageProjects.each {
      compute.images().list(it).queue(imageRequest, new AllImagesCallback<ImageList>(imageList: imageList))
    }
    executeIfRequestsAreQueued(imageRequest)

    imageList
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

    List<Image> imageList

    @Override
    void onSuccess(ImageList imageListResult, HttpHeaders responseHeaders) throws IOException {
      def nonDeprecatedImages = filterDeprecatedImages(imageListResult)
      if (nonDeprecatedImages) {
        imageList.addAll(nonDeprecatedImages)
      }
    }
  }
}
