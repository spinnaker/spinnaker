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

package com.netflix.spinnaker.igor.docker

import com.netflix.discovery.DiscoveryClient
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.build.model.GenericArtifact
import com.netflix.spinnaker.igor.docker.model.DockerRegistryAccounts
import com.netflix.spinnaker.igor.docker.service.TaggedImage
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.DockerEvent
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor
import com.netflix.spinnaker.igor.polling.DeltaItem
import com.netflix.spinnaker.igor.polling.PollContext
import com.netflix.spinnaker.igor.polling.PollingDelta
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

import static net.logstash.logback.argument.StructuredArguments.kv

@Service
@SuppressWarnings('CatchException')
@ConditionalOnProperty('dockerRegistry.enabled')
class DockerMonitor extends CommonPollingMonitor<ImageDelta, DockerPollingDelta> {

    private final DockerRegistryCache cache
    private final DockerRegistryAccounts dockerRegistryAccounts
    private final Optional<EchoService> echoService

    @Autowired
    DockerMonitor(IgorConfigurationProperties properties,
                  Registry registry,
                  Optional<DiscoveryClient> discoveryClient,
                  DockerRegistryCache cache,
                  DockerRegistryAccounts dockerRegistryAccounts,
                  Optional<EchoService> echoService) {
        super(properties, registry, discoveryClient)
        this.cache = cache
        this.dockerRegistryAccounts = dockerRegistryAccounts
        this.echoService = echoService
    }

    @Override
    void initialize() {
    }

    @Override
    void poll() {
        dockerRegistryAccounts.updateAccounts()
        dockerRegistryAccounts.accounts.forEach({ account ->
            internalPoll(new PollContext((String) account.name, account))
        })
    }

    @Override
    DockerPollingDelta generateDelta(PollContext ctx) {
        String account = ctx.context.name
        Boolean trackDigests = ctx.context.trackDigests ?: false

        log.debug("Checking new tags for {}", account)
        List<String> cachedImages = cache.getImages(account)

        long startTime = System.currentTimeMillis()
        List<TaggedImage> images = dockerRegistryAccounts.service.getImagesByAccount(account)
        log.debug("Took ${System.currentTimeMillis() - startTime}ms to retrieve images (account: {})", kv("account", account))

        List<ImageDelta> delta = []
        images.parallelStream().forEach({ TaggedImage image ->
            String imageId = cache.makeKey(account, image.registry, image.repository, image.tag)
            if (shouldUpdateCache(cachedImages, imageId, image, trackDigests)) {
                delta.add(new ImageDelta(imageId: imageId, image: image))
            }
        })

        return new DockerPollingDelta(items: delta, cachedImages: cachedImages)
    }

    private boolean shouldUpdateCache(List<String> cachedImages, String imageId, TaggedImage image, boolean trackDigests) {
        boolean updateCache = false
        if (imageId in cachedImages) {
            if (trackDigests) {
                String lastDigest = cache.getLastDigest(image.account, image.registry, image.repository, image.tag)

                if (lastDigest != image.digest) {
                    log.info("Updated tagged image: {}: {}. Digest changed from [$lastDigest] -> [$image.digest].", kv("account", image.account), kv("image", imageId))
                    // If either is null, there was an error retrieving the manifest in this or the previous cache cycle.
                    updateCache = image.digest != null && lastDigest != null
                }
            }
        } else {
            updateCache = true
        }
        return updateCache
    }

    /**
     * IMPORTANT: We don't remove indexed images from igor due to the potential for
     * incomplete reads from clouddriver or Redis.
     */
    @Override
    void commitDelta(DockerPollingDelta delta) {
        delta.items.parallelStream().forEach({ ImageDelta item ->
            cache.setLastDigest(item.image.account, item.image.registry, item.image.repository, item.image.tag, item.image.digest)
            postEvent(delta.cachedImages, item.image, item.imageId)
        })
    }

    @Override
    String getName() {
        "dockerTagMonitor"
    }

    void postEvent(List<String> cachedImagesForAccount, TaggedImage image, String imageId) {
        if (!echoService.isPresent()) {
            log.warn("Cannot send tagged image notification: Echo is not enabled")
            registry.counter(missedNotificationId.withTag("monitor", getClass().simpleName)).increment()
            return
        }
        if (!cachedImagesForAccount) {
            // avoid publishing an event if this account has no indexed images (protects against a flushed redis)
            return
        }

        if (!echoService) {
            // avoid publishing an event if echo is disabled
            return
        }

        log.info("Sending tagged image info to echo: {}: {}", kv("account", image.account), kv("image", imageId))
        GenericArtifact dockerArtifact = new GenericArtifact("docker", image.repository, image.tag, "${image.registry}/${image.repository}:${image.tag}")
        dockerArtifact.metadata = [registry: image.registry]

        echoService.get().postEvent(new DockerEvent(content: new DockerEvent.Content(
            registry: image.registry,
            repository: image.repository,
            tag: image.tag,
            digest: image.digest,
            account: image.account,
        ), artifact: dockerArtifact))
    }

    @Override
    protected Integer getPartitionUpperThreshold(String partition) {
        return dockerRegistryAccounts.accounts.find { it.name == partition }?.itemUpperThreshold
    }

    private static class DockerPollingDelta implements PollingDelta<ImageDelta> {
        List<ImageDelta> items
        List<String> cachedImages
    }

    private static class ImageDelta implements DeltaItem {
        String imageId
        TaggedImage image
    }
}
