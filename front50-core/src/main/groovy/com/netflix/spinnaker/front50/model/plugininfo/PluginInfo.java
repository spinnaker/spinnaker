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
package com.netflix.spinnaker.front50.model.plugininfo;

import com.netflix.spinnaker.front50.model.Timestamped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import lombok.Data;

/**
 * A Spinnaker plugin's artifact information.
 *
 * <p>This model is used internally by Spinnaker to track what plugins are available for services to
 * install, as well as the specific releases that should be installed.
 */
@Data
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
  @Nonnull private List<Release> releases = new ArrayList<>();

  /** The time (epoch millis) when the plugin info was first created. */
  private Long createTs;

  /** The last time (epoch millis) this PluginInfo was modified. */
  private Long lastModified;

  /** The last principal to modify this PluginInfo. */
  private String lastModifiedBy;

  public PluginInfo() {}

  /** A singular {@code PluginInfo} release. */
  @Data
  public static class Release {
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
     */
    private List<String> requires;

    /** The absolute path of the plugin artifact binary. */
    private String url;

    /** The SHA512 checksum of the plugin binary. */
    private String sha512sum;

    /**
     * Defines the state of this release, which can help services to determine what version they
     * should be installing.
     */
    private State state;

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
     */
    public boolean supportsService(@Nonnull String service) {
      return requires.stream()
          .anyMatch(
              it -> {
                Matcher m = SUPPORTS_PATTERN.matcher(it);
                if (m.matches()) {
                  return m.group(SUPPORTS_PATTERN_SERVICE_GROUP).equals(service);
                }
                return false;
              });
    }

    public enum State {
      CANDIDATE,
      RELEASE
    }
  }
}
