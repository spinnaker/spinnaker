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

package com.netflix.spinnaker.kork.actuator.observability.service;

import static java.util.Optional.ofNullable;

import com.netflix.spinnaker.kork.actuator.observability.model.EnvironmentMetadata;
import com.netflix.spinnaker.kork.actuator.observability.model.MetricsConfig;
import com.netflix.spinnaker.kork.actuator.observability.model.ObservabilityConfigurationProperties;
import com.netflix.spinnaker.kork.actuator.observability.version.VersionResolver;
import io.micrometer.core.instrument.Tag;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;

/**
 * Service that will collect common metadata and allow for Micrometer to use this metadata as common
 * tags.
 */
@Slf4j
public class TagsService {

  private static final String SPRING_BOOT_BUILD_PROPERTIES_PATH = "META-INF/build-info.properties";
  public static final String SPIN_SVC = "spinSvc";
  public static final String OSS_SPIN_SVC_VER = "ossSpinSvcVer";
  public static final String HOSTNAME = "hostname";
  public static final String LIB = "lib";
  public static final String LIB_NAME = "kork-observability";

  protected final MetricsConfig metricsConfig;
  private final VersionResolver versionResolver;
  private final String springInjectedApplicationName;
  private final BuildProperties cachedBuildProps;
  private final EnvironmentMetadata cachedMetadata;
  private final List<Tag> cachedDefaultTags;

  public TagsService(
      ObservabilityConfigurationProperties metricsConfig,
      VersionResolver versionResolver,
      @Value("${spring.application.name:#{null}}") String springInjectedApplicationName) {

    this.metricsConfig = metricsConfig.getMetrics();
    this.versionResolver = versionResolver;
    this.springInjectedApplicationName = springInjectedApplicationName;

    // Cache build properties and derived tags once at construction time
    this.cachedBuildProps = getBuildProperties(getPropertiesPath());
    this.cachedMetadata = getEnvironmentMetadata(cachedBuildProps);
    var tagsAsMap = getDefaultTagsAsFilteredMap(cachedMetadata);
    // Make the list immutable to ensure thread-safety and avoid accidental mutation
    this.cachedDefaultTags = Collections.unmodifiableList(getDefaultTags(tagsAsMap));
  }

  private String trimToNull(String string) {
    if (string == null) {
      return null;
    }
    var trimmed = string.strip();
    return trimmed.isEmpty() ? null : trimmed;
  }

  protected EnvironmentMetadata getEnvironmentMetadata(BuildProperties buildProperties) {

    String resolvedApplicationName =
        ofNullable(trimToNull(springInjectedApplicationName))
            .or(() -> ofNullable(trimToNull(buildProperties.getName())))
            .orElse("UNKNOWN");

    return EnvironmentMetadata.builder()
        .applicationName(resolvedApplicationName)
        .ossAppVersion(buildProperties.getVersion())
        .build();
  }

  /**
   * @return Map of environment metadata that we will use as the default tags, with all null/empty
   *     values stripped.
   */
  protected Map<String, String> getDefaultTagsAsFilteredMap(
      EnvironmentMetadata environmentMetadata) {
    Map<String, String> tags = new HashMap<>(metricsConfig.getAdditionalTags());

    ofNullable(environmentMetadata.getOssAppVersion())
        .or(() -> ofNullable(versionResolver.resolve(environmentMetadata.getApplicationName())))
        .ifPresent(version -> tags.put("version", version));

    tags.put(LIB, LIB_NAME);
    tags.put(SPIN_SVC, environmentMetadata.getApplicationName());
    tags.put(OSS_SPIN_SVC_VER, environmentMetadata.getOssAppVersion());
    tags.put(HOSTNAME, System.getenv("HOSTNAME"));

    return tags.entrySet().stream()
        .filter(it -> (it.getValue() != null && !it.getValue().strip().equals("")))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * Loads the build properties Spring Boot metadata object. Reads directly from the classpath
   * resource rather than relying on Spring Boot auto-configuration.
   *
   * <p>If the file is not present, the properties will all be null.
   *
   * <p>Duplicates the logic from Spring Boot's ProjectInfoAutoConfiguration.
   *
   * @return build-related information such as group and artifact.
   * @see <a
   *     href="https://github.com/spring-projects/spring-boot/blob/main/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/info/ProjectInfoAutoConfiguration.java">ProjectInfoAutoConfiguration</a>
   */
  protected BuildProperties getBuildProperties(String propertiesPath) {
    var buildInfoPrefix = "build.";
    var buildInfoProperties = new Properties();
    try (var is = this.getClass().getClassLoader().getResourceAsStream(propertiesPath)) {
      var rawProperties = new Properties();
      rawProperties.load(is);
      rawProperties.stringPropertyNames().stream()
          .filter(propName -> propName.startsWith(buildInfoPrefix))
          .forEach(
              propName ->
                  buildInfoProperties.put(
                      propName.substring(buildInfoPrefix.length()), rawProperties.get(propName)));
    } catch (Exception e) {
      log.debug(
          "Could not load META-INF/build-info.properties - this is normal if the service "
              + "does not include Spring Boot build info. Message: {}",
          e.getMessage());
    }
    return new BuildProperties(buildInfoProperties);
  }

  protected List<Tag> getDefaultTags(Map<String, String> tags) {
    return tags.entrySet().stream()
        .map(
            tag -> {
              log.info(
                  "Adding default tag {}: {} to default tags list.", tag.getKey(), tag.getValue());
              return Tag.of(tag.getKey(), tag.getValue());
            })
        .collect(Collectors.toList());
  }

  protected String getPropertiesPath() {
    return SPRING_BOOT_BUILD_PROPERTIES_PATH;
  }

  public List<Tag> getDefaultTags() {
    return cachedDefaultTags;
  }
}
