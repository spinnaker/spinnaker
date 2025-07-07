/*
 * Copyright 2025 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.igor.docker;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.build.model.GenericArtifact;
import com.netflix.spinnaker.igor.config.DockerRegistryProperties;
import com.netflix.spinnaker.igor.docker.model.DockerRegistryAccounts;
import com.netflix.spinnaker.igor.docker.service.TaggedImage;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.history.model.DockerEvent;
import com.netflix.spinnaker.igor.keel.KeelService;
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor;
import com.netflix.spinnaker.igor.polling.DeltaItem;
import com.netflix.spinnaker.igor.polling.LockService;
import com.netflix.spinnaker.igor.polling.PollContext;
import com.netflix.spinnaker.igor.polling.PollingDelta;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty({"services.clouddriver.base-url", "docker-registry.enabled"})
@Slf4j
public class DockerMonitor
    extends CommonPollingMonitor<DockerMonitor.ImageDelta, DockerMonitor.DockerPollingDelta> {

  private final DockerRegistryCache cache;
  private final DockerRegistryAccounts dockerRegistryAccounts;
  private final Optional<EchoService> echoService;
  private final Optional<KeelService> keelService;
  private final DockerRegistryProperties dockerRegistryProperties;

  @Autowired
  public DockerMonitor(
      IgorConfigurationProperties properties,
      Registry registry,
      DynamicConfigService dynamicConfigService,
      DiscoveryStatusListener discoveryStatusListener,
      Optional<LockService> lockService,
      @Qualifier("DockerRegistryCache") DockerRegistryCache cache,
      DockerRegistryAccounts dockerRegistryAccounts,
      Optional<EchoService> echoService,
      Optional<KeelService> keelService,
      DockerRegistryProperties dockerRegistryProperties,
      TaskScheduler taskScheduler) {
    super(
        properties,
        registry,
        dynamicConfigService,
        discoveryStatusListener,
        lockService,
        taskScheduler);
    this.cache = cache;
    this.dockerRegistryAccounts = dockerRegistryAccounts;
    this.echoService = echoService;
    this.dockerRegistryProperties = dockerRegistryProperties;
    this.keelService = keelService;
  }

  @Override
  public void poll(boolean sendEvents) {
    dockerRegistryAccounts.updateAccounts();
    dockerRegistryAccounts
        .getAccounts()
        .forEach(
            account -> {
              pollSingle(new PollContext((String) account.get("name"), account, !sendEvents));
            });
  }

  @Override
  public PollContext getPollContext(String partition) {
    Map<String, Object> account =
        dockerRegistryAccounts.getAccounts().stream()
            .filter(it -> partition.equals(it.get("name")))
            .findFirst()
            .orElse(null);

    if (account == null) {
      throw new IllegalStateException("Cannot find account named '" + partition + "'");
    }
    return new PollContext((String) account.get("name"), account);
  }

  @Override
  public DockerPollingDelta generateDelta(PollContext ctx) {
    String account = (String) ctx.context.get("name");
    Boolean trackDigests = (Boolean) ctx.context.getOrDefault("trackDigests", false);

    log.trace("Checking new tags for {}", account);
    Set<String> cachedImages = cache.getImages(account);

    long startTime = System.currentTimeMillis();
    // Netflix is adding `includeDetails` flag to `getImagesByAccount`, in order to get a detailed
    // response from the registry
    List<TaggedImage> images =
        AuthenticatedRequest.allowAnonymous(
            () ->
                Retrofit2SyncCall.execute(
                    dockerRegistryAccounts.getService().getImagesByAccount(account, true)));

    long endTime = System.currentTimeMillis();
    log.debug(
        "Executed generateDelta:DockerMonitor with includeData=true in {}ms", endTime - startTime);

    registry
        .timer(
            "pollingMonitor.docker.retrieveImagesByAccount",
            Collections.singleton(new BasicTag("account", account)))
        .record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);

    List<ImageDelta> delta = new ArrayList<>();
    images.stream()
        .filter(it -> it != null)
        .forEach(
            image -> {
              String imageId =
                  new DockerRegistryV2Key(
                          igorProperties.getSpinnaker().getJedis().getPrefix(),
                          DockerRegistryCache.ID,
                          account,
                          image.getRepository(),
                          image.getTag())
                      .toString();
              UpdateType updateType = getUpdateType(cachedImages, imageId, image, trackDigests);
              if (updateType.isUpdateCache()) {
                delta.add(new ImageDelta(imageId, image, updateType.isSendEvent()));
              }
            });

    log.info("Found {} new images for {}. Images: {}", delta.size(), account, delta);

    return new DockerPollingDelta(delta, cachedImages);
  }

  protected UpdateType getUpdateType(
      Set<String> cachedImages, String imageId, TaggedImage image, boolean trackDigests) {
    if (!cachedImages.contains(imageId)) {
      // We have not seen this tag before; do a full update
      return UpdateType.full();
    }

    if (!trackDigests) {
      // We have seen this tag before and are not tracking digests, so there is nothing to update
      return UpdateType.none();
    }

    String lastDigest =
        cache.getLastDigest(image.getAccount(), image.getRepository(), image.getTag());
    if (lastDigest != null && lastDigest.equals(image.getDigest()) || image.getDigest() == null) {
      return UpdateType.none();
    }

    log.info(
        "Updated tagged image: {}: {}. Digest changed from [{}] -> [{}].",
        kv("account", image.getAccount()),
        kv("image", imageId),
        lastDigest,
        image.getDigest());

    // If the last digest was null, update the cache but don't send events as we don't actually know
    // if the digest
    // changed. This is to prevent triggering multiple pipelines when trackDigests is initially
    // turned on.
    return lastDigest == null ? UpdateType.cacheOnly() : UpdateType.full();
  }

  /**
   * IMPORTANT: We don't remove indexed images from igor due to the potential for incomplete reads
   * from clouddriver or Redis.
   */
  @Override
  public void commitDelta(DockerPollingDelta delta, boolean sendEvents) {
    delta.getItems().stream()
        .filter(it -> it != null)
        .forEach(
            item -> {
              if (item != null) {
                cache.setLastDigest(
                    item.getImage().getAccount(),
                    item.getImage().getRepository(),
                    item.getImage().getTag(),
                    item.getImage().getDigest());
                log.info(
                    "New tagged image: {}, {}. Digest is now [{}].",
                    kv("account", item.getImage().getAccount()),
                    kv("image", item.getImageId()),
                    item.getImage().getDigest());

                if (sendEvents && item.isSendEvent()) {
                  postEvent(delta.getCachedImages(), item.getImage(), item.getImageId());
                } else {
                  if (!sendEvents) {
                    registry
                        .counter(
                            missedNotificationId.withTags(
                                "monitor", getName(), "reason", "fastForward"))
                        .increment();
                  } else {
                    registry
                        .counter(
                            missedNotificationId.withTags(
                                "monitor", getName(), "reason", "skippedDueToEmptyCache"))
                        .increment();
                  }
                }
              }
            });
  }

  @Override
  public String getName() {
    return "dockerTagMonitor";
  }

  public void postEvent(Set<String> cachedImagesForAccount, TaggedImage image, String imageId) {
    if (!echoService.isPresent()) {
      log.warn("Cannot send tagged image notification: Echo is not enabled");
      registry
          .counter(missedNotificationId.withTags("monitor", getName(), "reason", "echoDisabled"))
          .increment();
      return;
    }
    if (cachedImagesForAccount == null || cachedImagesForAccount.isEmpty()) {
      // avoid publishing an event if this account has no indexed images (protects against a flushed
      // redis)
      return;
    }

    log.info(
        "Sending tagged image info to echo: {}: {}",
        kv("account", image.getAccount()),
        kv("image", imageId));

    GenericArtifact dockerArtifact =
        new GenericArtifact(
            "docker",
            image.getRepository(),
            image.getTag(),
            image.getRegistry() + "/" + image.getRepository() + ":" + image.getTag());
    dockerArtifact.setMetadata(Map.of("registry", image.getRegistry()));

    DockerEvent event = new DockerEvent();
    DockerEvent.Content content = new DockerEvent.Content();
    content.setRegistry(image.getRegistry());
    content.setRepository(image.getRepository());
    content.setTag(image.getTag());
    content.setDigest(image.getDigest());
    content.setAccount(image.getAccount());
    event.setContent(content);
    event.setArtifact(dockerArtifact);
    AuthenticatedRequest.allowAnonymous(
        () -> Retrofit2SyncCall.execute(echoService.get().postEvent(event)));

    if (keelService.isPresent()) {
      String imageReference = image.getRepository() + ":" + image.getTag();
      Map<String, String> metadata =
          Map.of(
              "fullname", imageReference,
              "registry", image.getAccount(),
              "tag", image.getTag());

      // Create a mutable copy of the metadata map to add optional fields
      Map<String, Object> mutableMetadata = new HashMap<>(metadata);

      Optional.ofNullable(image.getBuildNumber())
          .ifPresent(buildNumber -> mutableMetadata.put("buildNumber", buildNumber));
      Optional.ofNullable(image.getCommitId())
          .ifPresent(commitId -> mutableMetadata.put("commitId", commitId));
      Optional.ofNullable(image.getDate()).ifPresent(date -> mutableMetadata.put("date", date));
      Optional.ofNullable(image.getBranch())
          .ifPresent(branch -> mutableMetadata.put("branch", branch));

      Artifact artifact =
          Artifact.builder()
              .type("DOCKER")
              .customKind(false)
              .name(image.getRepository())
              .version(image.getTag())
              .location(image.getAccount())
              .reference(imageId)
              .metadata(mutableMetadata)
              .provenance(image.getRegistry())
              .build();

      Map<String, Object> artifactEvent =
          Map.of(
              "payload",
              Map.of("artifacts", List.of(artifact), "details", Map.of()),
              "eventName",
              "spinnaker_artifacts_docker");

      AuthenticatedRequest.allowAnonymous(
          () -> Retrofit2SyncCall.execute(keelService.get().sendArtifactEvent(artifactEvent)));
    }
  }

  @Override
  protected Integer getPartitionUpperThreshold(String partition) {
    Optional<Map> account =
        dockerRegistryAccounts.getAccounts().stream()
            .filter(it -> partition.equals(it.get("name")))
            .findFirst();

    Integer upperThreshold = null;
    if (account.isPresent() && account.get().containsKey("itemUpperThreshold")) {
      upperThreshold = (Integer) account.get().get("itemUpperThreshold");
    }

    if (upperThreshold == null && dockerRegistryProperties != null) {
      upperThreshold = dockerRegistryProperties.getItemUpperThreshold();
    }

    return upperThreshold;
  }

  @Data
  public static class DockerPollingDelta implements PollingDelta<ImageDelta> {
    private List<ImageDelta> items;
    private Set<String> cachedImages;

    public DockerPollingDelta(List<ImageDelta> items, Set<String> cachedImages) {
      this.items = items;
      this.cachedImages = cachedImages;
    }
  }

  @Data
  protected static class ImageDelta implements DeltaItem {
    private String imageId;
    private TaggedImage image;
    private boolean sendEvent;

    protected ImageDelta(String imageId, TaggedImage image, boolean sendEvent) {
      this.imageId = imageId;
      this.image = image;
      this.sendEvent = sendEvent;
    }
  }

  protected static class UpdateType {
    private final boolean updateCache;
    private final boolean sendEvent;

    private UpdateType(boolean updateCache, boolean sendEvent) {
      this.updateCache = updateCache;
      this.sendEvent = sendEvent;
    }

    static UpdateType full() {
      return new UpdateType(true, true);
    }

    static UpdateType cacheOnly() {
      return new UpdateType(true, false);
    }

    static UpdateType none() {
      return new UpdateType(false, false);
    }

    boolean isUpdateCache() {
      return updateCache;
    }

    boolean isSendEvent() {
      return sendEvent;
    }
  }
}
