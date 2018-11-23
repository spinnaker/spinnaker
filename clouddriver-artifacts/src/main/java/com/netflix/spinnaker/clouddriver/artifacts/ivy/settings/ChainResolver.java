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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.annotation.Nullable;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ChainResolver extends Resolver<org.apache.ivy.plugins.resolver.ChainResolver> {
  @JsonIgnore
  private final Resolvers resolvers = new Resolvers();
  /**
   * If the first found should be returned.
   */
  @JacksonXmlProperty(isAttribute = true)
  @Nullable
  private Boolean returnFirst;
  /**
   * If the chain should behave like a dual chain.
   */
  @JacksonXmlProperty(isAttribute = true)
  @Nullable
  private Boolean dual;

  @JacksonXmlElementWrapper(useWrapping = false)
  public void setBintray(@Nullable List<BintrayResolver> bintray) {
    this.resolvers.setBintray(bintray);
  }

  @JacksonXmlElementWrapper(useWrapping = false)
  public void setUrl(@Nullable List<UrlResolver> url) {
    this.resolvers.setUrl(url);
  }

  @JacksonXmlElementWrapper(useWrapping = false)
  public void setIbiblio(@Nullable List<IBiblioResolver> ibiblio) {
    this.resolvers.setIbiblio(ibiblio);
  }

  @JacksonXmlElementWrapper(useWrapping = false)
  public void setSsh(@Nullable List<SshResolver> ssh) {
    this.resolvers.setSsh(ssh);
  }

  @Override
  public org.apache.ivy.plugins.resolver.ChainResolver toIvyModel() {
    org.apache.ivy.plugins.resolver.ChainResolver chainResolver = new org.apache.ivy.plugins.resolver.ChainResolver();
    if (returnFirst != null) {
      chainResolver.setReturnFirst(returnFirst);
    }
    if (dual != null) {
      chainResolver.setDual(dual);
    }
    resolvers.toDependencyResolvers().forEach(chainResolver::add);
    return super.toIvyModel(chainResolver);
  }
}
