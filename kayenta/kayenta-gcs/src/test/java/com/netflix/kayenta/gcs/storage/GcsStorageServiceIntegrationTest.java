/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.kayenta.gcs.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.netflix.kayenta.google.security.GoogleNamedAccountCredentials;
import com.netflix.kayenta.index.CanaryConfigIndex;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.MapBackedAccountCredentialsRepository;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs {@link GcsStorageService} against fake-gcs-server, which implements the GCS JSON API that
 * the Google API client speaks. (S3-compatible stores such as SeaweedFS or MinIO do not.)
 */
@Testcontainers
class GcsStorageServiceIntegrationTest {

  /** Pinned so the emulator version these tests run against is deterministic. */
  private static final String FAKE_GCS_IMAGE = "fsouza/fake-gcs-server:1.52.2";

  private static final String ACCOUNT_NAME = "test-account";
  private static final String BUCKET = "kayenta-test";
  private static final String ROOT_FOLDER = "kayenta";

  /**
   * Resumable uploads are redirected to the server's own external URL, so the container must be
   * reachable on the same port it advertises; a randomly mapped port would not work.
   */
  private static final int FAKE_GCS_PORT = freePort();

  @Container
  static final GenericContainer<?> fakeGcs =
      new GenericContainer<>(DockerImageName.parse(FAKE_GCS_IMAGE))
          .withExposedPorts(FAKE_GCS_PORT)
          .withCreateContainerCmdModifier(
              cmd ->
                  cmd.getHostConfig()
                      .withPortBindings(
                          new PortBinding(
                              Ports.Binding.bindPort(FAKE_GCS_PORT),
                              new ExposedPort(FAKE_GCS_PORT))))
          .withCommand(
              "-scheme",
              "http",
              "-port",
              String.valueOf(FAKE_GCS_PORT),
              "-external-url",
              "http://localhost:" + FAKE_GCS_PORT);

  private static int freePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Storage storage;

  private GcsStorageService storageService;

  @BeforeAll
  static void setUpOnce() throws Exception {
    String endpoint = "http://localhost:" + FAKE_GCS_PORT;
    storage =
        new Storage.Builder(new NetHttpTransport(), JacksonFactory.getDefaultInstance(), null)
            .setRootUrl(endpoint + "/")
            .setApplicationName("kayenta-gcs-test")
            .build();
    storage.buckets().insert("test-project", new Bucket().setName(BUCKET)).execute();
  }

  @BeforeEach
  void setUp() throws Exception {
    GoogleNamedAccountCredentials credentials =
        GoogleNamedAccountCredentials.builder()
            .name(ACCOUNT_NAME)
            .project("test-project")
            .bucket(BUCKET)
            .rootFolder(ROOT_FOLDER)
            .storage(storage)
            .build();

    AccountCredentialsRepository credentialsRepository =
        new MapBackedAccountCredentialsRepository();
    credentialsRepository.save(ACCOUNT_NAME, credentials);

    storageService = GcsStorageService.builder().accountName(ACCOUNT_NAME).build();

    // inject dependencies via reflection since @Builder + @Autowired don't mix in tests
    inject("accountCredentialsRepository", credentialsRepository);
    inject("canaryConfigIndex", mock(CanaryConfigIndex.class));
    inject("kayentaObjectMapper", new ObjectMapper());
  }

  private void inject(String field, Object value) throws ReflectiveOperationException {
    var f = GcsStorageService.class.getDeclaredField(field);
    f.setAccessible(true);
    f.set(storageService, value);
  }

  private void store(String objectKey, String metricName) {
    storageService.storeObject(
        ACCOUNT_NAME,
        ObjectType.METRIC_SET_LIST,
        objectKey,
        List.of(MetricSet.builder().name(metricName).build()),
        null,
        false);
  }

  @Test
  void storeAndLoadMetricSetList() {
    store("roundtrip", "roundtrip-metric");

    List<MetricSet> loaded =
        storageService.loadObject(ACCOUNT_NAME, ObjectType.METRIC_SET_LIST, "roundtrip");

    assertThat(loaded).extracting(MetricSet::getName).containsExactly("roundtrip-metric");
  }

  /** An object key that is a string prefix of another key must not match the longer one. */
  @Test
  void loadObjectIgnoresSiblingKeySharingPrefix() {
    store("abc", "short-key");
    store("abcd", "long-key");

    List<MetricSet> loaded =
        storageService.loadObject(ACCOUNT_NAME, ObjectType.METRIC_SET_LIST, "abc");

    assertThat(loaded).extracting(MetricSet::getName).containsExactly("short-key");
  }

  @Test
  void deleteObjectLeavesSiblingKeySharingPrefix() {
    store("del", "short-key");
    store("delta", "long-key");

    storageService.deleteObject(ACCOUNT_NAME, ObjectType.METRIC_SET_LIST, "del");

    List<MetricSet> sibling =
        storageService.loadObject(ACCOUNT_NAME, ObjectType.METRIC_SET_LIST, "delta");
    assertThat(sibling).extracting(MetricSet::getName).containsExactly("long-key");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> storageService.loadObject(ACCOUNT_NAME, ObjectType.METRIC_SET_LIST, "del"))
        .isInstanceOf(NotFoundException.class);
  }
}
