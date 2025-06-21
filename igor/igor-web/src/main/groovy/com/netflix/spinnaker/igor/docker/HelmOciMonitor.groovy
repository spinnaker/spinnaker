/*
 * Copyright 2025 Harness, Inc.
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
import com.netflix.spinnaker.igor.config.HelmOciDockerRegistryProperties
import com.netflix.spinnaker.igor.docker.model.DockerRegistryAccounts
import com.netflix.spinnaker.igor.docker.service.TaggedImage
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.DockerEvent
import com.netflix.spinnaker.igor.keel.KeelService
import com.netflix.spinnaker.igor.polling.LockService
import com.netflix.spinnaker.igor.polling.PollContext
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service

import java.util.concurrent.TimeUnit

import static net.logstash.logback.argument.StructuredArguments.kv

/**
 * Monitor for Helm OCI repositories that polls for new tags.
 * Extends DockerMonitor to leverage existing Docker registry functionality
 * while adding specific behavior for Helm OCI charts.
 */
@Service
@SuppressWarnings('CatchException')
@ConditionalOnProperty(['services.clouddriver.base-url', 'helm-oci-docker-registry.enabled'])
class HelmOciMonitor extends DockerMonitor {

  private final HelmOciDockerRegistryCache cache
  private final DockerRegistryAccounts dockerRegistryAccounts
  private final Optional<EchoService> echoService
  private final Optional<KeelService> keelService
  private final HelmOciDockerRegistryProperties helmOciDockerRegistryProperties

    @Autowired
    HelmOciMonitor(IgorConfigurationProperties properties,
                  Registry registry,
                  DynamicConfigService dynamicConfigService,
                  DiscoveryStatusListener discoveryStatusListener,
                  Optional<LockService> lockService,
                  @Qualifier("HelmOciDockerRegistryCache") HelmOciDockerRegistryCache cache,
                  DockerRegistryAccounts dockerRegistryAccounts,
                  Optional<EchoService> echoService,
                  Optional<KeelService> keelService,
                  HelmOciDockerRegistryProperties helmOciDockerRegistryProperties,
                  TaskScheduler taskScheduler) {
        super(properties, registry, dynamicConfigService, discoveryStatusListener, lockService, cache,
              dockerRegistryAccounts, echoService, keelService, null, taskScheduler)
      this.cache = cache
      this.dockerRegistryAccounts = dockerRegistryAccounts
      this.echoService = echoService
      this.helmOciDockerRegistryProperties = helmOciDockerRegistryProperties
      this.keelService = keelService
    }

    @Override
    String getName() {
        "helmOciTagMonitor"
    }

    @Override
    protected Integer getPartitionUpperThreshold(String partition) {
        def upperThreshold = dockerRegistryAccounts.accounts.find { it.name == partition }?.itemUpperThreshold
        if (!upperThreshold) {
            upperThreshold = helmOciDockerRegistryProperties.itemUpperThreshold
        }
        return upperThreshold
    }

    @Override
    DockerPollingDelta generateDelta(PollContext ctx) {
      String account = ctx.context.name
      Boolean trackDigests = ctx.context.trackDigests ?: false

      log.trace("Checking new tags for {}", account)
      Set<String> cachedImages = cache.getImages(account)

      long startTime = System.currentTimeMillis()
      List<TaggedImage> images = AuthenticatedRequest.allowAnonymous {
        Retrofit2SyncCall.execute(dockerRegistryAccounts.service.getChartImagesByAccount(account, true))
      }

      long endTime = System.currentTimeMillis()
      log.debug("Executed generateDelta:HelmOciMonitor with includeData=true in {}ms", endTime - startTime);

      registry.timer("pollingMonitor.docker.retrieveChartsByAccount", [new BasicTag("account", account)])
        .record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)

      List<ImageDelta> delta = []
      images.findAll { it != null }.forEach { TaggedImage image ->
        String imageId = new DockerRegistryV2Key(igorProperties.spinnaker.jedis.prefix, HelmOciDockerRegistryCache.ID, account, image.repository, image.tag)
        UpdateType updateType = getUpdateType(cachedImages, imageId, image, trackDigests)
        if (updateType.updateCache) {
          delta.add(new ImageDelta(imageId: imageId, image: image, sendEvent: updateType.sendEvent))
        }
      }

      log.info("Found {} new images for {}. Images: {}", delta.size(), account, delta.collect {[imageId: it.imageId, sendEvent: it.sendEvent] })

      return new DockerPollingDelta(items: delta, cachedImages: cachedImages)
    }

    @Override
    void postEvent(Set<String> cachedImagesForAccount, TaggedImage image, String imageId) {
        if (!echoService.isPresent()) {
            log.warn("Cannot send tagged Helm OCI image notification: Echo is not enabled")
            registry.counter(missedNotificationId.withTags("monitor", getName(), "reason", "echoDisabled")).increment()
            return
        }
        if (!cachedImagesForAccount) {
            // avoid publishing an event if this account has no indexed images (protects against a flushed redis)
            return
        }

        log.info("Sending tagged Helm OCI image info to echo: {}: {}", kv("account", image.account), kv("image", imageId))

        // For Helm OCI, we use a different artifact type to distinguish from regular Docker images
        GenericArtifact helmOciArtifact = new GenericArtifact("helm/image", image.repository, image.tag, "${image.registry}/${image.repository}:${image.tag}")
        helmOciArtifact.metadata = [registry: image.registry]

        AuthenticatedRequest.allowAnonymous {
          DockerEvent event = new DockerEvent()
          event.setType("helm/oci")
          event.setContent(new DockerEvent.Content(
            registry: image.registry,
            repository: image.repository,
            tag: image.tag,
            digest: image.digest,
            account: image.account,
          ))
          event.setArtifact(helmOciArtifact)
          Retrofit2SyncCall.execute(echoService.get().postEvent(event))
        }

        if (keelService.isPresent()) {
          String imageReference = image.repository + ":" + image.tag
          Map<String, String> metadata = [
            fullname: imageReference,
            registry: image.account,
            tag: image.tag,
            type: "helm/image"
          ]
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
            eventName: "spinnaker_artifacts_helm_oci"
          ]
          AuthenticatedRequest.allowAnonymous { Retrofit2SyncCall.execute(keelService.get().sendArtifactEvent(artifactEvent)) }
        }
    }
}
