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

import com.netflix.spinnaker.igor.plugins.model.PluginRelease;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginReleaseService {

  private static final Logger log = LoggerFactory.getLogger(PluginReleaseService.class);

  private final Front50Service front50Service;

  public PluginReleaseService(Front50Service front50Service) {
    this.front50Service = front50Service;
  }

  public List<PluginRelease> getPluginReleasesSinceTimestamps(
      @Nonnull Map<String, Instant> pluginTimestamps) {
    return getPluginReleases().stream()
        .filter(
            release -> {
              Instant lastCycle =
                  Optional.ofNullable(pluginTimestamps.get(release.getPluginId()))
                      .orElse(Instant.EPOCH);
              return parseReleaseTimestamp(release)
                  .map(releaseTs -> releaseTs.isAfter(lastCycle))
                  .orElse(false);
            })
        .collect(Collectors.toList());
  }

  private Optional<Instant> parseReleaseTimestamp(PluginRelease release) {
    try {
      return Optional.of(Instant.parse(release.getReleaseDate()));
    } catch (DateTimeParseException e) {
      log.error(
          "Failed parsing plugin timestamp for '{}': '{}', cannot index plugin",
          release.getPluginId(),
          release.getReleaseDate());
      return Optional.empty();
    }
  }

  private List<PluginRelease> getPluginReleases() {
    return AuthenticatedRequest.allowAnonymous(
        () ->
            Retrofit2SyncCall.execute(front50Service.listPluginInfo()).stream()
                .flatMap(
                    info ->
                        info.releases.stream()
                            .map(
                                release ->
                                    new PluginRelease(
                                        info.id,
                                        info.description,
                                        info.provider,
                                        release.version,
                                        release.date,
                                        release.requires,
                                        PluginRequiresParser.parseRequires(release.requires),
                                        release.url,
                                        release.sha512sum,
                                        release.preferred,
                                        release.lastModified)))
                .collect(Collectors.toList()));
  }
}
