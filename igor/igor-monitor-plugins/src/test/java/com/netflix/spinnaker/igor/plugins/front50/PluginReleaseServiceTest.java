/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.igor.plugins.front50;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.mock.Calls;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PluginReleaseServiceTest {

  static Clock clock = Clock.fixed(Instant.EPOCH.plus(1, ChronoUnit.DAYS), ZoneId.systemDefault());

  @Mock Front50Service front50Service;
  PluginReleaseService subject;

  @BeforeEach
  void setup() {
    subject = new PluginReleaseService(front50Service);
  }

  static Stream<Arguments> getReleasesSinceTimestampTestData() {
    return Stream.of(
        Arguments.of(null, List.of("1.0.0", "1.0.1", "2.0.0")),
        Arguments.of(clock.instant().minus(1, ChronoUnit.HOURS), List.of("1.0.0", "1.0.1", "2.0.0")),
        Arguments.of(clock.instant(), List.of("1.0.1", "2.0.0")),
        Arguments.of(clock.instant().plus(1, ChronoUnit.HOURS), List.of("1.0.1", "2.0.0")),
        Arguments.of(clock.instant().plus(2, ChronoUnit.DAYS), List.of())
    );
  }

  @ParameterizedTest
  @MethodSource("getReleasesSinceTimestampTestData")
  void getsReleasesSinceTimestamp(Instant timestamp, List<String> expectedVersions) throws Exception {
    PluginInfo plugin1 =
        new PluginInfo(
            "plugin1",
            "A pugin",
            "foo@example.com",
            List.of(
                release("1.0.0", clock.instant()),
                release("1.0.1", clock.instant().plus(1, ChronoUnit.DAYS))));
    PluginInfo plugin2 =
        new PluginInfo(
            "plugin2",
            "A pugin",
            "foo@example.com",
            List.of(release("2.0.0", clock.instant().plus(2, ChronoUnit.DAYS))));

    Map<String, Instant> lastPollTimestamps =
        Map.of(
            "plugin1", timestamp != null ? timestamp : Instant.EPOCH,
            "plugin2", timestamp != null ? timestamp : Instant.EPOCH);

    when(front50Service.listPluginInfo()).thenReturn(Calls.response(List.of(plugin1, plugin2)));

    List<PluginInfo.Release> result = subject.getPluginReleasesSinceTimestamps(lastPollTimestamps);

    assertEquals(
        expectedVersions, result.stream().map(PluginInfo.Release::getVersion).toList());
    verify(front50Service, times(1)).listPluginInfo();
  }

  private PluginInfo.Release release(String version, Instant releaseDate) {
    return new PluginInfo.Release(
        version,
        releaseDate.toString(),
        "orca>=0.0.0",
        "http://example.com/file.zip",
        "sha512",
        true,
        clock.instant().toString());
  }
}
