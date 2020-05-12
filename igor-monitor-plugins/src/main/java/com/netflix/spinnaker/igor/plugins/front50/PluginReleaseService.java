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
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginReleaseService {

  private static final Logger log = LoggerFactory.getLogger(PluginReleaseService.class);

  private final Front50Service front50Service;

  public PluginReleaseService(Front50Service front50Service) {
    this.front50Service = front50Service;
  }

  public List<PluginRelease> getPluginReleasesSince(Instant timestamp) {
    if (timestamp == null) {
      return getPluginReleases();
    }

    return getPluginReleases().stream()
        .filter(
            r -> {
              try {
                return Instant.parse(r.getReleaseDate()).isAfter(timestamp);
              } catch (DateTimeParseException e) {
                log.error(
                    "Failed parsing plugin timestamp for '{}': '{}', cannot index plugin",
                    r.getPluginId(),
                    r.getReleaseDate());
                return false;
              }
            })
        .collect(Collectors.toList());
  }

  private List<PluginRelease> getPluginReleases() {
    return AuthenticatedRequest.allowAnonymous(
        () ->
            front50Service.listPluginInfo().stream()
                .flatMap(
                    info ->
                        info.releases.stream()
                            .map(
                                release ->
                                    new PluginRelease(
                                        info.id,
                                        release.version,
                                        release.date,
                                        PluginRequiresParser.parseRequires(release.requires),
                                        release.url,
                                        release.preferred,
                                        release.lastModified)))
                .collect(Collectors.toList()));
  }
}
