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

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.annotation.Nullable;

@EqualsAndHashCode(callSuper = true)
@Data
public class IBiblioResolver extends Resolver<org.apache.ivy.plugins.resolver.IBiblioResolver> {
  /**
   * The root of the artifact repository.
   */
  @JacksonXmlProperty(isAttribute = true)
  @Nullable
  private String root;

  /**
   * A pattern describing the layout of the artifact repository. For example:
   * {@code https://repo1.maven.org/maven2/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]}
   */
  @JacksonXmlProperty(isAttribute = true)
  @Nullable
  private String pattern;

  @JacksonXmlProperty(isAttribute = true)
  @Nullable
  private Boolean m2compatible;

  /**
   * If this resolver should use Maven POMs when it is already in {@link #m2compatible} mode.
   */
  @JacksonXmlProperty(isAttribute = true)
  @Nullable
  private Boolean useMavenMetadata;

  /**
   * If this resolver should use maven-metadata.xml files to list available revisions, otherwise use directory listing.
   */
  @JacksonXmlProperty(isAttribute = true)
  @Nullable
  private Boolean usepoms;

  @Override
  public org.apache.ivy.plugins.resolver.IBiblioResolver toIvyModel() {
    org.apache.ivy.plugins.resolver.IBiblioResolver biblioResolver = new org.apache.ivy.plugins.resolver.IBiblioResolver();
    if (pattern != null) {
      biblioResolver.setPattern(pattern);
    }
    if (root != null) {
      biblioResolver.setRoot(root);
    }
    if (m2compatible != null) {
      biblioResolver.setM2compatible(m2compatible);
    }
    if (useMavenMetadata != null) {
      biblioResolver.setUseMavenMetadata(useMavenMetadata);
    }
    if (usepoms != null) {
      biblioResolver.setUsepoms(usepoms);
    }
    return super.toIvyModel(biblioResolver);
  }
}
