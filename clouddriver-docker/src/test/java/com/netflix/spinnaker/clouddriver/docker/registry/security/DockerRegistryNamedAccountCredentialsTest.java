/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.clouddriver.docker.registry.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerOkClientProvider;
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerRegistryTags;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import retrofit.client.Request;
import retrofit.client.Response;
import retrofit.mime.TypedString;

final class DockerRegistryNamedAccountCredentialsTest {
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final String ACCOUNT_NAME = "test-account";
  private static final String REPO_NAME = "myrepo";
  private static Instant LATEST_DATE = Instant.ofEpochSecond(1500000000L);

  @Test
  void getTags() throws IOException {
    ImmutableList<String> tags = ImmutableList.of("latest", "other", "something");
    Ok3Client ok3Client = mockDockerOkClient(tags, ImmutableMap.of());
    DockerRegistryNamedAccountCredentials credentials =
        new DockerRegistryNamedAccountCredentials.Builder()
            .accountName(ACCOUNT_NAME)
            .address("https://gcr.io")
            .dockerOkClientProvider(new MockDockerOkClientProvider(ok3Client))
            .build();
    assertThat(credentials.getTags(REPO_NAME)).containsExactlyInAnyOrderElementsOf(tags);
  }

  @Test
  void getTagsInOrder() throws IOException {
    ImmutableList<String> tags = ImmutableList.of("older", "nodate", "oldest", "latest");
    ImmutableMap<String, Instant> creationDates =
        ImmutableMap.of(
            "latest",
            LATEST_DATE,
            "older",
            LATEST_DATE.minus(Duration.ofSeconds(1)),
            "oldest",
            LATEST_DATE.minus(Duration.ofDays(1)));

    Ok3Client ok3Client = mockDockerOkClient(tags, creationDates);
    DockerRegistryNamedAccountCredentials credentials =
        new DockerRegistryNamedAccountCredentials.Builder()
            .accountName(ACCOUNT_NAME)
            .address("https://gcr.io")
            .sortTagsByDate(true)
            .dockerOkClientProvider(new MockDockerOkClientProvider(ok3Client))
            .build();
    assertThat(credentials.getTags(REPO_NAME))
        .containsExactly("latest", "older", "oldest", "nodate");
  }

  /**
   * Generates a mock Ok3Client that simulates responses from a docker registry with the supplied
   * tags and supplied creation dates for each tag. Tags that are not present in the map of creation
   * dates will return null as their creation date.
   */
  private static Ok3Client mockDockerOkClient(
      Iterable<String> tags, Map<String, Instant> creationDates) throws IOException {
    Ok3Client ok3Client = mock(Ok3Client.class);
    doReturn(
            new Response(
                "https://gcr.io/v2/myrepo/tags/list",
                200,
                "",
                Collections.emptyList(),
                new TypedString(objectMapper.writeValueAsString(getTagsResponse(tags)))))
        .when(ok3Client)
        .execute(argThat(r -> r.getUrl().equals("https://gcr.io/v2/myrepo/tags/list")));

    doAnswer(
            new Answer() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                Request request = (Request) args[0];
                String tag = getTag(request.getUrl());
                Instant optionalDate = creationDates.get(tag);
                return new Response(
                    "https://gcr.io/v2/myrepo/manifests/latest",
                    200,
                    "",
                    Collections.emptyList(),
                    new TypedString(
                        objectMapper.writeValueAsString(
                            DockerManifestResponse.withCreationDate(optionalDate))));
              }

              private String getTag(String url) {
                Matcher matcher =
                    Pattern.compile("https://gcr.io/v2/myrepo/manifests/(.*)").matcher(url);
                if (matcher.matches()) {
                  return matcher.group(1);
                }
                throw new IllegalArgumentException();
              }
            })
        .when(ok3Client)
        .execute(argThat(r -> r.getUrl().matches("https://gcr.io/v2/myrepo/manifests/.*")));

    return ok3Client;
  }

  private static DockerRegistryTags getTagsResponse(Iterable<String> tags) {
    DockerRegistryTags tagsResponse = new DockerRegistryTags();
    tagsResponse.setName(REPO_NAME);
    tagsResponse.setTags(ImmutableList.copyOf(tags));
    return tagsResponse;
  }

  /**
   * Helper class for generating the response from a call to the /manifests docker endpoint. At this
   * point, the only field we look at is the created timestamp, so we only send this part of the
   * response.
   */
  @Getter
  @RequiredArgsConstructor
  private static class DockerManifestResponse {
    private final ImmutableList<HistoryEntry> history;

    static DockerManifestResponse withCreationDate(Instant instant) throws IOException {
      return new DockerManifestResponse(ImmutableList.of(HistoryEntry.withCreationDate(instant)));
    }

    @Getter
    @RequiredArgsConstructor
    private static class HistoryEntry {
      private final String v1Compatibility;
      private static DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;

      static HistoryEntry withCreationDate(Instant instant) throws IOException {
        Map<String, Object> entries = new HashMap<>();
        entries.put("created", formatter.format(instant));
        return new HistoryEntry(objectMapper.writeValueAsString(entries));
      }
    }
  }

  @RequiredArgsConstructor
  private static class MockDockerOkClientProvider implements DockerOkClientProvider {
    private final Ok3Client mockClient;

    @Override
    public Ok3Client provide(String address, long timeoutMs, boolean insecure) {
      return mockClient;
    }
  }
}
