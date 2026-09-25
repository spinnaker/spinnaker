/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.artifacts.ivy.settings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Data;
import org.apache.ivy.Ivy;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.util.url.CredentialsStore;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "ivysettings")
@Data
public final class IvySettings {
  private Resolvers resolvers = new Resolvers();
  private Settings settings = new Settings();

  @Nullable private Credentials credentials;

  public static IvySettings parse(String xml) {
    try {
      return XmlMapper.builder()
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .build()
          .readValue(xml, IvySettings.class);
    } catch (JacksonException e) {
      throw new UncheckedIOException("Unable to read Ivy settings", new IOException(e));
    }
  }

  public Ivy toIvy(Path cache) {
    return Ivy.newInstance(toIvySettings(cache));
  }

  org.apache.ivy.core.settings.IvySettings toIvySettings(Path cache) {
    org.apache.ivy.core.settings.IvySettings ivySettings =
        new org.apache.ivy.core.settings.IvySettings();
    List<DependencyResolver> dependencyResolvers = resolvers.toDependencyResolvers();
    if (dependencyResolvers.isEmpty()) {
      throw new IllegalArgumentException("At least one ivy resolver is required");
    }

    dependencyResolvers.forEach(ivySettings::addResolver);
    String defaultResolver = settings.getDefaultResolver();
    ivySettings.setDefaultResolver(
        defaultResolver == null
            ? dependencyResolvers.iterator().next().getName()
            : defaultResolver);
    if (credentials != null) {
      CredentialsStore.INSTANCE.addCredentials(
          credentials.getRealm(),
          credentials.getHost(),
          credentials.getUsername(),
          credentials.getPassword());
    }
    ivySettings.setDefaultCache(cache.toFile());
    ivySettings.validate();
    return ivySettings;
  }
}
