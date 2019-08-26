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
package com.netflix.spinnaker.kork.version;

import static java.lang.String.format;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves a service version from scanning MANIFEST.MF files in the service artifacts.
 *
 * <p>This class iterates through matching JARs, rather than looking directly at itself, to support
 * the use case where OSS services are being extended via a library pattern.
 */
@Slf4j
public class ManifestVersionResolver implements VersionResolver {

  private static final String GROUP = "com.netflix.spinnaker";

  private final String group;

  public ManifestVersionResolver() {
    this(null);
  }

  /** Constructor is only available for testing. */
  private ManifestVersionResolver(String group) {
    this.group = group;
  }

  @Nullable
  @Override
  public String resolve(@Nonnull String serviceName) {
    final String serviceArtifact = getServiceArtifact(serviceName);
    return getManifestStream()
        .filter(it -> it.getPath().contains(serviceArtifact))
        .map(this::convertToManifest)
        .filter(Objects::nonNull)
        .map(this::extractImplementationVersion)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private String getServiceArtifact(String serviceName) {
    if (group == null) {
      return format("/%1$s.%2$s/%2$s", GROUP, serviceName);
    } else {
      return format("/%s/%s", group, serviceName);
    }
  }

  private Stream<URL> getManifestStream() {
    final Enumeration<URL> urls;
    try {
      urls = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
    } catch (IOException e) {
      log.error("Failed reading manifest resources", e);
      return Stream.empty();
    }
    List<URL> urlList = Collections.list(urls);
    return urlList.stream();
  }

  private Manifest convertToManifest(URL resourceUrl) {
    try {
      return new Manifest(resourceUrl.openStream());
    } catch (IOException e) {
      log.error("Failed reading resource contents: {}", resourceUrl.getPath(), e);
      return null;
    }
  }

  private String extractImplementationVersion(Manifest manifest) {
    Attributes mainAttributes = manifest.getMainAttributes();
    return mainAttributes.getValue("Implementation-Version");
  }

  @Override
  public int getOrder() {
    return 0;
  }
}
