/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.front50.model.plugins;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Splitter;
import com.netflix.spinnaker.front50.api.model.Timestamped;
import com.netflix.spinnaker.kork.api.plugins.remote.RemoteExtensionConfig;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.URL;

/**
 * A Spinnaker plugin's artifact information.
 *
 * <p>This model is used internally by Spinnaker to track what plugins are available for services to
 * install, as well as the specific releases that should be installed.
 */
@Data
@Slf4j
@Valid
public class PluginInfo implements Timestamped {
  /**
   * The canonical plugin ID.
   *
   * <p>A canonical plugin ID is one that includes the namespace, e.g. {@code netflix.hello-world}
   */
  @Nonnull private String id;

  /** The description of the plugin. */
  private String description;

  /** The plugin provider, typically the name of the author (or company). */
  private String provider;

  /** A list of plugin releases. */
  @Valid @Nonnull private List<Release> releases = new ArrayList<>();

  /** The time (epoch millis) when the plugin info was first created. */
  private Long createTs;

  /** The last time (epoch millis) this PluginInfo was modified. */
  private Long lastModified;

  /** The last principal to modify this PluginInfo. */
  private String lastModifiedBy;

  /** The code repository information for the plugin. */
  private Repository repository;

  /** The homepage URL for the plugin. */
  @URL private String homepage;

  public PluginInfo() {}

  public Optional<Release> getReleaseByVersion(String version) {
    return releases.stream().filter(it -> it.version.equals(version)).findFirst();
  }

  public void setReleaseByVersion(String version, Release release) {
    Optional<Release> versionRelease = getReleaseByVersion(version);
    if (versionRelease.isPresent()) {
      int index = releases.indexOf(versionRelease.get());
      releases.set(index, release);
    }
  }

  /** A singular {@code PluginInfo} release. */
  @Data
  public static class Release {
    private static final Splitter REQUIRES_SPLITTER = Splitter.on(",");
    public static final String VERSION_PATTERN = "^[0-9]\\d*\\.\\d+\\.\\d+(?:-[a-zA-Z0-9]+)?$";
    public static final Pattern SUPPORTS_PATTERN =
        Pattern.compile(
            "^(?<service>[\\w\\-]+)(?<operator>[><=]{1,2})(?<version>[0-9]+\\.[0-9]+\\.[0-9]+)$");
    public static final String SUPPORTS_PATTERN_SERVICE_GROUP = "service";
    public static final String SUPPORTS_PATTERN_OPERATOR_GROUP = "operator";
    public static final String SUPPORTS_PATTERN_VERSION_GROUP = "version";

    /**
     * The version of a plugin release in SemVer format.
     *
     * @link https://semver.org/
     */
    @jakarta.validation.constraints.Pattern(regexp = VERSION_PATTERN)
    private String version;

    /** The date of the plugin release. */
    private String date;

    /**
     * Spinnaker service support.
     *
     * <p>This defines, from the plugin's perspective, what services it supports. This will be used
     * to narrow what plugins are returned to a requesting service as candidates for installation.
     * Which release is actually downloaded is handled by the service itself, according to
     * heuristics that front50 does not care about.
     *
     * <p>Each element must follow a "{service}{comparator}{version}" format where:
     *
     * <p>1. "{service}" is alphanumeric and dash characters. 2. "{comparator}" is an algebraic
     * comparison operator (e.g. {@code "<", "<=", ">", ">="}) 3. "{version}" is a SemVer-compatible
     * version.
     *
     * <p>For multiple elements this is a comma-delimited string.
     */
    private String requires;

    @JsonIgnore
    public List<ServiceRequirement> getParsedRequires() {
      List<String> requirements =
          REQUIRES_SPLITTER.splitToList(this.requires != null ? this.requires : "");

      return requirements.stream()
          .map(
              r -> {
                Matcher m = SUPPORTS_PATTERN.matcher(r);
                if (!m.matches()) {
                  log.error("Failed parsing plugin requires field '{}'", r);
                  return null;
                }
                return new ServiceRequirement(
                    m.group("service"), m.group("operator"), m.group("version"));
              })
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
    }

    /** The absolute path of the plugin artifact binary. */
    private String url;

    /** The SHA512 checksum of the plugin binary. */
    private String sha512sum;

    /**
     * Whether or not this release is the preferred release, which services can use to determine if
     * this version should be installed.
     */
    private boolean preferred;

    /**
     * The last time this release was modified, typically defining the last time the {@code active}
     * flag changed.
     */
    private Instant lastModified;

    /** The principal that last modified this release. */
    private String lastModifiedBy;

    /**
     * Returns whether or not the release is supported for a particular service.
     *
     * <p>This does not perform a version check. It is the responsibility of the service itself to
     * determine which release to select.
     *
     * @param service The service name to check against.
     * @return Whether or not the plugin supports the given service
     */
    public boolean supportsService(@Nonnull String service) {
      return getParsedRequires().stream().anyMatch(it -> it.getService().equalsIgnoreCase(service));
    }

    /** Remote extensions associated with this plugin release. */
    @Nonnull private List<RemoteExtensionConfig> remoteExtensions = new ArrayList<>();
  }

  @Data
  public static class ServiceRequirement {
    private final String service;
    private final String operator;
    private final String version;
  }

  @Data
  public static class Repository {
    String type;

    @URL String url;
  }
}
