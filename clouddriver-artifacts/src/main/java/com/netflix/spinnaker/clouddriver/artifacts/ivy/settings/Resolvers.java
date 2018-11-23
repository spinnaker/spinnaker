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

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import lombok.Data;
import org.apache.ivy.plugins.resolver.DependencyResolver;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Data
public class Resolvers {
  @JacksonXmlElementWrapper(useWrapping = false)
  @Nullable
  private List<BintrayResolver> bintray;

  @JacksonXmlElementWrapper(useWrapping = false)
  @Nullable
  private List<UrlResolver> url;

  @JacksonXmlElementWrapper(useWrapping = false)
  @Nullable
  private List<IBiblioResolver> ibiblio;

  @JacksonXmlElementWrapper(useWrapping = false)
  @Nullable
  private List<SshResolver> ssh;

  @JacksonXmlElementWrapper(useWrapping = false)
  @Nullable
  private List<ChainResolver> chain;

  public List<DependencyResolver> toDependencyResolvers() {
    List<DependencyResolver> resolvers = new ArrayList<>();
    if (bintray != null) {
      bintray.forEach(r -> resolvers.add(r.toIvyModel()));
    }
    if (url != null) {
      url.forEach(r -> resolvers.add(r.toIvyModel()));
    }
    if (ibiblio != null) {
      ibiblio.forEach(r -> resolvers.add(r.toIvyModel()));
    }
    if (ssh != null) {
      ssh.forEach(r -> resolvers.add(r.toIvyModel()));
    }
    if (chain != null) {
      chain.forEach(r -> resolvers.add(r.toIvyModel()));
    }
    return resolvers;
  }
}
