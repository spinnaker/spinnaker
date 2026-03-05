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
import com.netflix.spinnaker.igor.config.HelmOciDockerRegistryProperties;
import com.netflix.spinnaker.igor.docker.model.DockerRegistryAccounts;
import com.netflix.spinnaker.igor.docker.service.TaggedImage;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.history.model.DockerEvent;
import com.netflix.spinnaker.igor.keel.KeelService;
import com.netflix.spinnaker.igor.polling.LockService;
import com.netflix.spinnaker.igor.polling.PollContext;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

/**
 * Monitor for Helm OCI repositories that polls for new tags. Extends DockerMonitor to leverage
 * existing Docker registry functionality while adding specific behavior for Helm OCI charts.
 */
@Service
@ConditionalOnProperty({"services.clouddriver.base-url", "helm-oci-docker-registry.enabled"})
@Slf4j
public class HelmOciMonitor extends DockerMonitor {

  private final HelmOciDockerRegistryCache cache;
  private final DockerRegistryAccounts dockerRegistryAccounts;
  private final Optional<EchoService> echoService;
  private final Optional<KeelService> keelService;
  private final HelmOciDockerRegistryProperties helmOciDockerRegistryProperties;

  @Autowired
  public HelmOciMonitor(
      IgorConfigurationProperties properties,
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
    super(
        properties,
        registry,
        dynamicConfigService,
        discoveryStatusListener,
        lockService,
        cache,
        dockerRegistryAccounts,
        echoService,
        keelService,
        null,
        taskScheduler);
    this.cache = cache;
    this.dockerRegistryAccounts = dockerRegistryAccounts;
    this.echoService = echoService;
    this.helmOciDockerRegistryProperties = helmOciDockerRegistryProperties;
    this.keelService = keelService;
  }

  @Override
  public String getName() {
    return "helmOciTagMonitor";
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

    if (upperThreshold == null) {
      upperThreshold = helmOciDockerRegistryProperties.getItemUpperThreshold();
    }

    return upperThreshold;
  }

  @Override
  public DockerPollingDelta generateDelta(PollContext ctx) {
    String account = ctx.partitionName;
    Boolean trackDigests =
        ctx.context != null ? (Boolean) ctx.context.getOrDefault("trackDigests", false) : false;

    log.trace("Checking new tags for {}", account);
    Set<String> cachedImages = cache.getImages(account);

    long startTime = System.currentTimeMillis();
    List<TaggedImage> images =
        AuthenticatedRequest.allowAnonymous(
            () ->
                Retrofit2SyncCall.execute(
                    dockerRegistryAccounts.getService().getChartImagesByAccount(account, true)));

    long endTime = System.currentTimeMillis();
    log.debug(
        "Executed generateDelta:HelmOciMonitor with includeData=true in {}ms", endTime - startTime);

    registry
        .timer(
            "pollingMonitor.docker.retrieveChartsByAccount",
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
                          HelmOciDockerRegistryCache.ID,
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

  @Override
  public void postEvent(Set<String> cachedImagesForAccount, TaggedImage image, String imageId) {
    if (!echoService.isPresent()) {
      log.warn("Cannot send tagged Helm OCI image notification: Echo is not enabled");
      registry
          .counter(missedNotificationId.withTags("monitor", getName(), "reason", "echoDisabled"))
          .increment();
      return;
    }
    if (cachedImagesForAccount == null || cachedImagesForAccount.isEmpty()) {
      // avoid publishing an event if this account has no indexed images (protects against aflushed
      // redis)
      return;
    }

    log.info(
        "Sending tagged Helm OCI image info to echo: {}: {}",
        kv("account", image.getAccount()),
        kv("image", imageId));

    // For Helm OCI, we use a different artifact type to distinguish from regular Docker images
    GenericArtifact helmOciArtifact =
        new GenericArtifact(
            "helm/image",
            image.getRepository(),
            image.getTag(),
            image.getRegistry() + "/" + image.getRepository() + ":" + image.getTag());
    helmOciArtifact.setMetadata(Map.of("registry", image.getRegistry()));

    DockerEvent event = new DockerEvent();
    event.setType("helm/oci");
    DockerEvent.Content content = new DockerEvent.Content();
    content.setRegistry(image.getRegistry());
    content.setRepository(image.getRepository());
    content.setTag(image.getTag());
    content.setDigest(image.getDigest());
    content.setAccount(image.getAccount());
    event.setContent(content);
    event.setArtifact(helmOciArtifact);
    AuthenticatedRequest.allowAnonymous(
        () -> Retrofit2SyncCall.execute(echoService.get().postEvent(event)));

    if (keelService.isPresent()) {
      String imageReference = image.getRepository() + ":" + image.getTag();
      Map<String, Object> metadata = new HashMap<>();
      metadata.put("fullname", imageReference);
      metadata.put("registry", image.getAccount());
      metadata.put("tag", image.getTag());
      metadata.put("type", "helm/image");

      Optional.ofNullable(image.getBuildNumber())
          .ifPresent(buildNumber -> metadata.put("buildNumber", buildNumber.toString()));
      Optional.ofNullable(image.getCommitId())
          .ifPresent(commitId -> metadata.put("commitId", commitId.toString()));
      Optional.ofNullable(image.getDate()).ifPresent(date -> metadata.put("date", date.toString()));
      Optional.ofNullable(image.getBranch())
          .ifPresent(branch -> metadata.put("branch", branch.toString()));

      Artifact artifact =
          Artifact.builder()
              .type("DOCKER")
              .customKind(false)
              .name(image.getRepository())
              .version(image.getTag())
              .location(image.getAccount())
              .reference(imageId)
              .metadata(metadata)
              .provenance(image.getRegistry())
              .build();

      Map<String, Object> artifactEvent =
          Map.of(
              "payload",
              Map.of("artifacts", List.of(artifact), "details", Map.of()),
              "eventName",
              "spinnaker_artifacts_helm_oci");

      AuthenticatedRequest.allowAnonymous(
          () -> {
            Retrofit2SyncCall.execute(keelService.get().sendArtifactEvent(artifactEvent));
            return null;
          });
    }
  }
}
