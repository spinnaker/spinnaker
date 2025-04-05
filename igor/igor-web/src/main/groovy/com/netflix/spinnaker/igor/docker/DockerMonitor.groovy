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

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.build.model.GenericArtifact
import com.netflix.spinnaker.igor.config.DockerRegistryProperties
import com.netflix.spinnaker.igor.docker.model.DockerRegistryAccounts
import com.netflix.spinnaker.igor.docker.service.TaggedImage
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.DockerEvent
import com.netflix.spinnaker.igor.keel.KeelService
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor
import com.netflix.spinnaker.igor.polling.DeltaItem
import com.netflix.spinnaker.igor.polling.LockService
import com.netflix.spinnaker.igor.polling.PollContext
import com.netflix.spinnaker.igor.polling.PollingDelta
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service

import java.util.concurrent.TimeUnit

import static net.logstash.logback.argument.StructuredArguments.kv

@Service
@SuppressWarnings('CatchException')
@ConditionalOnProperty(['services.clouddriver.base-url', 'docker-registry.enabled'])
class DockerMonitor extends CommonPollingMonitor<ImageDelta, DockerPollingDelta> {

    private final DockerRegistryCache cache
    private final DockerRegistryAccounts dockerRegistryAccounts
    private final Optional<EchoService> echoService
    private final Optional<KeelService> keelService
    private final DockerRegistryProperties dockerRegistryProperties

    @Autowired
    DockerMonitor(IgorConfigurationProperties properties,
                  Registry registry,
                  DynamicConfigService dynamicConfigService,
                  DiscoveryStatusListener discoveryStatusListener,
                  Optional < LockService > lockService,
                  DockerRegistryCache cache,
                  DockerRegistryAccounts dockerRegistryAccounts,
                  Optional<EchoService> echoService,
                  Optional<KeelService> keelService,
                  DockerRegistryProperties dockerRegistryProperties,
                  TaskScheduler taskScheduler) {
        super(properties, registry, dynamicConfigService, discoveryStatusListener, lockService, taskScheduler)
        this.cache = cache
        this.dockerRegistryAccounts = dockerRegistryAccounts
        this.echoService = echoService
        this.dockerRegistryProperties = dockerRegistryProperties
        this.keelService = keelService
    }

    @Override
    void poll(boolean sendEvents) {
        dockerRegistryAccounts.updateAccounts()
        dockerRegistryAccounts.accounts.forEach({ account ->
            pollSingle(new PollContext((String) account.name, account, !sendEvents))
        })
    }

    @Override
    PollContext getPollContext(String partition) {
        Map account = dockerRegistryAccounts.accounts.find { it.name == partition }
        if (account == null) {
            throw new IllegalStateException("Cannot find account named '$partition'")
        }
        return new PollContext((String) account.name, account)
    }

    @Override
    DockerPollingDelta generateDelta(PollContext ctx) {
        String account = ctx.context.name
        Boolean trackDigests = ctx.context.trackDigests ?: false

        log.trace("Checking new tags for {}", account)
        Set<String> cachedImages = cache.getImages(account)

        long startTime = System.currentTimeMillis()
        //Netflix is adding `includeDetails` flag to `getImagesByAccount`, in order to get a detailed response from the resgistry
        List<TaggedImage> images = AuthenticatedRequest.allowAnonymous { Retrofit2SyncCall.execute(dockerRegistryAccounts.service.getImagesByAccount(account, true)) }

        long endTime = System.currentTimeMillis()
        log.debug("Executed generateDelta:DockerMonitor with includeData=true in {}ms", endTime - startTime);

        registry.timer("pollingMonitor.docker.retrieveImagesByAccount", [new BasicTag("account", account)])
            .record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)

        List<ImageDelta> delta = []
        images.findAll { it != null }.forEach { TaggedImage image ->
            String imageId = new DockerRegistryV2Key(igorProperties.spinnaker.jedis.prefix, DockerRegistryCache.ID, account, image.repository, image.tag)
            UpdateType updateType = getUpdateType(cachedImages, imageId, image, trackDigests)
            if (updateType.updateCache) {
                delta.add(new ImageDelta(imageId: imageId, image: image, sendEvent: updateType.sendEvent))
            }
        }

        log.info("Found {} new images for {}. Images: {}", delta.size(), account, delta.collect {[imageId: it.imageId, sendEvent: it.sendEvent] })

        return new DockerPollingDelta(items: delta, cachedImages: cachedImages)
    }

    private UpdateType getUpdateType(Set<String> cachedImages, String imageId, TaggedImage image, boolean trackDigests) {
        if (!cachedImages.contains(imageId)) {
            // We have not seen this tag before; do a full update
            return UpdateType.full()
        }

        if (!trackDigests) {
            // We have seen this tag before and are not tracking digests, so there is nothing to update
            return UpdateType.none()
        }

        String lastDigest = cache.getLastDigest(image.account, image.repository, image.tag)
        if (lastDigest == image.digest || image.digest == null) {
            return UpdateType.none();
        }

        log.info("Updated tagged image: {}: {}. Digest changed from [$lastDigest] -> [$image.digest].", kv("account", image.account), kv("image", imageId))
        // If the last digest was null, update the cache but don't send events as we don't actually know if the digest
        // changed. This is to prevent triggering multiple pipelines when trackDigests is initially turned on.
        return lastDigest == null ? UpdateType.cacheOnly() : UpdateType.full()
    }

    /**
     * IMPORTANT: We don't remove indexed images from igor due to the potential for
     * incomplete reads from clouddriver or Redis.
     */
    @Override
    void commitDelta(DockerPollingDelta delta, boolean sendEvents) {
        delta.items.findAll { it != null }.forEach { ImageDelta item ->
            if (item != null) {
                cache.setLastDigest(item.image.account, item.image.repository, item.image.tag, item.image.digest)
                log.info("New tagged image: {}, {}. Digest is now [$item.image.digest].", kv("account", item.image.account), kv("image", item.imageId))
                if (sendEvents && item.sendEvent) {
                    postEvent(delta.cachedImages, item.image, item.imageId)
                } else {
                  if (!sendEvents) {
                    registry.counter(missedNotificationId.withTags("monitor", getName(), "reason", "fastForward")).increment()
                  } else {
                    registry.counter(missedNotificationId.withTags("monitor", getName(), "reason", "skippedDueToEmptyCache")).increment()
                  }
                }
            }
        }
    }

    @Override
    String getName() {
        "dockerTagMonitor"
    }

    void postEvent(Set<String> cachedImagesForAccount, TaggedImage image, String imageId) {
        if (!echoService.isPresent()) {
            log.warn("Cannot send tagged image notification: Echo is not enabled")
            registry.counter(missedNotificationId.withTags("monitor", getName(), "reason", "echoDisabled")).increment()
            return
        }
        if (!cachedImagesForAccount) {
            // avoid publishing an event if this account has no indexed images (protects against a flushed redis)
            return
        }

        log.info("Sending tagged image info to echo: {}: {}", kv("account", image.account), kv("image", imageId))
        GenericArtifact dockerArtifact = new GenericArtifact("docker", image.repository, image.tag, "${image.registry}/${image.repository}:${image.tag}")
        dockerArtifact.metadata = [registry: image.registry]

        AuthenticatedRequest.allowAnonymous {
          Retrofit2SyncCall.execute(echoService.get().postEvent(new DockerEvent(content: new DockerEvent.Content(
            registry: image.registry,
            repository: image.repository,
            tag: image.tag,
            digest: image.digest,
            account: image.account,
          ), artifact: dockerArtifact)))
        }

        if (keelService.isPresent()) {
          String imageReference = image.repository + ":" + image.tag
          Map <String, String> metadata = [fullname: imageReference, registry: image.account, tag: image.tag]
          Optional.ofNullable(image.buildNumber)
            .ifPresent({ buildNumber -> metadata.put("buildNumber", buildNumber.toString()) })
          Optional.ofNullable(image.commitId)
            .ifPresent({ commitId -> metadata.put("commitId", commitId.toString()) })
          Optional.ofNullable(image.date)
            .ifPresent({ date -> metadata.put("date", date.toString()) })
          Optional.ofNullable(image.branch)
            .ifPresent({ branch -> metadata.put("branch", branch.toString()) })


          Artifact artifact = Artifact.builder()
            .type("DOCKER")
            .customKind(false)
            .name(image.repository)
            .version(image.tag)
            .location(image.account)
            .reference(imageId)
            .metadata(metadata)
            .provenance(image.registry)
            .build()

          Map artifactEvent = [
            payload: [artifacts: [artifact], details: [:]],
            eventName: "spinnaker_artifacts_docker"
          ]
          AuthenticatedRequest.allowAnonymous { Retrofit2SyncCall.execute(keelService.get().sendArtifactEvent(artifactEvent)) }
        }
    }

    @Override
    protected Integer getPartitionUpperThreshold(String partition) {
        def upperThreshold = dockerRegistryAccounts.accounts.find { it.name == partition }?.itemUpperThreshold
        if (!upperThreshold) {
            upperThreshold = dockerRegistryProperties.itemUpperThreshold
        }
        return upperThreshold
    }

    private static class DockerPollingDelta implements PollingDelta<ImageDelta> {
        List<ImageDelta> items
        Set<String> cachedImages
    }

    private static class ImageDelta implements DeltaItem {
        String imageId
        TaggedImage image
        boolean sendEvent = true
    }

    private static class UpdateType {
        final boolean updateCache
        final boolean sendEvent

        private UpdateType(boolean updateCache, boolean sendEvent) {
            this.updateCache = updateCache
            this.sendEvent = sendEvent
        }

        static UpdateType full() {
            return new UpdateType(true, true);
        }

        static UpdateType cacheOnly() {
            return new UpdateType(true, false)
        }

        static UpdateType none() {
            return new UpdateType(false, false)
        }
    }
}
