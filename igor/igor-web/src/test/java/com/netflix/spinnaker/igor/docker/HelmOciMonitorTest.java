/*
 * Copyright 2025 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.config.HelmOciDockerRegistryProperties;
import com.netflix.spinnaker.igor.docker.model.DockerRegistryAccounts;
import com.netflix.spinnaker.igor.docker.service.TaggedImage;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.history.model.DockerEvent;
import com.netflix.spinnaker.igor.keel.KeelService;
import com.netflix.spinnaker.igor.polling.LockService;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
public class HelmOciMonitorTest {

  private IgorConfigurationProperties properties = new IgorConfigurationProperties();
  private NoopRegistry registry = new NoopRegistry();
  private DynamicConfigService dynamicConfig = new DynamicConfigService.NoopDynamicConfig();
  private DiscoveryStatusListener discoveryStatusListener = new DiscoveryStatusListener(true);
  private Optional<LockService> lockService = Optional.empty();

  private Void voidResponse = null;

  @Mock private HelmOciDockerRegistryCache helmOciDockerRegistryCache;
  @Mock private DockerRegistryAccounts dockerRegistryAccounts;
  @Mock private EchoService echoService;
  @Mock private KeelService keelService;
  @Mock private TaskScheduler taskScheduler;

  private HelmOciDockerRegistryProperties helmOciDockerRegistryProperties =
      new HelmOciDockerRegistryProperties();

  @BeforeEach
  public void setUp() {
    helmOciDockerRegistryProperties.setEnabled(true);
    helmOciDockerRegistryProperties.setItemUpperThreshold(5);
  }

  @ParameterizedTest
  @MethodSource("cachedImagesProvider")
  public void shouldOnlyPublishEventsIfAccountHasBeenIndexedPreviously(
      Set<String> cachedImages, int echoServiceCallCount) {
    // given
    TaggedImage taggedImage = new TaggedImage();
    taggedImage.setTag("tag");
    taggedImage.setAccount("account");
    taggedImage.setRegistry("registry");
    taggedImage.setRepository("repository");
    taggedImage.setDigest("digest");

    // Configure mocks
    if (echoServiceCallCount > 0) {
      when(keelService.sendArtifactEvent(any())).thenReturn(Calls.response(voidResponse));
      when(echoService.postEvent(any())).thenReturn(Calls.response(voidResponse));
    }

    // when
    new HelmOciMonitor(
            properties,
            registry,
            dynamicConfig,
            discoveryStatusListener,
            lockService,
            helmOciDockerRegistryCache,
            dockerRegistryAccounts,
            Optional.of(echoService),
            Optional.of(keelService),
            helmOciDockerRegistryProperties,
            taskScheduler)
        .postEvent(cachedImages, taggedImage, "imageId");

    // then
    verify(echoService, times(echoServiceCallCount)).postEvent(any());
  }

  private static Stream<Arguments> cachedImagesProvider() {
    return Stream.of(
        Arguments.of(null, 0),
        Arguments.of(Collections.emptySet(), 0),
        Arguments.of(Collections.singleton("job1"), 1));
  }

  @Test
  public void shouldShortCircuitIfEchoServiceIsNotAvailable() {
    // given
    TaggedImage taggedImage = new TaggedImage();
    taggedImage.setTag("tag");
    taggedImage.setAccount("account");
    taggedImage.setRegistry("registry");
    taggedImage.setRepository("repository");
    taggedImage.setDigest("digest");

    Set<String> cachedImages = Collections.singleton("imageId");

    // Configure mocks
    when(keelService.sendArtifactEvent(any())).thenReturn(Calls.response(voidResponse));
    when(echoService.postEvent(any())).thenReturn(Calls.response(voidResponse));

    // when & then - no exception is thrown
    createSubject().postEvent(cachedImages, taggedImage, "imageId");
  }

  @Test
  public void shouldIncludeDecoratedArtifactInThePayload() {
    // given
    TaggedImage taggedImage = new TaggedImage();
    taggedImage.setTag("tag");
    taggedImage.setAccount("account");
    taggedImage.setRegistry("registry");
    taggedImage.setRepository("repository");
    taggedImage.setDigest("digest");
    taggedImage.setBuildNumber("111");
    taggedImage.setCommitId("ab12c3");
    taggedImage.setDate("1598707355157");
    taggedImage.setBranch("master");

    Set<String> cachedImages = Collections.singleton("job1");

    // Configure mocks
    ArgumentCaptor<DockerEvent> echoEventCaptor = ArgumentCaptor.forClass(DockerEvent.class);
    ArgumentCaptor<Map<String, Object>> keelEventCaptor = ArgumentCaptor.forClass(Map.class);

    when(echoService.postEvent(echoEventCaptor.capture())).thenReturn(Calls.response(voidResponse));
    when(keelService.sendArtifactEvent(keelEventCaptor.capture()))
        .thenReturn(Calls.response(voidResponse));

    // when
    createSubject().postEvent(cachedImages, taggedImage, "imageId");

    // then
    DockerEvent capturedEchoEvent = echoEventCaptor.getValue();
    assertEquals(taggedImage.getTag(), capturedEchoEvent.getArtifact().getVersion());
    assertEquals(taggedImage.getRepository(), capturedEchoEvent.getArtifact().getName());
    assertEquals("helm/image", capturedEchoEvent.getArtifact().getType());
    assertEquals("registry/repository:tag", capturedEchoEvent.getArtifact().getReference());
    assertEquals(
        taggedImage.getRegistry(), capturedEchoEvent.getArtifact().getMetadata().get("registry"));
    assertEquals("helm/oci", capturedEchoEvent.getDetails().get("type"));

    Map<String, Object> capturedKeelEvent = keelEventCaptor.getValue();
    Map<String, Object> payload = (Map<String, Object>) capturedKeelEvent.get("payload");
    List<Artifact> artifacts = (List<Artifact>) payload.get("artifacts");
    assertEquals(1, artifacts.size());
    Artifact artifact = artifacts.get(0);
    assertEquals("repository", artifact.getName());
    assertEquals("DOCKER", artifact.getType());
    Map<String, Object> metadata = artifact.getMetadata();
    assertEquals("tag", metadata.get("tag"));
    assertEquals("111", metadata.get("buildNumber"));
    assertEquals("ab12c3", metadata.get("commitId"));
    assertEquals("1598707355157", metadata.get("date"));
    assertEquals("master", metadata.get("branch"));
    assertEquals("helm/image", metadata.get("type"));
    assertEquals("spinnaker_artifacts_helm_oci", capturedKeelEvent.get("eventName"));
  }

  @Test
  public void shouldNotIncludeBuildAndCommitDetailsIfMissingFromTaggedImage() {
    // given
    TaggedImage taggedImageWithoutMetadata = new TaggedImage();
    taggedImageWithoutMetadata.setTag("new-tag");
    taggedImageWithoutMetadata.setAccount("account");
    taggedImageWithoutMetadata.setRegistry("registry");
    taggedImageWithoutMetadata.setRepository("repository");
    taggedImageWithoutMetadata.setDigest("digest");

    Set<String> cachedImages = Collections.singleton("job1");

    // Configure mocks
    when(echoService.postEvent(any())).thenReturn(Calls.response(voidResponse));
    ArgumentCaptor<Map<String, Object>> keelEventCaptor = ArgumentCaptor.forClass(Map.class);
    when(keelService.sendArtifactEvent(keelEventCaptor.capture()))
        .thenReturn(Calls.response(voidResponse));

    // when
    createSubject().postEvent(cachedImages, taggedImageWithoutMetadata, "imageId");

    // then
    Map<String, Object> capturedKeelEvent = keelEventCaptor.getValue();
    Map<String, Object> payload = (Map<String, Object>) capturedKeelEvent.get("payload");
    List<Artifact> artifacts = (List<Artifact>) payload.get("artifacts");
    assertEquals(1, artifacts.size());
    Artifact artifact = artifacts.get(0);
    assertEquals("repository", artifact.getName());
    assertEquals("DOCKER", artifact.getType());
    Map<String, Object> metadata = artifact.getMetadata();
    assertEquals("new-tag", metadata.get("tag"));
    assertFalse(metadata.containsKey("buildNumber"));
    assertFalse(metadata.containsKey("commitId"));
    assertEquals("helm/image", metadata.get("type"));
  }

  @ParameterizedTest
  @MethodSource("updateTypeProvider")
  public void shouldUpdateCacheIfImageIsNotAlreadyCached(
      String tag,
      String digest,
      String cachedDigest,
      boolean trackDigest,
      boolean expectedUpdateCache,
      boolean expectedSendEvent) {
    // given
    HelmOciMonitor subject = createSubject();
    Set<String> cachedImages =
        new HashSet<>(
            Arrays.asList(
                "prefix:" + HelmOciDockerRegistryCache.ID + ":v2:account:registry:tag",
                "prefix:" + HelmOciDockerRegistryCache.ID + ":v2:account:anotherregistry:tag"));

    // Configure mocks
    if (trackDigest) {
      when(helmOciDockerRegistryCache.getLastDigest(anyString(), anyString(), anyString()))
          .thenReturn(cachedDigest);
    }
    // when
    TaggedImage taggedImage = new TaggedImage();
    taggedImage.setTag(tag);
    taggedImage.setAccount("account");
    taggedImage.setRegistry("registry");
    taggedImage.setRepository("repository");
    taggedImage.setDigest(digest);

    DockerMonitor.UpdateType result =
        subject.getUpdateType(
            cachedImages, keyFromTaggedImage(taggedImage), taggedImage, trackDigest);

    // then
    assertEquals(expectedUpdateCache, result.isUpdateCache());
    assertEquals(expectedSendEvent, result.isSendEvent());
  }

  private static Stream<Arguments> updateTypeProvider() {
    return Stream.of(
        Arguments.of("tag", "digest", "digest", false, false, false),
        Arguments.of("new", "digest", "digest", false, true, true),
        Arguments.of("tag", "digest2", "digest", true, true, true),
        Arguments.of("tag", null, "digest", true, false, false),
        Arguments.of("tag", "digest", null, true, true, false),
        Arguments.of("tag", null, null, true, false, false),
        Arguments.of("tag", null, "digest", false, false, false),
        Arguments.of("tag", "digest", null, false, false, false),
        Arguments.of("tag", null, null, false, false, false));
  }

  @ParameterizedTest
  @MethodSource("thresholdProvider")
  public void shouldRetrieveItemUpperThresholdWithFallbackValue(
      String partition, Integer fallbackThreshold, Integer expectedUpperThreshold) {
    // given
    HelmOciMonitor subject = createSubject();
    helmOciDockerRegistryProperties.setItemUpperThreshold(fallbackThreshold);

    // Configure mocks
    List<Map<String, Object>> accounts = new ArrayList<>();
    Map<String, Object> account1 = new HashMap<>();
    account1.put("name", "partition1");
    account1.put("itemUpperThreshold", 10);
    accounts.add(account1);

    Map<String, Object> account2 = new HashMap<>();
    account2.put("name", "partition2");
    account2.put("itemUpperThreshold", 20);
    accounts.add(account2);

    Map<String, Object> account3 = new HashMap<>();
    account3.put("name", "partition3");
    accounts.add(account3);

    when(dockerRegistryAccounts.getAccounts()).thenReturn(List.of(account1, account2, account3));

    // when
    Integer result = subject.getPartitionUpperThreshold(partition);

    // then
    assertEquals(expectedUpperThreshold, result);
  }

  private static Stream<Arguments> thresholdProvider() {
    return Stream.of(
        Arguments.of("partition1", 100, 10),
        Arguments.of("partition1", null, 10),
        Arguments.of("partition2", 100, 20),
        Arguments.of("partition3", 100, 100),
        Arguments.of("partition4", 100, 100),
        Arguments.of("partition4", null, null));
  }

  private HelmOciMonitor createSubject() {
    return new HelmOciMonitor(
        properties,
        registry,
        dynamicConfig,
        discoveryStatusListener,
        lockService,
        helmOciDockerRegistryCache,
        dockerRegistryAccounts,
        Optional.of(echoService),
        Optional.of(keelService),
        helmOciDockerRegistryProperties,
        taskScheduler);
  }

  private static String keyFromTaggedImage(TaggedImage taggedImage) {
    return new DockerRegistryV2Key(
            "prefix",
            HelmOciDockerRegistryCache.ID,
            taggedImage.getAccount(),
            taggedImage.getRegistry(),
            taggedImage.getTag())
        .toString();
  }
}
